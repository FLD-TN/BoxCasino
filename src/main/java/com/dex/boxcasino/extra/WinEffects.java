package com.dex.boxcasino.extra;

import com.dex.boxcasino.BoxCasinoPlugin;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * A win in any game: a firework shoots up from the winner and a "counting money" sound plays (config effects.win).
 * The fireworks are tagged and never hurt anyone.
 */
public final class WinEffects implements Listener {

    private static final Color[] COLORS = {
            Color.fromRGB(0xFFD166), Color.fromRGB(0x7CFC9A), Color.fromRGB(0xE056FD), Color.fromRGB(0x70A1FF),
            Color.fromRGB(0xFF8A65), Color.fromRGB(0xF78FB3)
    };

    private final BoxCasinoPlugin plugin;
    private final NamespacedKey key;

    public WinEffects(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "win_firework");
    }

    private ConfigurationSection config() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("effects.win");
        return c == null ? plugin.getConfig().createSection("effects.win") : c;
    }

    /** A bet paid more than it cost. */
    public void win(Player player) {
        ConfigurationSection c = config();
        if (!c.getBoolean("enabled", true) || !player.isOnline()) return;
        if (c.getBoolean("firework", true)) firework(player.getLocation(), c.getInt("firework-power", 1));
        sounds(player, c.getStringList("sounds"));
    }

    private void firework(Location at, int power) {
        Color a = COLORS[plugin.random().nextInt(COLORS.length)];
        Color b = COLORS[plugin.random().nextInt(COLORS.length)];
        at.getWorld().spawn(at.clone().add(0, 0.5, 0), Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(plugin.random().nextBoolean() ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.STAR)
                    .withColor(a, b).withFade(Color.WHITE).trail(true).flicker(true).build());
            meta.setPower(Math.max(0, Math.min(3, power)));
            fw.setFireworkMeta(meta);
            fw.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** Each entry: 'sound key:volume:pitch:delay ticks' (vanilla key or a resource pack sound). */
    private void sounds(Player player, List<String> entries) {
        if (!plugin.gameSounds()) return;
        for (String entry : entries) {
            String[] p = entry.split(":");
            // a namespaced key ("boxcasino:music.cash") has one ':' more
            int base = p.length >= 5 ? 1 : 0;
            String sound = base == 1 ? p[0] + ":" + p[1] : p[0];
            float volume = parse(p, base + 1, 0.8f);
            float pitch = parse(p, base + 2, 1f);
            long delay = (long) parse(p, base + 3, 0);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch);
            }, Math.max(0, delay));
        }
    }

    private static float parse(String[] p, int i, float fallback) {
        try {
            return i < p.length ? Float.parseFloat(p[i]) : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Firework fw && fw.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            e.setCancelled(true);
        }
    }
}
