package com.dex.boxcasino.game.roulette;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.game.GameView;
import com.dex.boxcasino.util.Format;
import com.dex.boxcasino.util.Payouts;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.scheduler.BukkitTask;

/** One player's Vòng Quay. The stop cell is drawn and stored before the marker starts running. */
final class RouletteView extends GameView {

    private final RouletteGame roulette;
    private RouletteGame.Difficulty difficulty;
    private Bet bet;
    private int target = -1;
    /** Steps the marker makes in total, and steps done. */
    private int totalSteps;
    private int step;
    /** Ring index the marker shows (also after the round, until reset). */
    private int marker = -1;
    private BukkitTask task;

    RouletteView(BoxCasinoPlugin plugin, Player player, RouletteGame game, boolean npc) {
        super(plugin, player, game, npc);
        this.roulette = game;
        this.difficulty = game.difficulties.values().iterator().next();
        render();
    }

    @Override
    protected boolean locked() {
        return bet != null;
    }

    @Override
    public boolean busy() {
        return bet != null;
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (handlePanel(slot, click)) return;
        if (slot == roulette.spotSlot || slot == roulette.leverSlot) {
            if (bet != null) reject();
            else spin();
            return;
        }
        for (RouletteGame.Difficulty d : roulette.difficulties.values()) {
            if (d.slot() == slot) {
                if (bet != null || d == difficulty) {
                    reject();
                } else {
                    difficulty = d;
                    sound(Sound.UI_BUTTON_CLICK);
                    render();
                }
                return;
            }
        }
    }

    private void spin() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (!mayStart()) {
            reject();
            return;
        }
        int drawn = roulette.draw(difficulty, plugin.random());
        Bet placed = plugin.bets().place(player, game.id(), currency(), amount(),
                "{\"difficulty\":\"" + difficulty.key() + "\",\"cell\":" + drawn
                        + ",\"multiplier\":" + roulette.multiplier(difficulty, drawn) + "}");
        if (placed == null) {
            reject();
            return;
        }
        bet = placed;
        target = drawn;
        int start = marker < 0 ? 0 : marker;
        int size = roulette.ring.size();
        totalSteps = roulette.laps * size + Math.floorMod(target - start, size);
        step = 0;
        marker = start;
        render();
        sound(Sound.UI_BUTTON_CLICK);
        plugin.dealers().actFor(player.getUniqueId());
        scheduleStep();
    }

    /** Fast at first, slower towards the end (1 → 6 ticks per step). */
    private void scheduleStep() {
        long delay = 1 + (long) (5.0 * step / Math.max(1, totalSteps) * step / Math.max(1, totalSteps));
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int previous = marker;
            marker = (marker + 1) % roulette.ring.size();
            step++;
            renderCell(previous);
            renderCell(marker);
            sound(Sound.BLOCK_NOTE_BLOCK_HAT);
            if (step % 8 == 0) plugin.dealers().actFor(player.getUniqueId());
            if (step >= totalSteps) {
                task = null;
                finish(true);
            } else {
                scheduleStep();
            }
        }, delay);
    }

    private void finish(boolean visible) {
        marker = target;
        double multiplier = roulette.multiplier(difficulty, target);
        long payout = Payouts.payout(bet.amount(), multiplier);
        plugin.bets().settle(bet, payout);
        String key = target == roulette.winIndex ? "games.roulette.won"
                : multiplier > 0 ? "games.roulette.bonus" : "games.roulette.lost";
        plugin.messages().send(player, key, "multiplier", Format.decimal(multiplier),
                "payout", plugin.wallet().format(bet.currency, payout), "bet", plugin.wallet().format(bet.currency, bet.amount()));
        Bet done = bet;
        bet = null;
        if (!visible) return;
        sound(target == roulette.winIndex ? Sound.ENTITY_PLAYER_LEVELUP
                : multiplier > 0 ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.BLOCK_NOTE_BLOCK_BASS);
        render();
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            task = null;
            if (!isClosed() && bet == null && done != null) render();
        }, roulette.resetTicks);
    }

    /** ESC while the round runs: the GUI comes back (watch it to the end); standing up from the chair skips it. */
    @Override
    protected boolean holdOnEsc() {
        return bet != null;
    }

    @Override
    protected void onClose() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bet != null) finish(false);
    }

    @Override
    protected void redraw() {
        render();
    }

    private void render() {
        Messages m = plugin.messages();
        renderPanel();
        for (int i = 0; i < roulette.ring.size(); i++) renderCell(i);
        for (RouletteGame.Bonus b : roulette.bonuses) {
            if (b.labelSlot() < 0) continue;
            set(b.labelSlot(), roulette.labelItem, m.line("games.roulette.bonus-label.name",
                            "multiplier", Format.decimal(b.multiplier())),
                    m.lines("games.roulette.bonus-label.lore", "chance", Format.decimal(b.weight()) + "%"));
        }
        for (RouletteGame.Difficulty d : roulette.difficulties.values()) {
            set(d.slot(), d == difficulty ? d.active() : d.inactive(),
                    m.line("games.roulette.difficulty.name", "name", m.raw("games.roulette.difficulty." + d.key()),
                            "multiplier", Format.decimal(d.multiplier())),
                    m.lines(d == difficulty ? "games.roulette.difficulty.lore-active" : "games.roulette.difficulty.lore",
                            "chance", Format.decimal(d.weight()) + "%", "rtp", Format.percent(roulette.rtp(d))));
        }
        String[] ph = {"multiplier", Format.decimal(difficulty.multiplier()), "chance", Format.decimal(difficulty.weight()) + "%",
                "bet", plugin.wallet().format(currency(), amount())};
        if (bet != null) {
            set(roulette.spotSlot, chip(bet.currency, bet.amount(), m.line("games.roulette.spot-running.name",
                    "bet", plugin.wallet().format(bet.currency, bet.amount())), List.of()));
        } else {
            set(roulette.spotSlot, roulette.spotItem, m.line("games.roulette.spot.name", ph), m.lines("games.roulette.spot.lore", ph));
        }
        set(roulette.leverSlot, roulette.spotItem, m.line(bet != null ? "games.roulette.lever-running.name"
                : "games.roulette.spot.name", ph), bet != null ? List.of() : m.lines("games.roulette.spot.lore", ph));
    }

    private void renderCell(int index) {
        Messages m = plugin.messages();
        int slot = roulette.ring.get(index);
        if (index == marker && bet != null) {
            set(slot, chip(bet.currency, bet.amount(), m.line("games.roulette.marker.name"), List.of()));
            return;
        }
        if (index == marker && marker == target && target >= 0) {
            set(slot, chip(currency(), amount(), m.line("games.roulette.marker-stop.name",
                    "multiplier", Format.decimal(roulette.multiplier(difficulty, index))), List.of()));
            return;
        }
        String name;
        if (index == roulette.winIndex) {
            name = m.raw("games.roulette.cell-win.name", "multiplier", Format.decimal(difficulty.multiplier()));
        } else {
            double mul = roulette.multiplier(difficulty, index);
            name = mul > 0 ? m.raw("games.roulette.cell-bonus.name", "multiplier", Format.decimal(mul))
                    : m.raw("games.roulette.cell-empty.name");
        }
        set(slot, index % 2 == 0 ? roulette.cellA : roulette.cellB, Messages.itemLine(name), List.of());
    }
}
