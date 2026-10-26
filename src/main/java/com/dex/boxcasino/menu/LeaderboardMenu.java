package com.dex.boxcasino.menu;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.extra.LeaderboardService;
import com.dex.boxcasino.gui.CasinoView;
import com.dex.boxcasino.gui.Heads;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Top 10 net winners (or losers) as a pyramid: #1 on top, then 2, 3 and 4 per row. Switch currency and winners /
 * losers at the bottom.
 */
public final class LeaderboardMenu extends CasinoView {

    private static final int[] PLACES = {13, 21, 23, 29, 31, 33, 37, 39, 41, 43};
    private static final int BACK = 45;
    private static final int SWITCH = 49;
    private static final int BOARD = 51;

    private Currency currency;
    private LeaderboardService.Board board = LeaderboardService.Board.WINNERS;

    public LeaderboardMenu(BoxCasinoPlugin plugin, Player player, Currency currency) {
        super(plugin, player);
        this.currency = currency;
        this.inventory = Bukkit.createInventory(this, 54, plugin.messages().text("leaderboard.title"));
        render();
    }

    private void render() {
        Messages m = plugin.messages();
        inventory.clear();
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, m.line("leaderboard.filler"), List.of());
        for (int i = 0; i < 9; i++) inventory.setItem(i, filler);
        for (int i = 45; i < 54; i++) inventory.setItem(i, filler);
        boolean winners = board == LeaderboardService.Board.WINNERS;
        String kind = winners ? "leaderboard.winners" : "leaderboard.losers";
        inventory.setItem(4, item(currency == Currency.MONEY ? Material.EMERALD : Material.AMETHYST_SHARD,
                m.line(kind + ".header.name", "currency", plugin.wallet().name(currency)), m.lines(kind + ".header.lore")));
        List<LeaderboardService.Entry> top = plugin.leaderboard().top(board, currency);
        for (int i = 0; i < PLACES.length; i++) {
            if (i >= top.size()) {
                inventory.setItem(PLACES[i], item(Material.GRAY_STAINED_GLASS_PANE,
                        m.line("leaderboard.empty", "rank", String.valueOf(i + 1)), List.of()));
                continue;
            }
            LeaderboardService.Entry e = top.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta skull) {
                Heads.apply(skull, e.player());
                String rankColor = m.raw(i == 0 ? "leaderboard.rank-1" : i == 1 ? "leaderboard.rank-2"
                        : i == 2 ? "leaderboard.rank-3" : "leaderboard.rank-other");
                skull.displayName(m.line("leaderboard.entry.name", "color", rankColor, "rank", String.valueOf(i + 1),
                        "player", e.name()));
                skull.lore(m.lines(kind + ".entry-lore", "net", plugin.wallet().format(currency, e.net())));
                head.setItemMeta(skull);
            }
            inventory.setItem(PLACES[i], head);
        }
        inventory.setItem(BACK, item(Material.ARROW, m.line("leaderboard.back"), List.of()));
        inventory.setItem(SWITCH, item(currency == Currency.MONEY ? Material.AMETHYST_SHARD : Material.EMERALD,
                m.line("leaderboard.switch.name", "currency", plugin.wallet().name(currency.other())),
                m.lines("leaderboard.switch.lore")));
        inventory.setItem(BOARD, item(winners ? Material.REDSTONE : Material.GOLD_INGOT,
                m.line(winners ? "leaderboard.board.losers" : "leaderboard.board.winners"), m.lines("leaderboard.board.lore")));
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
        if (slot == BACK) {
            new MainMenu(plugin, player, false).open();
        } else if (slot == SWITCH) {
            currency = currency.other();
            render();
        } else if (slot == BOARD) {
            board = board == LeaderboardService.Board.WINNERS ? LeaderboardService.Board.LOSERS : LeaderboardService.Board.WINNERS;
            render();
        }
    }
}
