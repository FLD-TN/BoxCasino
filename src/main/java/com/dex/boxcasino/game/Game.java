package com.dex.boxcasino.game;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ConfigFiles;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** One casino game: its config ({@code games/<id>.yml}), its GUI and its math. */
public abstract class Game {

    protected final BoxCasinoPlugin plugin;
    private final String id;
    protected GameSettings settings;
    private boolean enabled;

    protected Game(BoxCasinoPlugin plugin, String id) {
        this.plugin = plugin;
        this.id = id;
    }

    public String id() {
        return id;
    }

    public GameSettings settings() {
        return settings;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String displayName() {
        return plugin.messages().raw("games." + id + ".name");
    }

    /** {@code boxcasino.game.<id>} (default true). Tells the player when missing. */
    public boolean permitted(Player player) {
        if (player.hasPermission("boxcasino.game." + id)) return true;
        plugin.messages().send(player, "command.no-permission");
        return false;
    }

    /** Played against the house (feeds and can win the jackpot); false for 1v1 games. */
    public boolean house() {
        return true;
    }

    /** Needs the casino region (false for PvP games playable anywhere). */
    public boolean casinoOnly() {
        return true;
    }

    /** Loads {@code games/<id>.yml}; returns an error text when the game must stay disabled. */
    public final String load() {
        ConfigFiles.ensure(plugin, "games/" + id + ".yml");
        File file = new File(plugin.getDataFolder(), "games/" + id + ".yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("games/" + id + ".yml")) {
            if (in != null) yml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (java.io.IOException ignored) {
            // defaults are optional
        }
        settings = new GameSettings(yml);
        String error = loadGame(yml);
        if (error == null && rtp() > 1.0) error = "RTP " + rtp() + " > 100%";
        enabled = settings.enabled && error == null;
        return error;
    }

    /** Game-specific config. Returns an error text or null. */
    protected abstract String loadGame(YamlConfiguration yml);

    /** Theoretical return to player (0..1); NaN when it depends on play (shown as "theo luật"). */
    public abstract double rtp();

    /** Shared tables settle or refund their bets (plugin disable, reload). Single-player games use their GUI close. */
    public void shutdown() {
    }

    /** Opens the game GUI. Access checks are already done. */
    public abstract void open(Player player, boolean npc);

    /** Plays {@code rounds} rounds with a fixed strategy; returns paid / wagered. Runs off the main thread. */
    public abstract double simulate(long rounds, RandomGenerator random);
}
