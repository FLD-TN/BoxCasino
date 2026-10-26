package com.dex.boxcasino.game.stairs;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.game.GameView;
import com.dex.boxcasino.util.Format;
import com.dex.boxcasino.util.Payouts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.scheduler.BukkitTask;

/** One player's Leo Thang board. States: IDLE → RUNNING → ENDED → IDLE. */
final class StairsView extends GameView {

    private enum State { IDLE, RUNNING, ENDED }

    private final StairsGame stairs;
    private State state = State.IDLE;
    private Bet bet;
    /** TNT columns of each row (0..width-1). */
    private final List<Set<Integer>> tnt = new ArrayList<>();
    /** Column picked in each cleared row. */
    private final List<Integer> picks = new ArrayList<>();
    /** Row (0-based) and column of the TNT that ended the round, -1 when none. */
    private int lostRow = -1;
    private int lostCol = -1;
    private BukkitTask resetTask;

    StairsView(BoxCasinoPlugin plugin, Player player, StairsGame game, boolean npc) {
        super(plugin, player, game, npc);
        this.stairs = game;
        render();
    }

    @Override
    protected boolean locked() {
        return state == State.RUNNING;
    }

    @Override
    public boolean busy() {
        return state == State.RUNNING;
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (handlePanel(slot, click)) return;
        if (slot == stairs.spotSlot) {
            if (state == State.RUNNING) {
                if (click.isRightClick() && picks.isEmpty()) cancelRound();
                else reject();
            } else {
                start();
            }
            return;
        }
        if (slot == stairs.leverSlot) {
            if (state == State.RUNNING && !picks.isEmpty()) cashOut(true);
            else reject();
            return;
        }
        if (state != State.RUNNING) return;
        int row = picks.size();
        int first = stairs.rowSlots.get(row);
        if (slot >= first && slot < first + stairs.width) pick(slot - first);
    }

    private void start() {
        if (state == State.ENDED) reset();
        if (!mayStart()) {
            reject();
            return;
        }
        List<Set<Integer>> placed = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        for (int c = 0; c < stairs.width; c++) cols.add(c);
        for (int r = 0; r < stairs.rows(); r++) {
            Collections.shuffle(cols, plugin.random());
            placed.add(new HashSet<>(cols.subList(0, stairs.tnt[r])));
        }
        String outcome = placed.stream()
                .map(s -> s.stream().sorted().map(String::valueOf).collect(Collectors.joining(",", "[", "]")))
                .collect(Collectors.joining(",", "{\"tnt\":[", "]}"));
        Bet placedBet = plugin.bets().place(player, game.id(), currency(), amount(), outcome);
        if (placedBet == null) {
            reject();
            return;
        }
        bet = placedBet;
        tnt.clear();
        tnt.addAll(placed);
        picks.clear();
        lostRow = -1;
        state = State.RUNNING;
        sound(Sound.UI_BUTTON_CLICK);
        render();
    }

    private void pick(int col) {
        int row = picks.size();
        if (tnt.get(row).contains(col)) {
            lostRow = row;
            lostCol = col;
            plugin.bets().settle(bet, 0);
            state = State.ENDED;
            sound(Sound.ENTITY_GENERIC_EXPLODE);
            plugin.messages().send(player, "games.stairs.lost", "bet", plugin.wallet().format(bet.currency, bet.amount()));
            render();
            scheduleReset();
            return;
        }
        picks.add(col);
        sound(Sound.BLOCK_NOTE_BLOCK_PLING);
        if (picks.size() == stairs.rows()) {
            cashOut(true);
            return;
        }
        render();
    }

    private void cashOut(boolean visible) {
        double multiplier = stairs.multiplier(picks.size());
        long payout = Payouts.payout(bet.amount(), multiplier);
        plugin.bets().settle(bet, payout);
        state = State.ENDED;
        plugin.messages().send(player, "games.stairs.won", "rows", String.valueOf(picks.size()),
                "multiplier", Format.decimal(multiplier), "payout", plugin.wallet().format(bet.currency, payout));
        if (!visible) return;
        sound(Sound.ENTITY_PLAYER_LEVELUP);
        render();
        scheduleReset();
    }

    private void cancelRound() {
        plugin.bets().refund(bet);
        state = State.IDLE;
        plugin.messages().send(player, "games.stairs.cancelled");
        render();
    }

    private void scheduleReset() {
        if (resetTask != null) resetTask.cancel();
        resetTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            resetTask = null;
            if (state == State.ENDED && !isClosed()) reset();
        }, stairs.resetTicks);
    }

    private void reset() {
        if (resetTask != null) {
            resetTask.cancel();
            resetTask = null;
        }
        state = State.IDLE;
        bet = null;
        tnt.clear();
        picks.clear();
        lostRow = -1;
        render();
    }

    @Override
    protected void onClose() {
        if (resetTask != null) resetTask.cancel();
        if (state != State.RUNNING) return;
        if (picks.isEmpty()) {
            plugin.bets().refund(bet);
            plugin.messages().send(player, "games.stairs.closed-refund");
        } else {
            cashOut(false);
        }
        state = State.IDLE;
    }

    @Override
    protected void redraw() {
        render();
    }

    private void render() {
        Messages m = plugin.messages();
        renderPanel();
        int current = state == State.RUNNING ? picks.size() : -1;
        for (int r = 0; r < stairs.rows(); r++) {
            int first = stairs.rowSlots.get(r);
            boolean revealed = state == State.ENDED && !tnt.isEmpty();
            for (int c = 0; c < stairs.width; c++) {
                int slot = first + c;
                if (r < picks.size() && picks.get(r) == c) {
                    set(slot, stairs.clickedItem, m.line("games.stairs.cell-clicked.name"), List.of());
                } else if ((r < picks.size() || revealed) && tnt.size() > r && tnt.get(r).contains(c)) {
                    set(slot, stairs.tntItem, m.line(r == lostRow && c == lostCol ? "games.stairs.cell-boom.name"
                            : "games.stairs.cell-tnt.name"), List.of());
                } else if (r < picks.size() || (revealed && r == lostRow)) {
                    set(slot, stairs.passedItem, m.line("games.stairs.cell-safe.name"), List.of());
                } else if (r == current) {
                    set(slot, stairs.currentItem, m.line("games.stairs.cell-current.name",
                            "multiplier", Format.decimal(stairs.multiplier(r + 1))), List.of());
                } else {
                    set(slot, stairs.closedItem, m.line(state == State.RUNNING ? "games.stairs.cell-locked.name"
                            : "games.stairs.cell-idle.name"), List.of());
                }
            }
            if (r < stairs.labelSlots.size()) {
                set(stairs.labelSlots.get(r), stairs.labelItem,
                        m.line("games.stairs.label.name", "row", String.valueOf(r + 1),
                                "multiplier", Format.decimal(stairs.multiplier(r + 1))),
                        m.lines("games.stairs.label.lore", "tnt", String.valueOf(stairs.tnt[r]),
                                "width", String.valueOf(stairs.width)));
            }
        }
        if (state == State.RUNNING) {
            set(stairs.spotSlot, chip(bet.currency, bet.amount(), m.line("games.stairs.spot-running.name",
                    "bet", plugin.wallet().format(bet.currency, bet.amount())),
                    m.lines(picks.isEmpty() ? "games.stairs.spot-running.lore-cancel" : "games.stairs.spot-running.lore")));
            double mul = stairs.multiplier(picks.size());
            set(stairs.leverSlot, stairs.leverRunning, m.line(picks.isEmpty() ? "games.stairs.lever-running.name-none"
                            : "games.stairs.lever-running.name", "multiplier", Format.decimal(mul)),
                    m.lines("games.stairs.lever-running.lore", "rows", String.valueOf(picks.size()),
                            "payout", plugin.wallet().format(bet.currency, Payouts.payout(bet.amount(), mul)),
                            "next", Format.decimal(stairs.multiplier(picks.size() + 1))));
        } else {
            set(stairs.spotSlot, stairs.spotItem, m.line("games.stairs.spot.name"),
                    m.lines("games.stairs.spot.lore", "bet", plugin.wallet().format(currency(), amount())));
            set(stairs.leverSlot, state == State.ENDED ? stairs.leverEnd : stairs.leverIdle,
                    m.line(state == State.ENDED ? "games.stairs.lever-end.name" : "games.stairs.lever-idle.name"),
                    List.of());
        }
    }
}
