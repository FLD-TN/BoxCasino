package com.dex.boxcasino.storage;

import java.io.File;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite at {@code plugins/BoxCasino/data.db} (WAL). One connection; every access is {@code synchronized} because it
 * is used from the main thread (ledger, synchronous) and from one async writer (stats, limits, logs).
 */
public final class Database {

    private static final String[] SCHEMA = {
            "CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT)",
            "CREATE TABLE IF NOT EXISTS rounds (id INTEGER PRIMARY KEY AUTOINCREMENT, game TEXT NOT NULL,"
                    + " created_at INTEGER NOT NULL, outcome TEXT)",
            "CREATE TABLE IF NOT EXISTS bets (id INTEGER PRIMARY KEY AUTOINCREMENT, round_id INTEGER NOT NULL,"
                    + " uuid TEXT NOT NULL, currency TEXT NOT NULL, amount INTEGER NOT NULL, payout INTEGER NOT NULL DEFAULT 0,"
                    + " state TEXT NOT NULL, created_at INTEGER NOT NULL, settled_at INTEGER)",
            "CREATE INDEX IF NOT EXISTS bets_state ON bets(state)",
            "CREATE INDEX IF NOT EXISTS bets_uuid ON bets(uuid, id)",
            // purge and "round without bets" checks: without these every old round scans the whole bets table
            "CREATE INDEX IF NOT EXISTS bets_round ON bets(round_id)",
            "CREATE INDEX IF NOT EXISTS bets_created ON bets(created_at)",
            "CREATE INDEX IF NOT EXISTS rounds_created ON rounds(created_at)",
            "CREATE TABLE IF NOT EXISTS pending_payouts (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL,"
                    + " currency TEXT NOT NULL, amount INTEGER NOT NULL, reason TEXT NOT NULL, created_at INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS pending_uuid ON pending_payouts(uuid)",
            "CREATE TABLE IF NOT EXISTS player_ips (uuid TEXT NOT NULL, ip_hash TEXT NOT NULL, last_seen INTEGER NOT NULL,"
                    + " PRIMARY KEY(uuid, ip_hash))",
            "CREATE INDEX IF NOT EXISTS player_ips_hash ON player_ips(ip_hash)",
            "CREATE TABLE IF NOT EXISTS ip_main (ip_hash TEXT PRIMARY KEY, main_uuid TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS alt_allowed (uuid TEXT PRIMARY KEY)",
            "CREATE TABLE IF NOT EXISTS limits (uuid TEXT PRIMARY KEY, play_seconds INTEGER NOT NULL DEFAULT 0,"
                    + " rest_until INTEGER NOT NULL DEFAULT 0, excluded_until INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS daily_loss (uuid TEXT NOT NULL, day TEXT NOT NULL, currency TEXT NOT NULL,"
                    + " net INTEGER NOT NULL, PRIMARY KEY(uuid, day, currency))",
            "CREATE TABLE IF NOT EXISTS jackpot (currency TEXT PRIMARY KEY, pot INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS stats (uuid TEXT NOT NULL, game TEXT NOT NULL, currency TEXT NOT NULL,"
                    + " rounds INTEGER NOT NULL DEFAULT 0, wins INTEGER NOT NULL DEFAULT 0, losses INTEGER NOT NULL DEFAULT 0,"
                    + " draws INTEGER NOT NULL DEFAULT 0, wagered INTEGER NOT NULL DEFAULT 0, paid INTEGER NOT NULL DEFAULT 0,"
                    + " won INTEGER NOT NULL DEFAULT 0, lost INTEGER NOT NULL DEFAULT 0,"
                    + " biggest_win INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(uuid, game, currency))",
            "CREATE TABLE IF NOT EXISTS house_daily (day TEXT NOT NULL, game TEXT NOT NULL, currency TEXT NOT NULL,"
                    + " wagered INTEGER NOT NULL DEFAULT 0, paid INTEGER NOT NULL DEFAULT 0, tax INTEGER NOT NULL DEFAULT 0,"
                    + " PRIMARY KEY(day, game, currency))"
    };

    private final Logger log;
    private Connection conn;
    private ExecutorService writer;
    /** Prepared once per SQL text: the ledger runs the same few statements on the main thread every round. */
    private final java.util.Map<String, PreparedStatement> statements = new java.util.HashMap<>();

    public Database(Logger log) {
        this.log = log;
    }

    public synchronized void open(File file) throws SQLException {
        File dir = file.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) throw new SQLException("Cannot create " + dir);
        conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA busy_timeout=5000");
            for (String sql : SCHEMA) st.execute(sql);
        }
        writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BoxCasino-DB");
            t.setDaemon(true);
            return t;
        });
    }

    /** Not synchronized while waiting: the queued writes need the connection lock to finish. */
    public void close() {
        if (writer != null) {
            writer.shutdown();
            try {
                if (!writer.awaitTermination(10, TimeUnit.SECONDS)) log.warning("Async DB writes did not finish in 10s");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (this) {
            for (PreparedStatement ps : statements.values()) {
                try {
                    ps.close();
                } catch (SQLException ignored) {
                    // closing anyway
                }
            }
            statements.clear();
            try {
                if (conn != null) conn.close();
            } catch (SQLException ex) {
                log.log(Level.WARNING, "Closing data.db failed", ex);
            }
        }
    }

    /** Runs on the single DB thread; errors are logged. */
    public void async(SqlTask task) {
        writer.execute(() -> {
            try {
                task.run();
            } catch (SQLException | RuntimeException ex) {
                log.log(Level.SEVERE, "Async DB write failed", ex);
            }
        });
    }

    public synchronized int update(String sql, Object... args) throws SQLException {
        return prepare(sql, false, args).executeUpdate();
    }

    /** INSERT returning the generated id. */
    public synchronized long insert(String sql, Object... args) throws SQLException {
        PreparedStatement ps = prepare(sql, true, args);
        ps.executeUpdate();
        try (ResultSet rs = ps.getGeneratedKeys()) {
            if (rs.next()) return rs.getLong(1);
        }
        throw new SQLException("No generated key for " + sql);
    }

    public synchronized <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) throws SQLException {
        List<T> out = new ArrayList<>();
        try (ResultSet rs = prepare(sql, false, args).executeQuery()) {
            while (rs.next()) out.add(mapper.map(rs));
        }
        return out;
    }

    /** Runs {@code task} as one transaction (one commit instead of one per statement); rolled back on error. */
    public synchronized void transaction(SqlTask task) throws SQLException {
        conn.setAutoCommit(false);
        try {
            task.run();
            conn.commit();
        } catch (SQLException | RuntimeException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    public <T> T first(String sql, RowMapper<T> mapper, Object... args) throws SQLException {
        List<T> rows = query(sql, mapper, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Random salt for IP hashing, created once. */
    public synchronized String salt() throws SQLException {
        String salt = first("SELECT value FROM meta WHERE key = 'ip_salt'", rs -> rs.getString(1));
        if (salt != null) return salt;
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        salt = HexFormat.of().formatHex(bytes);
        update("INSERT INTO meta (key, value) VALUES ('ip_salt', ?)", salt);
        return salt;
    }

    private PreparedStatement prepare(String sql, boolean keys, Object... args) throws SQLException {
        PreparedStatement ps = statements.get(sql);
        if (ps == null) {
            ps = keys ? conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS) : conn.prepareStatement(sql);
            statements.put(sql, ps);
        }
        ps.clearParameters();
        for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
        return ps;
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlTask {
        void run() throws SQLException;
    }
}
