package com.dex.boxcasino.extra;

import com.dex.boxcasino.BoxCasinoPlugin;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Dealer NPCs at the xì dách and roulette machines (config machines.dealers), through the ZNPCsPlus plugin (optional:
 * without it machines simply have no dealer). The dealer swings a hand while dealing / spinning.
 */
public final class DealerService {

    private final BoxCasinoPlugin plugin;
    private ZnpcDealers impl;

    public DealerService(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection config() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("machines.dealers");
        return c == null ? plugin.getConfig().createSection("machines.dealers") : c;
    }

    private ZnpcDealers impl() {
        if (impl == null && config().getBoolean("enabled", true) && Bukkit.getPluginManager().isPluginEnabled("ZNPCsPlus")) {
            impl = new ZnpcDealers(plugin);
        }
        return impl;
    }

    /** Where the dealer of {@code game} stands: "back|left|right &lt;distance&gt;", or null when the game has none. */
    String spot(String game) {
        String s = config().getString("positions." + game, "");
        return s.isBlank() ? null : s;
    }

    void spawn(String machine, Location at) {
        ZnpcDealers z = impl();
        if (z == null) return;
        try {
            z.spawn(machine, at, config().getString("name", "&#E056FDNhà Cái"), skinSource());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Could not create the dealer NPC: " + ex.getMessage());
        }
    }

    private String[] skinSource() {
        return new String[] {config().getString("texture", "").trim(), config().getString("signature", "").trim(),
                config().getString("skin", "casino_dealer.png")};
    }

    /** /casino admin dealer skin: the skin from the config on every dealer. Returns the dealer count, -1 = no ZNPCsPlus. */
    public int reskin() {
        ZnpcDealers z = impl();
        if (z == null) return -1;
        return z.reskin(skinSource());
    }

    /** Reload: the next dealer reads the skin from the config again. */
    public void reload() {
        if (impl != null) impl.forgetSkin();
    }

    void remove(String machine) {
        ZnpcDealers z = impl();
        if (z == null) return;
        try {
            z.remove(machine);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Could not remove the dealer NPC: " + ex.getMessage());
        }
    }

    /** The dealer of a machine swings a hand. */
    public void act(String machine) {
        ZnpcDealers z = impl();
        if (z == null || machine == null) return;
        try {
            z.act(machine);
        } catch (RuntimeException ignored) {
            // no dealer at that machine
        }
    }

    /** The dealer of the machine where {@code player} sits (if any) swings a hand. */
    public void actFor(UUID player) {
        SeatService.Seated s = plugin.seats().of(player);
        if (s != null) act(s.machine());
    }
}
