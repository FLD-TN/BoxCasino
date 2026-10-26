package com.dex.boxcasino.menu;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.gui.CasinoView;
import com.dex.boxcasino.storage.ProfileStore;
import com.dex.boxcasino.util.Format;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** /casino thongke: one item per game and currency, loaded from the DB thread. */
public final class StatsMenu extends CasinoView {

    private static final int BACK_SLOT = 49;

    public StatsMenu(BoxCasinoPlugin plugin, Player player) {
        super(plugin, player);
        this.inventory = Bukkit.createInventory(this, 54, plugin.messages().text("stats.title"));
        inventory.setItem(BACK_SLOT, item(Material.ARROW, plugin.messages().line("stats.back"), List.of()));
        UUID uuid = player.getUniqueId();
        plugin.database().async(() -> {
            List<ProfileStore.StatRow> rows = plugin.profileStore().stats(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> fill(rows));
        });
    }

    private void fill(List<ProfileStore.StatRow> rows) {
        if (isClosed()) return;
        Messages m = plugin.messages();
        if (rows.isEmpty()) {
            inventory.setItem(22, item(Material.BARRIER, m.line("stats.empty"), List.of()));
            return;
        }
        int slot = 0;
        for (ProfileStore.StatRow r : rows) {
            if (slot >= 45) break;
            Game game = plugin.games().get(r.game());
            String name = game == null ? r.game() : game.displayName();
            long net = r.paid() - r.wagered();
            inventory.setItem(slot++, item(Material.PAPER,
                    m.line("stats.entry.name", "game", name, "currency", plugin.wallet().name(r.currency())),
                    m.lines("stats.entry.lore",
                            "rounds", Format.number(r.rounds()), "wins", Format.number(r.wins()),
                            "losses", Format.number(r.losses()), "draws", Format.number(r.draws()),
                            "wagered", plugin.wallet().format(r.currency(), r.wagered()),
                            "paid", plugin.wallet().format(r.currency(), r.paid()),
                            "net", (net >= 0 ? m.raw("stats.plus") : m.raw("stats.minus"))
                                    + plugin.wallet().format(r.currency(), Math.abs(net)),
                            "biggest", plugin.wallet().format(r.currency(), r.biggestWin()))));
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
        if (slot == BACK_SLOT) new MainMenu(plugin, player, false).open();
    }
}
