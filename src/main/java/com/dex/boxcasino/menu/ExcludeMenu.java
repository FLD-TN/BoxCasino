package com.dex.boxcasino.menu;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.gui.CasinoView;
import com.dex.boxcasino.storage.Profile;
import com.dex.boxcasino.util.Format;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Self-exclusion (spec 4.5): pick a duration, then confirm. Cannot be undone by the player. */
public final class ExcludeMenu extends CasinoView {

    /** Accepted durations: key → seconds. */
    public static final Map<String, Long> DURATIONS = new LinkedHashMap<>();

    static {
        DURATIONS.put("1h", 3600L);
        DURATIONS.put("1d", 86400L);
        DURATIONS.put("7d", 7 * 86400L);
        DURATIONS.put("30d", 30 * 86400L);
    }

    private static final int[] CHOICE_SLOTS = {10, 12, 14, 16};
    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 15;

    private final long seconds;

    /** {@code seconds = 0}: choose a duration; otherwise confirm that duration. */
    public ExcludeMenu(BoxCasinoPlugin plugin, Player player, long seconds) {
        super(plugin, player);
        this.seconds = seconds;
        Messages m = plugin.messages();
        this.inventory = Bukkit.createInventory(this, 27, m.text("exclude.title"));
        if (seconds == 0) {
            int i = 0;
            for (long s : DURATIONS.values()) {
                inventory.setItem(CHOICE_SLOTS[i++], item(Material.CLOCK, m.line("exclude.choice.name", "time", Format.duration(s)),
                        m.lines("exclude.choice.lore")));
            }
        } else {
            inventory.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, m.line("exclude.confirm.name"),
                    m.lines("exclude.confirm.lore", "time", Format.duration(seconds))));
            inventory.setItem(CANCEL_SLOT, item(Material.RED_CONCRETE, m.line("exclude.cancel.name"), List.of()));
        }
    }

    private static ItemStack item(Material material, net.kyori.adventure.text.Component name,
                                  List<net.kyori.adventure.text.Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (seconds == 0) {
            int i = 0;
            for (long s : DURATIONS.values()) {
                if (CHOICE_SLOTS[i++] == slot) {
                    new ExcludeMenu(plugin, player, s).open();
                    return;
                }
            }
            return;
        }
        if (slot == CANCEL_SLOT) {
            player.closeInventory();
        } else if (slot == CONFIRM_SLOT) {
            Profile p = plugin.profiles().get(player.getUniqueId());
            if (p == null) {
                plugin.messages().send(player, "access.not-loaded");
                return;
            }
            p.excludedUntil = Math.max(p.excludedUntil, System.currentTimeMillis() + seconds * 1000);
            plugin.profiles().saveLimits(p);
            player.closeInventory();
            plugin.messages().send(player, "exclude.done", "time", Format.duration(seconds));
        }
    }
}
