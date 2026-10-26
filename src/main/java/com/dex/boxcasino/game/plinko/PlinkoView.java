package com.dex.boxcasino.game.plinko;

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

/** One player's Plinko board. The result is decided before the animation and paid even if the GUI is closed. */
final class PlinkoView extends GameView {

    private final PlinkoGame plinko;
    private Bet bet;
    /** Risk level of the ball in play (the chosen one can change only between drops). */
    private PlinkoGame.Risk playing;
    private int hopper = -1;
    private List<Integer> path = List.of();
    private int step;
    private BukkitTask task;

    PlinkoView(BoxCasinoPlugin plugin, Player player, PlinkoGame game, boolean npc) {
        super(plugin, player, game, npc);
        this.plinko = game;
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

    /** The chosen risk level, remembered per player. */
    private PlinkoGame.Risk risk() {
        return plinko.risk((int) Math.round(memory.options.getOrDefault("plinko.risk", 0.0)));
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (handlePanel(slot, click)) return;
        if (slot == plinko.startSlot) {
            if (bet != null) reject();
            else drop();
            return;
        }
        for (PlinkoGame.Risk r : plinko.risks.values()) {
            if (r.slot() != slot) continue;
            if (bet != null || r == risk()) {
                reject();
            } else {
                memory.options.put("plinko.risk", (double) plinko.indexOf(r));
                sound(Sound.UI_BUTTON_CLICK);
                render();
            }
            return;
        }
    }

    private void drop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (!mayStart()) {
            reject();
            return;
        }
        PlinkoGame.Risk risk = risk();
        PlinkoGame.Drop drop = plinko.drop(plugin.random());
        Bet placed = plugin.bets().place(player, game.id(), currency(), amount(),
                "{\"risk\":\"" + risk.key() + "\",\"hopper\":" + (drop.hopper() + 1) + ",\"multiplier\":"
                        + risk.multipliers()[drop.hopper()] + ",\"path\":" + drop.path() + "}");
        if (placed == null) {
            reject();
            return;
        }
        bet = placed;
        playing = risk;
        hopper = drop.hopper();
        path = drop.path();
        step = 0;
        render();
        sound(Sound.UI_BUTTON_CLICK);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, plinko.stepTicks, plinko.stepTicks);
    }

    private void tick() {
        if (step >= path.size()) {
            task.cancel();
            task = null;
            finish(true);
            return;
        }
        if (step > 0) clearBall(path.get(step - 1));
        set(path.get(step), plinko.ballItem, plugin.messages().line("games.plinko.ball.name"), List.of());
        sound(Sound.BLOCK_NOTE_BLOCK_HAT);
        step++;
    }

    private void clearBall(int slot) {
        if (slot == plinko.startSlot) {
            renderSpot();
        } else if (plinko.hopperSlots.contains(slot)) {
            renderHopper(plinko.hopperSlots.indexOf(slot));
        } else {
            inventory.setItem(slot, null);
        }
    }

    private void finish(boolean visible) {
        double multiplier = playing.multipliers()[hopper];
        long payout = Payouts.payout(bet.amount(), multiplier);
        plugin.bets().settle(bet, payout);
        plugin.messages().send(player, payout > bet.amount() ? "games.plinko.won" : payout == bet.amount()
                        ? "games.plinko.even" : "games.plinko.lost",
                "multiplier", Format.decimal(multiplier), "payout", plugin.wallet().format(bet.currency, payout),
                "bet", plugin.wallet().format(bet.currency, bet.amount()));
        bet = null;
        if (!visible) return;
        sound(payout > 0 && multiplier >= 1 ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_BASS);
        int landed = hopper;
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            task = null;
            if (!isClosed() && bet == null) {
                inventory.setItem(plinko.hopperSlots.get(landed), null);
                render();
            }
        }, plinko.resetTicks);
        renderPanel();
        renderSpot();
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
        renderPanel();
        renderSpot();
        renderRisks();
        for (int i = 0; i < plinko.hopperSlots.size(); i++) renderHopper(i);
    }

    private void renderRisks() {
        Messages m = plugin.messages();
        PlinkoGame.Risk chosen = risk();
        for (PlinkoGame.Risk r : plinko.risks.values()) {
            double[] mul = r.multipliers();
            set(r.slot(), r == chosen ? r.active() : r.inactive(),
                    m.line("games.plinko.risk.name", "name", m.raw("games.plinko.risk." + r.key())),
                    m.lines(r == chosen ? "games.plinko.risk.lore-active" : "games.plinko.risk.lore",
                            "edge", Format.decimal(mul[0]), "middle", Format.decimal(mul[mul.length / 2])));
        }
    }

    private void renderSpot() {
        Messages m = plugin.messages();
        if (bet != null) {
            set(plinko.startSlot, chip(bet.currency, bet.amount(), m.line("games.plinko.spot-running.name",
                    "bet", plugin.wallet().format(bet.currency, bet.amount())), List.of()));
        } else {
            set(plinko.startSlot, plinko.spotItem, m.line("games.plinko.spot.name"),
                    m.lines("games.plinko.spot.lore", "bet", plugin.wallet().format(currency(), amount())));
        }
    }

    private void renderHopper(int i) {
        Messages m = plugin.messages();
        PlinkoGame.Risk r = bet != null ? playing : risk();
        set(plinko.hopperSlots.get(i), plinko.hopperItem,
                m.line("games.plinko.hopper.name", "multiplier", Format.decimal(r.multipliers()[i])),
                m.lines("games.plinko.hopper.lore", "chance", Format.percent(plinko.chance(i))));
    }
}
