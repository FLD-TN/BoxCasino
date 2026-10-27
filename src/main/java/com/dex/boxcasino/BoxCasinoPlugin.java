package com.dex.boxcasino;

import com.dex.boxcasino.access.AccessService;
import com.dex.boxcasino.access.ProfileService;
import com.dex.boxcasino.chat.RoomService;
import com.dex.boxcasino.command.CasinoCommand;
import com.dex.boxcasino.command.PvpCommand;
import com.dex.boxcasino.pvp.PvpService;
import com.dex.boxcasino.pvp.dice.DiceGame;
import com.dex.boxcasino.pvp.rps.RpsGame;
import com.dex.boxcasino.pvp.tictactoe.TicTacToeGame;
import com.dex.boxcasino.config.ConfigFiles;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.config.Settings;
import com.dex.boxcasino.economy.Wallet;
import com.dex.boxcasino.extra.BigWinService;
import com.dex.boxcasino.extra.JackpotService;
import com.dex.boxcasino.extra.LeaderboardService;
import com.dex.boxcasino.extra.MachineService;
import com.dex.boxcasino.game.BetMemory;
import com.dex.boxcasino.game.BetService;
import com.dex.boxcasino.game.GameRegistry;
import com.dex.boxcasino.game.mines.MinesGame;
import com.dex.boxcasino.game.plinko.PlinkoGame;
import com.dex.boxcasino.game.races.RacesGame;
import com.dex.boxcasino.game.roulette.RouletteGame;
import com.dex.boxcasino.game.stairs.StairsGame;
import com.dex.boxcasino.game.xidach.XiDachGame;
import com.dex.boxcasino.gui.GuiListener;
import com.dex.boxcasino.gui.Items;
import com.dex.boxcasino.hook.CasinoExpansion;
import com.dex.boxcasino.hook.ItemsAdderHook;
import com.dex.boxcasino.hook.PapiHook;
import com.dex.boxcasino.listener.PlayerListener;
import com.dex.boxcasino.storage.Database;
import com.dex.boxcasino.storage.Ledger;
import com.dex.boxcasino.storage.ProfileStore;
import com.dex.boxcasino.storage.WriteBuffer;
import com.dex.boxcore.api.GemService;
import java.io.File;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class BoxCasinoPlugin extends JavaPlugin {

    private final SecureRandom random = new SecureRandom();
    private final Items items = new Items();
    private final BetMemory betMemory = new BetMemory();
    private final GameRegistry games = new GameRegistry();
    private Settings settings;
    private Messages messages;
    private Wallet wallet;
    private Database database;
    private Ledger ledger;
    private ProfileStore profileStore;
    private WriteBuffer writes;
    private ProfileService profiles;
    private AccessService access;
    private BetService bets;
    private PvpService pvp;
    private JackpotService jackpot;
    private BigWinService bigWins;
    private LeaderboardService leaderboard;
    private MachineService machines;
    private com.dex.boxcasino.music.MusicService music;
    private com.dex.boxcasino.extra.DealerService dealers;
    private com.dex.boxcasino.extra.WinEffects winEffects;
    private com.dex.boxcasino.extra.SeatService seats;
    private RoomService rooms;
    private com.dex.boxcasino.pvp.ChallengePrompt challengePrompt;
    private com.dex.boxcasino.extra.GoldenHourService goldenHour;

    @Override
    public void onEnable() {
        ConfigFiles.ensure(this, "config.yml");
        reloadConfig();
        messages = new Messages(this);
        messages.load();
        settings = new Settings(getConfig(), getLogger());

        GemService gems = Bukkit.getServicesManager().load(GemService.class);
        if (gems == null) {
            getLogger().severe("BoxCore GemService not found: BoxCasino disabled");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        wallet = new Wallet(gems, messages);
        wallet.resolveVault();

        database = new Database(getLogger());
        try {
            database.open(new File(getDataFolder(), "data.db"));
            profileStore = new ProfileStore(database);
            profileStore.init();
        } catch (SQLException ex) {
            getLogger().log(Level.SEVERE, "Cannot open data.db: BoxCasino disabled", ex);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        ledger = new Ledger(database);
        writes = new WriteBuffer(database, profileStore);
        profiles = new ProfileService(this, profileStore);
        access = new AccessService(this);
        bets = new BetService(this, ledger);
        pvp = new PvpService(this);
        jackpot = new JackpotService(this, database);
        jackpot.load();
        goldenHour = new com.dex.boxcasino.extra.GoldenHourService(this);
        bigWins = new BigWinService(this);
        bigWins.load();
        leaderboard = new LeaderboardService(this);
        machines = new MachineService(this);
        seats = new com.dex.boxcasino.extra.SeatService(this);
        dealers = new com.dex.boxcasino.extra.DealerService(this);
        winEffects = new com.dex.boxcasino.extra.WinEffects(this);
        rooms = new RoomService(this);
        challengePrompt = new com.dex.boxcasino.pvp.ChallengePrompt(this);
        music = new com.dex.boxcasino.music.MusicService(this);
        // first tick: every plugin is enabled, so the Vault economy exists before refunds are paid
        Bukkit.getScheduler().runTask(this, () -> {
            if (!wallet.resolveVault()) {
                getLogger().severe("No Vault economy found: nobody can bet until one is installed");
                return;
            }
            bets.recover();
        });

        ItemsAdderHook ia = ItemsAdderHook.create(this);
        items.setItemsAdder(ia);
        if (ia == null) getLogger().warning("ItemsAdder not found: GUIs use the plain fallback look");

        games.register(new MinesGame(this));
        games.register(new PlinkoGame(this));
        games.register(new StairsGame(this));
        games.register(new RouletteGame(this));
        games.register(new RacesGame(this));
        games.register(new DiceGame(this));
        games.register(new RpsGame(this));
        games.register(new TicTacToeGame(this));
        games.register(new XiDachGame(this));
        games.register(new com.dex.boxcasino.game.lottery.LotteryGame(this));
        games.register(new com.dex.boxcasino.game.slots.SlotsGame(this));
        games.register(new com.dex.boxcasino.game.chest.ChestGame(this));
        games.register(new com.dex.boxcasino.game.crash.CrashGame(this));
        games.register(new com.dex.boxcasino.game.pot.PotGame(this));
        games.loadAll(getLogger());

        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(machines, this);
        Bukkit.getPluginManager().registerEvents(seats, this);
        Bukkit.getPluginManager().registerEvents(winEffects, this);
        Bukkit.getPluginManager().registerEvents(challengePrompt, this);
        Bukkit.getScheduler().runTask(this, () -> machines.refreshHolograms());
        Bukkit.getPluginManager().registerEvents(music, this);
        music.start();
        PluginCommand cmd = getCommand("casino");
        if (cmd != null) {
            CasinoCommand handler = new CasinoCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
        for (String[] pair : new String[][] {{"dice", "dice"}, {"oantuti", "rps"}, {"caro", "tictactoe"}}) {
            PluginCommand pc = getCommand(pair[0]);
            if (pc == null) continue;
            PvpCommand handler = new PvpCommand(this, pair[1]);
            pc.setExecutor(handler);
            pc.setTabCompleter(handler);
        }
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new CasinoExpansion(this).register();
            Messages.setImageResolver(PapiHook.imageResolver());
        } else {
            getLogger().warning("PlaceholderAPI not found: %img_money% / %img_gem% stay as plain text");
        }

        Bukkit.getScheduler().runTaskTimer(this, access::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, profiles::saveAllDirty, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, writes::flush, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, this::purge, 200L, 20L * 60 * 60 * 6);
        Bukkit.getScheduler().runTaskTimer(this, leaderboard::refresh, 100L, 20L * 60 * 5);

        for (Player p : Bukkit.getOnlinePlayers()) profiles.load(p);
    }

    @Override
    public void onDisable() {
        if (database == null) return;
        if (goldenHour != null) goldenHour.shutdown();
        GuiListener.closeAll();
        if (seats != null) seats.shutdown();
        if (music != null) music.stop();
        games.shutdownAll();
        if (profiles != null) {
            for (Player p : Bukkit.getOnlinePlayers()) profiles.unload(p.getUniqueId());
        }
        if (writes != null) writes.flush();
        database.close();
    }

    /** /casino admin reload: every open game settles or refunds first (spec 6). */
    public void reloadAll() {
        GuiListener.closeAll();
        games.shutdownAll();
        ConfigFiles.ensure(this, "config.yml");
        reloadConfig();
        settings = new Settings(getConfig(), getLogger());
        messages.load();
        games.loadAll(getLogger());
        jackpot.load();
        bigWins.load();
        leaderboard.refresh();
        music.start();
        dealers.reload();
        machines.refreshHolograms();
    }

    private void purge() {
        long cutoff = System.currentTimeMillis() - settings.logRetentionDays * 86_400_000L;
        String oldDay = LocalDate.parse(profiles.today()).minusDays(7).toString();
        database.async(() -> {
            ledger.purge(cutoff);
            profileStore.purgeDailyBefore(oldDay);
        });
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public Wallet wallet() {
        return wallet;
    }

    public Items items() {
        return items;
    }

    public Database database() {
        return database;
    }

    public Ledger ledger() {
        return ledger;
    }

    public ProfileStore profileStore() {
        return profileStore;
    }

    public ProfileService profiles() {
        return profiles;
    }

    public AccessService access() {
        return access;
    }

    public BetService bets() {
        return bets;
    }

    public PvpService pvp() {
        return pvp;
    }

    public JackpotService jackpot() {
        return jackpot;
    }

    public BigWinService bigWins() {
        return bigWins;
    }

    public com.dex.boxcasino.extra.GoldenHourService goldenHour() {
        return goldenHour;
    }

    public LeaderboardService leaderboard() {
        return leaderboard;
    }

    public MachineService machines() {
        return machines;
    }

    /** config.yml game-sounds: click / win / lose sounds of the GUIs (players can also lower their own volume). */
    public boolean gameSounds() {
        return getConfig().getBoolean("game-sounds", true);
    }

    public com.dex.boxcasino.music.MusicService music() {
        return music;
    }

    public com.dex.boxcasino.extra.DealerService dealers() {
        return dealers;
    }

    public com.dex.boxcasino.extra.WinEffects winEffects() {
        return winEffects;
    }

    public com.dex.boxcasino.extra.SeatService seats() {
        return seats;
    }

    public com.dex.boxcasino.pvp.ChallengePrompt challengePrompt() {
        return challengePrompt;
    }

    public RoomService rooms() {
        return rooms;
    }

    public WriteBuffer writes() {
        return writes;
    }

    public BetMemory betMemory() {
        return betMemory;
    }

    public GameRegistry games() {
        return games;
    }

    public SecureRandom random() {
        return random;
    }
}
