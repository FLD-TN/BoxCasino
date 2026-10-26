package com.dex.boxcasino.game.pot;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.game.GameView;
import com.dex.boxcasino.gui.Heads;
import com.dex.boxcasino.util.Format;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * One viewer of a pot (the pot of the currency picked on the bet panel). Closing it never touches the stake: the
 * draw pays even when the player is gone.
 */
final class PotView extends GameView {

    private final PotGame pot;
    private PotTable table;

    PotView(BoxCasinoPlugin plugin, Player player, PotGame game, boolean npc) {
        super(plugin, player, game, npc);
        this.pot = game;
        PotTable mine = game.tableOf(player.getUniqueId());
        // already in a pot: show that one (and its currency)
        if (mine != null) memory.currency = mine.currency;
        this.table = game.table(currency());
        table.join(this);
        refresh();
    }

    /** The bet amount and currency can change at any time: the amount is what a click puts in. */
    @Override
    protected boolean locked() {
        return false;
    }

    @Override
    public boolean busy() {
        return pot.tableOf(player.getUniqueId()) != null;
    }

    @Override
    protected void onCurrencyChanged() {
        table.leave(this);
        table = pot.table(currency());
        table.join(this);
        refresh();
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (handlePanel(slot, click)) return;
        int color = pot.spotSlots.indexOf(slot);
        if (color < 0) return;
        PotTable.Entry mine = table.entryOf(player.getUniqueId());
        PotTable.Entry there = table.entryOfColor(color);
        if (there != null && there != mine) {
            plugin.messages().send(player, "games.pot.color-taken", "player", there.name());
            reject();
            return;
        }
        if (!table.open()) {
            plugin.messages().send(player, "games.pot.wait");
            reject();
            return;
        }
        if (mine == null) {
            PotTable other = pot.tableOf(player.getUniqueId());
            if (other != null) {
                plugin.messages().send(player, "games.pot.in-other", "currency", plugin.wallet().name(other.currency));
                reject();
            } else if (mayStart() && table.enter(player, color, amount())) {
                sound(Sound.BLOCK_NOTE_BLOCK_PLING);
                plugin.messages().send(player, "games.pot.entered", "bet", plugin.wallet().format(currency(), amount()));
            } else {
                reject();
            }
            return;
        }
        if (click.isRightClick()) {
            if (table.withdraw(player)) plugin.messages().send(player, "games.pot.withdrawn");
            else reject();
            return;
        }
        long before = mine.bet().amount();
        if (mayStart() && table.add(player, amount())) {
            sound(Sound.BLOCK_NOTE_BLOCK_PLING);
            plugin.messages().send(player, "games.pot.added", "amount", plugin.wallet().format(currency(),
                    mine.bet().amount() - before), "bet", plugin.wallet().format(currency(), mine.bet().amount()));
        } else {
            reject();
        }
    }

    @Override
    protected void onClose() {
        table.leave(this);
        if (busy()) plugin.rooms().offerReturn(player);
    }

    @Override
    protected void redraw() {
        refresh();
    }

    void refresh() {
        if (isClosed()) return;
        renderPanel();
        renderSeats();
        refreshStrip();
        refreshStatus();
    }

    void refreshStatus() {
        if (isClosed()) return;
        Messages m = plugin.messages();
        long total = table.pot();
        String key;
        ItemSpec look;
        switch (table.phase()) {
            case WAITING -> {
                key = table.entries().isEmpty() ? "games.pot.status-empty" : "games.pot.status-waiting";
                look = pot.statusIdle;
            }
            case COUNTDOWN -> {
                key = "games.pot.status-countdown";
                look = pot.statusCountdown;
            }
            case ROLLING -> {
                key = "games.pot.status-rolling";
                look = pot.statusRolling;
            }
            default -> {
                key = "games.pot.status-result";
                look = pot.statusEnd;
            }
        }
        PotTable.Entry w = table.winner();
        String[] ph = {
                "seconds", String.valueOf(Math.max(0, table.secondsLeft())),
                "wait", Format.duration(table.waitSecondsLeft()),
                "pot", plugin.wallet().format(table.currency, total),
                "prize", plugin.wallet().format(table.currency, pot.winnerPayout(total)),
                "players", String.valueOf(table.entries().size()),
                "max", String.valueOf(pot.spotSlots.size()),
                "min", String.valueOf(pot.minPlayers),
                "tax", Format.decimal(pot.taxPercent()),
                "currency", plugin.wallet().name(table.currency),
                "winner", w == null ? "" : w.name(),
                "payout", plugin.wallet().format(table.currency, table.lastPayout())
        };
        for (int slot : pot.statusSlots) set(slot, look, m.line(key + ".name", ph), m.lines(key + ".lore", ph));
    }

    void refreshStrip() {
        if (isClosed()) return;
        Messages m = plugin.messages();
        int middle = pot.stripSlots.size() / 2;
        for (int i = 0; i < pot.stripSlots.size(); i++) {
            int color = table.stripColor(i);
            int slot = pot.stripSlots.get(i);
            if (color < 0) {
                set(slot, pot.emptyItem, m.line(i == middle ? "games.pot.pointer.name" : "games.pot.strip-empty.name"), List.of());
                continue;
            }
            PotTable.Entry e = table.entryOfColor(color);
            set(slot, pot.colors.get(color), m.line(i == middle ? "games.pot.pointer-color.name" : "games.pot.strip.name",
                    "player", e == null ? "?" : e.name()), List.of());
        }
    }

    private void renderSeats() {
        Messages m = plugin.messages();
        long total = table.pot();
        PotTable.Entry mine = table.entryOf(player.getUniqueId());
        String stake = plugin.wallet().format(currency(), amount());
        for (int color = 0; color < pot.spotSlots.size(); color++) {
            PotTable.Entry e = table.entryOfColor(color);
            int spot = pot.spotSlots.get(color);
            int headSlot = color < pot.headSlots.size() ? pot.headSlots.get(color) : -1;
            if (e == null) {
                set(spot, pot.colors.get(color), m.line("games.pot.spot-free.name", "number", String.valueOf(color + 1)),
                        m.lines(mine == null && table.open() ? "games.pot.spot-free.lore" : "games.pot.spot-free.lore-closed",
                                "bet", stake));
                if (headSlot >= 0) set(headSlot, pot.emptyItem, m.line("games.pot.head-empty.name"), List.of());
                continue;
            }
            String bet = plugin.wallet().format(table.currency, e.bet().amount());
            String chance = Format.percent(total == 0 ? 0 : e.bet().amount() / (double) total);
            boolean own = e == mine;
            String lorePath = !own ? "games.pot.spot-taken.lore"
                    : table.phase() == PotTable.Phase.WAITING ? "games.pot.spot-own.lore-waiting"
                    : table.open() ? "games.pot.spot-own.lore" : "games.pot.spot-own.lore-closed";
            set(spot, pot.colors.get(color), m.line(own ? "games.pot.spot-own.name" : "games.pot.spot-taken.name",
                    "player", e.name(), "bet", bet), m.lines(lorePath, "bet", bet, "chance", chance, "add", stake));
            if (headSlot >= 0) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                if (head.getItemMeta() instanceof SkullMeta skull) {
                    Heads.apply(skull, e.bet().player);
                    skull.displayName(m.line("games.pot.head.name", "player", e.name()));
                    skull.lore(m.lines("games.pot.head.lore", "bet", bet, "chance", chance));
                    head.setItemMeta(skull);
                }
                set(headSlot, head);
            }
        }
    }
}
