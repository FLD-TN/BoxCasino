package com.dex.boxcasino.game.lottery;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.game.GameView;
import com.dex.boxcasino.util.Format;
import com.dex.boxcasino.util.Payouts;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.scheduler.BukkitTask;

/** One player's ticket. States: IDLE → RUNNING (scratching) → ENDED → IDLE. Every cell is drawn when buying. */
final class LotteryView extends GameView {

    private enum State { IDLE, RUNNING, ENDED }

    private final LotteryGame lottery;
    private State state = State.IDLE;
    private Bet bet;
    /** Prize of every cell of the ticket, by index in {@code slots.cells}. */
    private final List<LotteryGame.Prize> ticket = new ArrayList<>();
    private final Set<Integer> scratched = new LinkedHashSet<>();
    private BukkitTask task;

    LotteryView(BoxCasinoPlugin plugin, Player player, LotteryGame game, boolean npc) {
        super(plugin, player, game, npc);
        this.lottery = game;
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
        if (slot == lottery.spotSlot) {
            if (state == State.RUNNING) reject();
            else buy();
            return;
        }
        if (lottery.leverSlots.contains(slot)) {
            if (state == State.RUNNING && task == null) scratchAll();
            else reject();
            return;
        }
        int cell = lottery.cells.indexOf(slot);
        if (cell < 0) return;
        if (state != State.RUNNING || task != null || scratched.contains(cell)) {
            reject();
            return;
        }
        scratch(cell);
        if (scratched.size() >= lottery.picks) finish(true);
        else render();
    }

    private void buy() {
        if (state == State.ENDED) reset();
        if (!mayStart()) {
            reject();
            return;
        }
        List<LotteryGame.Prize> drawn = new ArrayList<>();
        for (int i = 0; i < lottery.cells.size(); i++) drawn.add(lottery.draw(plugin.random()));
        String outcome = drawn.stream().map(p -> Format.decimal(p.multiplier()))
                .collect(Collectors.joining(",", "{\"cells\":[", "]}"));
        Bet placed = plugin.bets().place(player, game.id(), currency(), amount(), outcome);
        if (placed == null) {
            reject();
            return;
        }
        bet = placed;
        ticket.clear();
        ticket.addAll(drawn);
        scratched.clear();
        state = State.RUNNING;
        sound(Sound.ITEM_BOOK_PAGE_TURN);
        render();
    }

    private void scratch(int cell) {
        scratched.add(cell);
        sound(Sound.BLOCK_SAND_BREAK);
        renderCell(cell);
    }

    /** The pencil: scratches the remaining picks one by one. */
    private void scratchAll() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.RUNNING) return;
            scratch(randomUnscratched());
            if (scratched.size() >= lottery.picks) {
                task.cancel();
                task = null;
                finish(true);
            } else {
                renderLever();
            }
        }, 1L, lottery.revealTicks);
        render();
    }

    private int randomUnscratched() {
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < ticket.size(); i++) if (!scratched.contains(i)) left.add(i);
        return left.get(plugin.random().nextInt(left.size()));
    }

    private double best() {
        double best = 0;
        for (int i : scratched) best = Math.max(best, ticket.get(i).multiplier());
        return best;
    }

    private void finish(boolean visible) {
        while (scratched.size() < lottery.picks) scratched.add(randomUnscratched());
        double multiplier = best();
        long payout = Payouts.payout(bet.amount(), multiplier);
        plugin.bets().settle(bet, payout);
        state = State.ENDED;
        String stake = plugin.wallet().format(bet.currency, bet.amount());
        plugin.messages().send(player, payout > bet.amount() ? "games.lottery.won"
                        : payout == bet.amount() ? "games.lottery.even" : "games.lottery.lost",
                "multiplier", Format.decimal(multiplier), "payout", plugin.wallet().format(bet.currency, payout), "bet", stake);
        if (!visible) return;
        sound(payout >= bet.amount() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_BASS);
        render();
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            task = null;
            if (state == State.ENDED && !isClosed()) reset();
        }, lottery.resetTicks);
    }

    private void reset() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        state = State.IDLE;
        bet = null;
        ticket.clear();
        scratched.clear();
        render();
    }

    @Override
    protected void onClose() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (state == State.RUNNING) finish(false);
    }

    @Override
    protected void redraw() {
        render();
    }

    private void render() {
        renderPanel();
        for (int i = 0; i < lottery.cells.size(); i++) renderCell(i);
        renderSpot();
        renderLever();
    }

    private void renderCell(int i) {
        Messages m = plugin.messages();
        int slot = lottery.cells.get(i);
        if (state == State.IDLE || ticket.isEmpty()) {
            set(slot, lottery.closedItem, m.line("games.lottery.cell-idle.name"), List.of());
            return;
        }
        LotteryGame.Prize p = ticket.get(i);
        String mul = Format.decimal(p.multiplier());
        if (scratched.contains(i)) {
            boolean top = state == State.ENDED && p.multiplier() == best();
            set(slot, p.look(), m.line(top ? "games.lottery.cell-best.name" : "games.lottery.cell-open.name", "multiplier", mul),
                    List.of());
        } else if (state == State.ENDED) {
            set(slot, p.look(), m.line("games.lottery.cell-missed.name", "multiplier", mul), List.of());
        } else {
            set(slot, lottery.closedItem, m.line("games.lottery.cell.name"),
                    m.lines("games.lottery.cell.lore", "left", String.valueOf(lottery.picks - scratched.size())));
        }
    }

    private void renderSpot() {
        Messages m = plugin.messages();
        if (state == State.RUNNING) {
            String b = plugin.wallet().format(bet.currency, bet.amount());
            set(lottery.spotSlot, chip(bet.currency, bet.amount(), m.line("games.lottery.spot-running.name", "bet", b), List.of()));
        } else {
            set(lottery.spotSlot, lottery.spotItem, m.line("games.lottery.spot.name"),
                    m.lines("games.lottery.spot.lore", "bet", plugin.wallet().format(currency(), amount()),
                            "picks", String.valueOf(lottery.picks), "cells", String.valueOf(lottery.cells.size())));
        }
    }

    private void renderLever() {
        Messages m = plugin.messages();
        for (int slot : lottery.leverSlots) {
            switch (state) {
                case IDLE -> set(slot, lottery.leverIdle, m.line("games.lottery.lever-idle.name"), List.of());
                case RUNNING -> {
                    String best = scratched.isEmpty() ? "-" : "x" + Format.decimal(best());
                    set(slot, lottery.leverRunning, m.line("games.lottery.lever-running.name"),
                            m.lines(task != null ? "games.lottery.lever-running.lore-auto" : "games.lottery.lever-running.lore",
                                    "left", String.valueOf(lottery.picks - scratched.size()), "best", best));
                }
                case ENDED -> set(slot, lottery.leverEnd, m.line("games.lottery.lever-end.name",
                        "multiplier", Format.decimal(best())), List.of());
            }
        }
    }
}
