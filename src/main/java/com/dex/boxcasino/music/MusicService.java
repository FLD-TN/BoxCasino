package com.dex.boxcasino.music;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.gui.CasinoView;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Casino music. A background song loops while a casino GUI is open and (with {@code music.area}) while the player
 * stands in the casino region. A background is either a real song in the resource pack (.ogg, played as a sound, no
 * extra plugin) or an .nbs song played through the Boombox plugin (optional). A short jingle plays on a result (win,
 * loss, draw, big win, jackpot; .nbs through Boombox). The music button of every game turns the music on / off (left
 * click) or picks the next background (right click); the choice is kept per player in music-players.yml.
 */
public final class MusicService implements Listener {

    public enum Result { WIN, LOSE, DRAW, BIG_WIN, JACKPOT }

    private static final String[] DEFAULT_FILES = {
            "background_1", "background_2", "background_3", "background_4", "victory", "defeat", "draw", "bonus", "jackpot"
    };

    /** A background: a resource pack sound (with its length) or an .nbs song id. */
    private record Track(String sound, int seconds, String nbs) {
        boolean ogg() {
            return sound != null;
        }
    }

    /** What is playing for a player right now. */
    private static final class Playing {
        final Track track;
        final Object handle;
        long restartAt;

        Playing(Track track, Object handle, long restartAt) {
            this.track = track;
            this.handle = handle;
            this.restartAt = restartAt;
        }
    }

    private record Pref(boolean on, int song) {
    }

    private final BoxCasinoPlugin plugin;
    private BoomboxEngine engine;
    private volatile boolean nbsLoaded;
    private BukkitTask task;
    private List<Track> tracks = List.of();
    private final Map<UUID, Pref> prefs = new HashMap<>();
    private final Map<UUID, Playing> background = new HashMap<>();
    private final Map<UUID, Object> jingle = new HashMap<>();
    private final Map<UUID, Long> jingleUntil = new HashMap<>();
    /** Seconds without a reason to play; the background stops after a short grace (switching menus, chat book). */
    private final Map<UUID, Integer> away = new HashMap<>();
    private File prefsFile;

    public MusicService(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection config() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("music");
        return c == null ? plugin.getConfig().createSection("music") : c;
    }

    /** Called on enable / reload. Pack songs work right away; .nbs songs load in the background when Boombox is there. */
    public void start() {
        stop();
        nbsLoaded = false;
        engine = null;
        tracks = List.of();
        if (!config().getBoolean("enabled", true)) return;
        loadPrefs();
        List<Track> list = new ArrayList<>();
        for (Object o : config().getList("backgrounds", List.of())) {
            if (o instanceof Map<?, ?> m && m.get("sound") != null) {
                Object len = m.get("length");
                list.add(new Track(String.valueOf(m.get("sound")), len instanceof Number n ? Math.max(1, n.intValue()) : 60, null));
            } else if (o instanceof String s && !s.isBlank()) {
                list.add(new Track(null, 0, s));
            }
        }
        tracks = list;
        boolean boombox = Bukkit.getPluginManager().isPluginEnabled("Boombox");
        if (boombox) loadNbs();
        else plugin.getLogger().info("Boombox not found: only resource pack (.ogg) music plays, .nbs songs are skipped");
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void loadNbs() {
        File folder = new File(plugin.getDataFolder(), "music");
        if (!folder.isDirectory() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create the music folder");
            return;
        }
        for (String id : DEFAULT_FILES) {
            File f = new File(folder, id + ".nbs");
            if (f.exists()) continue;
            try (InputStream in = plugin.getResource("music/" + id + ".nbs")) {
                if (in != null) Files.copy(in, f.toPath());
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save music/" + id + ".nbs: " + ex.getMessage());
            }
        }
        engine = new BoomboxEngine();
        BoomboxEngine e = engine;
        List<String> ids = new ArrayList<>();
        for (Track t : tracks) if (!t.ogg()) ids.add(t.nbs());
        ConfigurationSection results = config().getConfigurationSection("results");
        if (results != null) for (String k : results.getKeys(false)) ids.add(results.getString(k, ""));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            e.load(folder, ids.stream().filter(s -> !s.isEmpty()).distinct().toList(), plugin.getLogger());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (engine != e) return;
                nbsLoaded = true;
                plugin.getLogger().info("Game music loaded (Boombox)");
            });
        });
    }

    /** Backgrounds that can play now (.nbs ones only with Boombox loaded). */
    private List<Track> playable() {
        List<Track> out = new ArrayList<>();
        for (Track t : tracks) if (t.ogg() || (nbsLoaded && engine != null && engine.has(t.nbs()))) out.add(t);
        return out;
    }

    /** True when the music buttons should be shown. */
    public boolean available() {
        return !playable().isEmpty();
    }

    private Pref pref(UUID player) {
        return prefs.computeIfAbsent(player, k -> new Pref(config().getBoolean("default-on", true), 0));
    }

    public boolean isOn(UUID player) {
        return pref(player).on();
    }

    /** Number (1..n) of the background song the player picked. */
    public int songNumber(UUID player) {
        int n = Math.max(1, playable().size());
        return Math.floorMod(pref(player).song(), n) + 1;
    }

    public int songCount() {
        return Math.max(1, playable().size());
    }

    /** Music button: left = on / off, right = next background song (turns the music on). */
    public void click(Player player, ClickType click) {
        if (!available()) return;
        UUID id = player.getUniqueId();
        Pref p = pref(id);
        if (click.isRightClick()) {
            prefs.put(id, new Pref(true, songNumber(id)));
            stopAll(player);
        } else {
            prefs.put(id, new Pref(!p.on(), p.song()));
            if (p.on()) stopAll(player);
        }
        savePrefs();
        tickPlayer(player);
    }

    /** /casino nhac: the casino music on / off for this player. */
    public void toggle(Player player) {
        if (!available()) {
            plugin.messages().send(player, "music.unavailable");
            return;
        }
        UUID id = player.getUniqueId();
        Pref p = pref(id);
        prefs.put(id, new Pref(!p.on(), p.song()));
        if (p.on()) stopAll(player);
        savePrefs();
        tickPlayer(player);
        plugin.messages().send(player, isOn(id) ? "music.toggled-on" : "music.toggled-off");
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) tickPlayer(p);
    }

    /** Music plays in the casino region (music.area) and, with music.in-gui, at any casino GUI. */
    private boolean wantsMusic(Player player) {
        if (config().getBoolean("in-gui", false)
                && player.getOpenInventory().getTopInventory().getHolder() instanceof CasinoView) {
            return true;
        }
        return config().getBoolean("area", true) && plugin.access().inCasino(player);
    }

    private void tickPlayer(Player player) {
        UUID id = player.getUniqueId();
        if (!wantsMusic(player)) {
            if (!background.containsKey(id)) return;
            int seconds = away.merge(id, 1, Integer::sum);
            if (seconds >= config().getInt("grace-seconds", 3)) stopAll(player);
            return;
        }
        away.remove(id);
        if (!isOn(id)) return;
        long now = System.currentTimeMillis();
        Playing playing = background.get(id);
        if (playing != null) {
            // a pack sound cannot loop by itself: start it again when it ends
            if (playing.track.ogg() && now >= playing.restartAt) {
                playSound(player, playing.track);
                playing.restartAt = now + playing.track.seconds() * 1000L;
            }
            return;
        }
        Long until = jingleUntil.get(id);
        if (until != null && until > now) return;
        List<Track> list = playable();
        if (list.isEmpty()) return;
        Track t = list.get(songNumber(id) - 1);
        if (t.ogg()) {
            playSound(player, t);
            background.put(id, new Playing(t, null, now + t.seconds() * 1000L));
        } else {
            Object h = engine.play(player, t.nbs(), true, volume());
            if (h != null) background.put(id, new Playing(t, h, Long.MAX_VALUE));
        }
    }

    private void playSound(Player player, Track t) {
        player.playSound(player, t.sound(), SoundCategory.RECORDS, volume() / 100f, 1f);
    }

    private int volume() {
        return Math.max(1, Math.min(100, config().getInt("volume", 50)));
    }

    /** A bet was settled: the matching jingle for its player (if they are at a casino GUI with music on). */
    public void onSettled(Bet bet, long payout) {
        Result r;
        if (payout > bet.amount()) {
            double multiplier = (double) payout / bet.amount();
            r = multiplier >= config().getDouble("big-win-multiplier", 5) ? Result.BIG_WIN : Result.WIN;
        } else {
            r = payout == bet.amount() ? Result.DRAW : Result.LOSE;
        }
        Player p = Bukkit.getPlayer(bet.player);
        if (p != null) result(p, r);
    }

    /**
     * Plays the jingle of {@code result} (.nbs through Boombox). An .nbs background pauses and comes back after it; a
     * pack song keeps playing underneath (it could only restart from the beginning).
     */
    public void result(Player player, Result result) {
        if (engine == null || !nbsLoaded || !config().getBoolean("results-enabled", true)) return;
        UUID id = player.getUniqueId();
        String key = switch (result) {
            case WIN -> "win";
            case LOSE -> "lose";
            case DRAW -> "draw";
            case BIG_WIN -> "big-win";
            case JACKPOT -> "jackpot";
        };
        String song = config().getString("results." + key, "");
        if (song.isEmpty() || !engine.has(song)) return;
        engine.stop(jingle.remove(id));
        Playing bg = background.get(id);
        if (bg != null && !bg.track.ogg()) {
            engine.stop(bg.handle);
            background.remove(id);
        }
        Object h = engine.play(player, song, false, volume());
        if (h == null) return;
        jingle.put(id, h);
        int seconds = engine.seconds(h);
        jingleUntil.put(id, System.currentTimeMillis() + Math.max(2, seconds == 0 ? 4 : seconds + 1) * 1000L);
    }

    private void stopAll(Player player) {
        UUID id = player.getUniqueId();
        Playing bg = background.remove(id);
        if (bg != null) {
            if (bg.track.ogg()) player.stopSound(bg.track.sound(), SoundCategory.RECORDS);
            else if (engine != null) engine.stop(bg.handle);
        }
        if (engine != null) engine.stop(jingle.remove(id));
        else jingle.remove(id);
        jingleUntil.remove(id);
        away.remove(id);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stopAll(e.getPlayer());
    }

    /** Disable / reload: every song stops. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (background.containsKey(p.getUniqueId()) || jingle.containsKey(p.getUniqueId())) stopAll(p);
        }
        background.clear();
        jingle.clear();
        jingleUntil.clear();
        away.clear();
    }

    // --- per-player choice ---

    private void loadPrefs() {
        prefsFile = new File(plugin.getDataFolder(), "music-players.yml");
        prefs.clear();
        if (!prefsFile.isFile()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(prefsFile);
        for (String key : yml.getKeys(false)) {
            try {
                prefs.put(UUID.fromString(key), new Pref(yml.getBoolean(key + ".on", true), yml.getInt(key + ".song", 0)));
            } catch (IllegalArgumentException ignored) {
                // not a player id
            }
        }
    }

    private void savePrefs() {
        if (prefsFile == null) return;
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Pref> e : prefs.entrySet()) {
            yml.set(e.getKey() + ".on", e.getValue().on());
            yml.set(e.getKey() + ".song", Math.floorMod(e.getValue().song(), songCount()));
        }
        try {
            yml.save(prefsFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save music-players.yml: " + ex.getMessage());
        }
    }
}
