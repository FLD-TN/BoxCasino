package com.dex.boxcasino.game.mines;

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

/** One player's Dò Mìn board. States: IDLE → RUNNING → ENDED → IDLE. */
final class MinesView extends GameView {

    private enum State { IDLE, RUNNING, ENDED }

    private final MinesGame mines;
    private State state = State.IDLE;
    private MinesGame.Difficulty difficulty;
    private Bet bet;
    private final Set<Integer> mineSlots = new HashSet<>();
    private final Set<Integer> opened = new HashSet<>();
    private BukkitTask resetTask;

    MinesView(BoxCasinoPlugin plugin, Player player, MinesGame game, boolean npc) {
        super(plugin, player, game, npc);
        this.mines = game;
        this.difficulty = game.difficulties.values().iterator().next();
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
        if (slot == mines.spotSlot) {
            if (state == State.RUNNING) {
                if (click.isRightClick() && opened.isEmpty()) cancelRound();
                else reject();
            } else {
                start();
            }
            return;
        }
        if (mines.leverSlots.contains(slot)) {
            if (state == State.RUNNING && !opened.isEmpty()) cashOut(true);
            else reject();
            return;
        }
        for (MinesGame.Difficulty d : mines.difficulties.values()) {
            if (d.slot() == slot) {
                if (state == State.RUNNING || d == difficulty) {
                    reject();
                } else {
                    difficulty = d;
                    if (state == State.ENDED) reset();
                    else render();
                    sound(Sound.UI_BUTTON_CLICK);
                }
                return;
            }
        }
        if (state == State.RUNNING && mines.cells.contains(slot) && !opened.contains(slot)) reveal(slot);
    }

    private void start() {
        if (state == State.ENDED) reset();
        if (!mayStart()) {
            reject();
            return;
        }
        List<Integer> shuffled = new ArrayList<>(mines.cells);
        Collections.shuffle(shuffled, plugin.random());
        Set<Integer> placed = new HashSet<>(shuffled.subList(0, difficulty.mines()));
        String outcome = "{\"difficulty\":\"" + difficulty.key() + "\",\"mines\":"
                + placed.stream().sorted().map(String::valueOf).collect(Collectors.joining(",", "[", "]")) + "}";
        Bet placedBet = plugin.bets().place(player, game.id(), currency(), amount(), outcome);
        if (placedBet == null) {
            reject();
            return;
        }
        bet = placedBet;
        mineSlots.clear();
        mineSlots.addAll(placed);
        opened.clear();
        state = State.RUNNING;
        sound(Sound.BLOCK_CHEST_OPEN);
        render();
    }

    private void reveal(int slot) {
        if (mineSlots.contains(slot)) {
            plugin.bets().settle(bet, 0);
            state = State.ENDED;
            set(slot, mines.mineItem, plugin.messages().line("games.mines.mine.name"), List.of());
            sound(Sound.ENTITY_GENERIC_EXPLODE);
            plugin.messages().send(player, "games.mines.lost", "bet", plugin.wallet().format(bet.currency, bet.amount()));
            render();
            scheduleReset();
            return;
        }
        opened.add(slot);
        sound(Sound.BLOCK_NOTE_BLOCK_PLING);
        if (opened.size() == mines.cells.size() - difficulty.mines()) {
            cashOut(true);
            return;
        }
        render();
    }

    private void cashOut(boolean visible) {
        double multiplier = mines.multiplier(difficulty.mines(), opened.size());
        long payout = Payouts.payout(bet.amount(), multiplier);
        plugin.bets().settle(bet, payout);
        state = State.ENDED;
        plugin.messages().send(player, "games.mines.won", "multiplier", Format.decimal(multiplier),
                "payout", plugin.wallet().format(bet.currency, payout));
        if (!visible) return;
        sound(Sound.ENTITY_PLAYER_LEVELUP);
        render();
        scheduleReset();
    }

    private void cancelRound() {
        plugin.bets().refund(bet);
        state = State.IDLE;
        plugin.messages().send(player, "games.mines.cancelled");
        sound(Sound.UI_BUTTON_CLICK);
        render();
    }

    private void scheduleReset() {
        if (resetTask != null) resetTask.cancel();
        resetTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            resetTask = null;
            if (state == State.ENDED && !isClosed()) reset();
        }, mines.resetTicks);
    }

    private void reset() {
        if (resetTask != null) {
            resetTask.cancel();
            resetTask = null;
        }
        state = State.IDLE;
        bet = null;
        mineSlots.clear();
        opened.clear();
        render();
    }

    @Override
    protected void onClose() {
        if (resetTask != null) resetTask.cancel();
        if (state != State.RUNNING) return;
        if (opened.isEmpty()) {
            plugin.bets().refund(bet);
            plugin.messages().send(player, "games.mines.closed-refund");
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
        boolean showMines = state == State.ENDED;
        for (int slot : mines.cells) {
            if (opened.contains(slot)) {
                set(slot, difficulty.opened(), m.line("games.mines.safe.name"), List.of());
            } else if (showMines && mineSlots.contains(slot)) {
                set(slot, mines.mineItem, m.line("games.mines.mine.name"), List.of());
            } else {
                set(slot, mines.closedItem, m.line(state == State.RUNNING ? "games.mines.cell.name" : "games.mines.cell-idle.name"),
                        List.of());
            }
        }
        for (MinesGame.Difficulty d : mines.difficulties.values()) {
            set(d.slot(), d == difficulty ? d.active() : d.inactive(),
                    m.line("games.mines.difficulty.name", "name", m.raw("games.mines.difficulty." + d.key()),
                            "mines", String.valueOf(d.mines())),
                    m.lines(d == difficulty ? "games.mines.difficulty.lore-active" : "games.mines.difficulty.lore",
                            "mines", String.valueOf(d.mines())));
        }
        if (state == State.RUNNING) {
            String ph1 = plugin.wallet().format(bet.currency, bet.amount());
            set(mines.spotSlot, chip(bet.currency, bet.amount(), m.line("games.mines.spot-running.name", "bet", ph1),
                    m.lines(opened.isEmpty() ? "games.mines.spot-running.lore-cancel" : "games.mines.spot-running.lore")));
        } else {
            set(mines.spotSlot, mines.spotItem, m.line("games.mines.spot.name"),
                    m.lines("games.mines.spot.lore", "bet", plugin.wallet().format(currency(), amount()),
                            "mines", String.valueOf(difficulty.mines())));
        }
        String nextMul = Format.decimal(mines.multiplier(difficulty.mines(), opened.size() + 1));
        for (int slot : mines.leverSlots) {
            switch (state) {
                case IDLE -> set(slot, mines.leverIdle, m.line("games.mines.lever-idle.name"),
                        m.lines("games.mines.lever-idle.lore", "next", nextMul));
                case RUNNING -> {
                    double mul = mines.multiplier(difficulty.mines(), opened.size());
                    long payout = Payouts.payout(bet.amount(), mul);
                    set(slot, mines.leverRunning, m.line(opened.isEmpty() ? "games.mines.lever-running.name-none"
                                    : "games.mines.lever-running.name", "multiplier", Format.decimal(mul)),
                            m.lines("games.mines.lever-running.lore", "multiplier", Format.decimal(mul),
                                    "payout", plugin.wallet().format(bet.currency, payout), "next", nextMul,
                                    "opened", String.valueOf(opened.size())));
                }
                case ENDED -> set(slot, mines.leverEnd, m.line("games.mines.lever-end.name"), List.of());
            }
        }
    }
}
