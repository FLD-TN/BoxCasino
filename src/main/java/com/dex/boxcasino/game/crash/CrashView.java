package com.dex.boxcasino.game.crash;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.game.GameView;
import com.dex.boxcasino.util.Format;
import com.dex.boxcasino.util.Payouts;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** One viewer of the shared flight. Closing it during the flight cashes out at once (ESC = rút ngay). */
final class CrashView extends GameView {

    private final CrashGame crash;
    private final CrashTable table;

    CrashView(BoxCasinoPlugin plugin, Player player, CrashGame game, boolean npc) {
        super(plugin, player, game, npc);
        this.crash = game;
        this.table = game.table();
        table.join(this);
        refresh();
    }

    private CrashTable.Entry mine() {
        return table.entryOf(player.getUniqueId());
    }

    private double auto() {
        return memory.options.getOrDefault("crash.auto", 0.0);
    }

    @Override
    protected boolean locked() {
        return mine() != null;
    }

    @Override
    public boolean busy() {
        CrashTable.Entry e = mine();
        return e != null && e.cashedAt == 0 && e.bet.isOpen();
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (handlePanel(slot, click)) return;
        if (slot == crash.autoSlot) {
            List<Double> options = crash.autoOptions;
            int i = options.indexOf(auto());
            double next = options.get((Math.max(0, i) + (click.isRightClick() ? options.size() - 1 : 1)) % options.size());
            memory.options.put("crash.auto", next);
            table.setAuto(player.getUniqueId(), next);
            sound(Sound.UI_BUTTON_CLICK);
            refresh();
            return;
        }
        if (slot != crash.buttonSlot && slot != crash.statusSlot) return;
        CrashTable.Entry e = mine();
        CrashTable.Phase phase = table.phase();
        if (e == null) {
            if (!table.acceptsBets()) {
                plugin.messages().send(player, "games.crash.wait");
                reject();
            } else if (mayStart() && table.place(player, currency(), amount(), auto())) {
                sound(Sound.BLOCK_NOTE_BLOCK_PLING);
                plugin.messages().send(player, "games.crash.placed", "bet", plugin.wallet().format(currency(), amount()));
            } else {
                reject();
            }
            return;
        }
        if (phase == CrashTable.Phase.BETTING) {
            if (click.isRightClick() && table.cancel(player)) plugin.messages().send(player, "games.crash.cancelled");
            else reject();
            return;
        }
        if (!table.cashOut(player.getUniqueId())) reject();
    }

    /** ESC during the flight = cash out now; a bet still waiting for take-off stays (cashed out at x1.00 then). */
    @Override
    protected void onClose() {
        table.leave(this);
        CrashTable.Entry e = mine();
        if (e == null) return;
        if (table.phase() == CrashTable.Phase.FLYING) table.cashOut(player.getUniqueId());
        else if (table.phase() == CrashTable.Phase.BETTING) plugin.rooms().offerReturn(player);
    }

    @Override
    protected void redraw() {
        refresh();
    }

    void refresh() {
        if (isClosed()) return;
        renderPanel();
        renderHistory();
        renderAuto();
        refreshFlight();
    }

    /** Countdown: status and button only. */
    void refreshStatus() {
        if (isClosed()) return;
        renderStatus();
        renderButton();
    }

    /** Every step of the flight. */
    void refreshFlight() {
        if (isClosed()) return;
        renderStatus();
        renderButton();
        renderProgress();
    }

    private void renderStatus() {
        Messages m = plugin.messages();
        String mul = Format.decimal(table.multiplier());
        String key = switch (table.phase()) {
            case IDLE -> "games.crash.status-idle";
            case BETTING -> "games.crash.status-betting";
            case FLYING -> "games.crash.status-flying";
            case CRASHED -> "games.crash.status-crashed";
        };
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>(m.lines(key + ".lore"));
        int shown = 0;
        for (CrashTable.Entry e : table.entries()) {
            if (shown++ >= 10) break;
            String bet = plugin.wallet().format(e.bet.currency, e.bet.amount());
            if (e.cashedAt > 0) {
                lore.add(m.line("games.crash.player-out", "player", e.name, "bet", bet, "multiplier", Format.decimal(e.cashedAt)));
            } else if (table.phase() == CrashTable.Phase.CRASHED) {
                lore.add(m.line("games.crash.player-lost", "player", e.name, "bet", bet));
            } else {
                lore.add(m.line("games.crash.player-in", "player", e.name, "bet", bet));
            }
        }
        set(crash.statusSlot, table.phase() == CrashTable.Phase.IDLE || table.phase() == CrashTable.Phase.BETTING
                        ? crash.statusItem : crash.colorOf(table.multiplier()),
                m.line(key + ".name", "seconds", String.valueOf(Math.max(0, table.secondsLeft())), "multiplier", mul), lore);
    }

    private void renderButton() {
        Messages m = plugin.messages();
        CrashTable.Entry e = mine();
        CrashTable.Phase phase = table.phase();
        String stake = plugin.wallet().format(currency(), amount());
        String autoText = auto() > 0 ? "x" + Format.decimal(auto()) : m.raw("games.crash.auto-off");
        if (e == null) {
            if (table.acceptsBets()) {
                set(crash.buttonSlot, crash.buttonActive, m.line("games.crash.button-bet.name"),
                        m.lines("games.crash.button-bet.lore", "bet", stake, "auto", autoText));
            } else {
                set(crash.buttonSlot, phase == CrashTable.Phase.FLYING ? crash.buttonFlying : crash.buttonEnd,
                        m.line("games.crash.button-wait.name"), List.of());
            }
            return;
        }
        String bet = plugin.wallet().format(e.bet.currency, e.bet.amount());
        switch (phase) {
            case BETTING -> set(crash.buttonSlot, crash.buttonIdle, m.line("games.crash.button-placed.name", "bet", bet),
                    m.lines("games.crash.button-placed.lore", "auto", e.auto > 0 ? "x" + Format.decimal(e.auto) : m.raw("games.crash.auto-off")));
            case FLYING -> {
                if (e.cashedAt > 0) {
                    set(crash.buttonSlot, crash.buttonEnd, m.line("games.crash.button-out.name",
                            "multiplier", Format.decimal(e.cashedAt)), List.of());
                } else {
                    long payout = Payouts.payout(e.bet.amount(), table.multiplier());
                    set(crash.buttonSlot, crash.buttonFlying, m.line("games.crash.button-cashout.name",
                                    "multiplier", Format.decimal(table.multiplier())),
                            m.lines("games.crash.button-cashout.lore", "payout", plugin.wallet().format(e.bet.currency, payout)));
                }
            }
            default -> set(crash.buttonSlot, crash.buttonEnd, m.line(e.cashedAt > 0 ? "games.crash.button-out.name"
                    : "games.crash.button-lost.name", "multiplier", Format.decimal(e.cashedAt)), List.of());
        }
    }

    private void renderProgress() {
        Messages m = plugin.messages();
        boolean flying = table.phase() == CrashTable.Phase.FLYING || table.phase() == CrashTable.Phase.CRASHED;
        for (int i = 0; i < crash.progressSlots.size(); i++) {
            double at = i < crash.progress.size() ? crash.progress.get(i) : Double.MAX_VALUE;
            boolean lit = flying && table.multiplier() >= at;
            set(crash.progressSlots.get(i), lit ? crash.colorOf(at) : crash.emptyItem,
                    m.line(lit ? "games.crash.progress-lit.name" : "games.crash.progress.name", "multiplier", Format.decimal(at)),
                    List.of());
        }
    }

    private void renderHistory() {
        Messages m = plugin.messages();
        List<Double> h = table.history();
        for (int i = 0; i < crash.historySlots.size(); i++) {
            int slot = crash.historySlots.get(i);
            if (i >= h.size()) {
                set(slot, crash.emptyItem, m.line("games.crash.history-empty.name"), List.of());
            } else {
                set(slot, crash.colorOf(h.get(i)), m.line("games.crash.history.name", "multiplier", Format.decimal(h.get(i)),
                        "number", String.valueOf(i + 1)), List.of());
            }
        }
    }

    private void renderAuto() {
        Messages m = plugin.messages();
        String current = auto() > 0 ? "x" + Format.decimal(auto()) : m.raw("games.crash.auto-off");
        set(crash.autoSlot, crash.autoItem, m.line("games.crash.auto.name", "auto", current), m.lines("games.crash.auto.lore"));
    }
}
