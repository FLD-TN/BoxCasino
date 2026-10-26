package com.dex.boxcasino.game.xidach;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.game.GameView;
import com.dex.boxcasino.gui.Heads;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

/**
 * One player's window on a xì dách table. Own cards are face up; other players' cards stay face down until the
 * dealer reveals. The dealer shows one card until the players are done.
 */
final class XiDachView extends GameView {

    private final XiDachGame xd;
    private final XiDachTable table;
    /** Makes the viewer's own empty seat blink until they bet, plus an action bar hint. */
    private final BukkitTask blinkTask;
    private boolean blink;

    XiDachView(BoxCasinoPlugin plugin, Player player, XiDachGame game, XiDachTable table, boolean npc) {
        this(plugin, player, game, table, npc, -1);
    }

    XiDachView(BoxCasinoPlugin plugin, Player player, XiDachGame game, XiDachTable table, boolean npc, int seat) {
        super(plugin, player, game, npc);
        this.xd = game;
        this.table = table;
        table.join(this, player, seat);
        refresh();
        blinkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::blink, 10L, 10L);
    }

    /** True while the viewer sits without a bet and bets are open. */
    private boolean waitingForMyBet() {
        int mine = table.seatOf(player.getUniqueId());
        return mine >= 0 && table.seats[mine].bet == null
                && (table.phase == XiDachTable.Phase.WAITING || table.phase == XiDachTable.Phase.BETTING);
    }

    private void blink() {
        if (isClosed() || !waitingForMyBet()) return;
        blink = !blink;
        int mine = table.seatOf(player.getUniqueId());
        renderSeat(xd.seatSlots.get(mine), table.seats[mine], true);
        if (blink) player.sendActionBar(plugin.messages().text("games.xidach.hint-bet"));
    }

    @Override
    protected boolean locked() {
        int i = table.seatOf(player.getUniqueId());
        return i >= 0 && table.seats[i].bet != null;
    }

    @Override
    public boolean busy() {
        return table.inHand(player.getUniqueId());
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (handlePanel(slot, click)) {
            refresh();
            return;
        }
        int mine = table.seatOf(player.getUniqueId());
        if (mine >= 0 && slot == xd.seatSlots.get(mine)) {
            XiDachTable.Seat s = table.seats[mine];
            if (s.bet == null) {
                if (table.phase != XiDachTable.Phase.WAITING && table.phase != XiDachTable.Phase.BETTING) {
                    plugin.messages().send(player, "games.xidach.wait");
                    reject();
                } else if (mayStart() && table.placeBet(player, currency(), amount())) {
                    sound(Sound.BLOCK_NOTE_BLOCK_PLING);
                } else {
                    reject();
                }
            } else if (click.isRightClick() && table.cancelBet(player)) {
                plugin.messages().send(player, "games.xidach.cancelled");
            } else {
                reject();
            }
            return;
        }
        String action = slot == xd.hitSlot ? "hit" : slot == xd.standSlot ? "stand" : slot == xd.doubleSlot ? "double"
                : slot == xd.surrenderSlot ? "surrender" : null;
        if (action == null) return;
        if (table.act(player, action)) sound(Sound.UI_BUTTON_CLICK);
        else reject();
    }

    @Override
    protected void onClose() {
        blinkTask.cancel();
        if (busy()) plugin.rooms().offerReturn(player);
        table.leave(this, player.getUniqueId());
    }

    @Override
    protected void redraw() {
        refresh();
    }

    void refresh() {
        if (isClosed()) return;
        renderPanel();
        Messages m = plugin.messages();
        boolean reveal = table.phase == XiDachTable.Phase.DEALER || table.phase == XiDachTable.Phase.RESULT;
        for (int i = 0; i < table.seats.length; i++) {
            int slot = xd.seatSlots.get(i);
            XiDachTable.Seat s = table.seats[i];
            for (int c = 1; c <= 5; c++) inventory.setItem(slot + c, null);
            if (s == null) {
                set(slot, xd.seatFreeItem, m.line("games.xidach.seat-free.name"), m.lines("games.xidach.seat-free.lore"));
                continue;
            }
            boolean me = s.player.equals(player.getUniqueId());
            renderSeat(slot, s, me);
            for (int c = 0; c < s.cards.size(); c++) {
                boolean faceUp = me || reveal;
                set(slot + 1 + c, faceUp ? xd.cardItem(s.cards.get(c)) : xd.closedCard,
                        faceUp ? m.line("games.xidach.card.name") : m.line("games.xidach.card-hidden.name", "player", s.name),
                        List.of());
            }
        }
        renderDealer();
        renderButtons();
        refreshTimer();
    }

    private void renderSeat(int slot, XiDachTable.Seat s, boolean me) {
        Messages m = plugin.messages();
        if (s.bet == null) {
            if (me) {
                var name = m.line("games.xidach.seat-mine.name");
                var lore = m.lines("games.xidach.seat-mine.lore", "bet", plugin.wallet().format(currency(), amount()));
                // blinking: glowing chip of the chosen currency <-> the seat picture
                ItemStack look = blink ? plugin.items().make(xd.seatItem, name, lore) : chip(currency(), amount(), name, lore);
                ItemMeta meta = look.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                look.setItemMeta(meta);
                inventory.setItem(slot, look);
            } else {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                if (head.getItemMeta() instanceof SkullMeta skull) {
                    Heads.apply(skull, s.player);
                    skull.displayName(m.line("games.xidach.seat-other.name", "player", s.name));
                    head.setItemMeta(skull);
                }
                inventory.setItem(slot, head);
            }
            return;
        }
        String bet = plugin.wallet().format(s.bet.currency, s.bet.amount());
        boolean showHand = me || table.phase == XiDachTable.Phase.DEALER || table.phase == XiDachTable.Phase.RESULT;
        String hand = showHand && !s.cards.isEmpty() ? table.describe(s.cards, xd.minStand) : "?";
        if (table.phase == XiDachTable.Phase.RESULT && s.outcome != null) {
            ItemSpec look = s.surrendered ? xd.leftItem : switch (s.outcome) {
                case WIN -> xd.victoryItem;
                case DRAW -> xd.drawItem;
                case LOSE -> xd.defeatItem;
            };
            set(slot, look, m.line(s.surrendered ? "games.xidach.status.surrendered" : "games.xidach.status."
                            + s.outcome.name().toLowerCase(java.util.Locale.ROOT), "player", s.name),
                    m.lines("games.xidach.status.lore", "hand", hand, "bet", bet,
                            "payout", plugin.wallet().format(s.bet.currency, s.payout)));
            return;
        }
        String state = s.surrendered ? m.raw("games.xidach.state.surrendered") : s.done ? m.raw("games.xidach.state.done")
                : s.pending != null ? m.raw("games.xidach.state.chosen") : m.raw("games.xidach.state.thinking");
        set(slot, chip(s.bet.currency, s.bet.amount(), m.line("games.xidach.seat-bet.name", "player", s.name, "bet", bet), List.of()));
        ItemStack item = inventory.getItem(slot);
        if (item != null) {
            var meta = item.getItemMeta();
            meta.lore(m.lines(me && table.phase == XiDachTable.Phase.BETTING ? "games.xidach.seat-bet.lore-cancel"
                    : "games.xidach.seat-bet.lore", "hand", hand, "state", state, "doubled",
                    s.doubled ? m.raw("games.xidach.state.doubled") : ""));
            item.setItemMeta(meta);
        }
    }

    private void renderDealer() {
        Messages m = plugin.messages();
        for (int c = 1; c <= 5; c++) inventory.setItem(xd.dealerSlot + c, null);
        String hand = table.dealer.isEmpty() ? "-" : table.dealerRevealed ? table.describe(table.dealer, 0) : "?";
        set(xd.dealerSlot, xd.dealerItem, m.line("games.xidach.dealer.name"), m.lines("games.xidach.dealer.lore",
                "hand", hand, "min", String.valueOf(xd.dealerMinStand)));
        for (int c = 0; c < table.dealer.size(); c++) {
            boolean faceUp = c == 0 || table.dealerRevealed;
            set(xd.dealerSlot + 1 + c, faceUp ? xd.cardItem(table.dealer.get(c)) : xd.closedCard,
                    m.line(faceUp ? "games.xidach.card.name" : "games.xidach.card-dealer-hidden.name"), List.of());
        }
    }

    private void renderButtons() {
        Messages m = plugin.messages();
        int mine = table.seatOf(player.getUniqueId());
        XiDachTable.Seat s = mine >= 0 ? table.seats[mine] : null;
        boolean active = s != null && table.phase == XiDachTable.Phase.PLAYING && s.playing() && s.pending == null;
        int total = s == null || s.cards.isEmpty() ? 0 : Hand.total(s.cards);
        button(xd.hitSlot, "hit", active && s.cards.size() < 5);
        button(xd.standSlot, "stand", active && total >= xd.minStand);
        button(xd.doubleSlot, "double", active && s.firstAction && s.cards.size() == 2);
        button(xd.surrenderSlot, "surrender", active && s.firstAction);
        if (s != null && !s.cards.isEmpty() && table.phase == XiDachTable.Phase.PLAYING) {
            ItemStack stand = inventory.getItem(xd.standSlot);
            if (stand != null && total < xd.minStand) {
                var meta = stand.getItemMeta();
                meta.lore(m.lines("games.xidach.button.stand.lore-low", "min", String.valueOf(xd.minStand)));
                stand.setItemMeta(meta);
            }
        }
    }

    private void button(int slot, String key, boolean usable) {
        Messages m = plugin.messages();
        set(slot, xd.buttonItem, m.line("games.xidach.button." + key + (usable ? ".name" : ".name-off")),
                m.lines("games.xidach.button." + key + ".lore"));
    }

    void refreshTimer() {
        if (isClosed()) return;
        String key = switch (table.phase) {
            case WAITING -> "games.xidach.timer.waiting";
            case BETTING -> "games.xidach.timer.betting";
            case PLAYING -> "games.xidach.timer.playing";
            case DEALER -> "games.xidach.timer.dealer";
            case RESULT -> "games.xidach.timer.result";
        };
        set(xd.timerSlot, xd.buttonItem, plugin.messages().line(key, "seconds", String.valueOf(Math.max(0, table.secondsLeft)),
                "table", String.valueOf(table.number)), List.of());
    }
}
