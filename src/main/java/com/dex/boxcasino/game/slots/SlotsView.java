package com.dex.boxcasino.game.slots;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.game.GameView;
import com.dex.boxcasino.util.Format;
import com.dex.boxcasino.util.Payouts;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.scheduler.BukkitTask;

/** One player's slot machine. The spin is drawn on click; the columns then roll and stop from left to right. */
final class SlotsView extends GameView {

    private final SlotsGame slots;
    private Bet bet;
    private SlotsGame.Spin spin;
    /** Last result shown on the screen until the next spin (null = fresh screen). */
    private SlotsGame.Spin shown;
    private int frame;
    private BukkitTask task;

    SlotsView(BoxCasinoPlugin plugin, Player player, SlotsGame game, boolean npc) {
        super(plugin, player, game, npc);
        this.slots = game;
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
        if (slot == slots.spotSlot || slots.leverSlots.contains(slot)) {
            if (bet != null) reject();
            else start();
        }
    }

    private void start() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (!mayStart()) {
            reject();
            return;
        }
        SlotsGame.Spin drawn = slots.spin(plugin.random());
        String outcome = "{\"grid\":" + Arrays.stream(drawn.grid()).mapToObj(String::valueOf).collect(Collectors.joining(",", "[", "]"))
                + ",\"pattern\":\"" + (drawn.pattern() == null ? "none" : drawn.pattern().key())
                + "\",\"multiplier\":" + drawn.multiplier() + "}";
        Bet placed = plugin.bets().place(player, game.id(), currency(), amount(), outcome);
        if (placed == null) {
            reject();
            return;
        }
        bet = placed;
        spin = drawn;
        shown = null;
        frame = 0;
        sound(Sound.UI_BUTTON_CLICK);
        render();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, slots.frameTicks, slots.frameTicks);
    }

    private void tick() {
        frame++;
        boolean done = true;
        for (int c = 0; c < SlotsGame.SIZE; c++) {
            boolean stopped = frame >= slots.stopFrames[c];
            if (!stopped) done = false;
            for (int r = 0; r < SlotsGame.SIZE; r++) {
                int cell = r * SlotsGame.SIZE + c;
                int symbol = stopped ? spin.grid()[cell] : plugin.random().nextInt(slots.symbols.size());
                renderSymbol(cell, symbol, false);
            }
            if (frame == slots.stopFrames[c]) sound(Sound.BLOCK_NOTE_BLOCK_BASEDRUM);
        }
        if (!done) {
            if (frame % 2 == 0) sound(Sound.BLOCK_NOTE_BLOCK_HAT);
            return;
        }
        task.cancel();
        task = null;
        finish(true);
    }

    private void finish(boolean visible) {
        SlotsGame.Spin s = spin;
        long payout = Payouts.payout(bet.amount(), s.multiplier());
        plugin.bets().settle(bet, payout);
        String stake = plugin.wallet().format(bet.currency, bet.amount());
        if (s.pattern() == null) {
            plugin.messages().send(player, "games.slots.lost", "bet", stake);
        } else {
            plugin.messages().send(player, payout > bet.amount() ? "games.slots.won" : payout == bet.amount()
                            ? "games.slots.even" : "games.slots.partial",
                    "pattern", plugin.messages().raw("games.slots.pattern." + s.pattern().key()),
                    "multiplier", Format.decimal(s.multiplier()), "payout", plugin.wallet().format(bet.currency, payout), "bet", stake);
        }
        bet = null;
        spin = null;
        shown = s;
        if (!visible) return;
        sound(payout > 0 && s.multiplier() >= 1 ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_BASS);
        render();
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            task = null;
            if (!isClosed() && bet == null) {
                shown = null;
                render();
            }
        }, slots.resetTicks);
    }

    /** ESC while the columns roll: the GUI comes back (watch it to the end); standing up from the chair skips it. */
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
        SlotsGame.Spin s = shown;
        for (int cell = 0; cell < SlotsGame.SIZE * SlotsGame.SIZE; cell++) {
            if (bet != null) {
                // rolling: cells stay as they are, the next frame draws them
                if (inventory.getItem(slots.grid.get(cell)) == null) renderSymbol(cell, plugin.random().nextInt(slots.symbols.size()), false);
            } else if (s != null) {
                boolean part = s.mask() != null && s.mask()[cell];
                if (s.pattern() != null && !part) renderDim(cell);
                else renderSymbol(cell, s.grid()[cell], part);
            } else {
                renderSymbol(cell, cell % slots.symbols.size(), false);
            }
        }
        renderLever();
    }

    private void renderSymbol(int cell, int symbol, boolean winning) {
        Messages m = plugin.messages();
        set(slots.grid.get(cell), slots.symbols.get(symbol).look(),
                m.line(winning ? "games.slots.symbol-win.name" : "games.slots.symbol.name"), List.of());
    }

    private void renderDim(int cell) {
        set(slots.grid.get(cell), slots.dimItem, plugin.messages().line("games.slots.symbol.name"), List.of());
    }

    private void renderLever() {
        Messages m = plugin.messages();
        String stake = plugin.wallet().format(currency(), amount());
        for (int slot : leverAndSpot()) {
            if (bet != null) {
                set(slot, slots.leverRunning, m.line("games.slots.lever-running.name"), List.of());
            } else if (shown != null) {
                set(slot, slots.leverEnd, m.line(shown.pattern() == null ? "games.slots.lever-lost.name" : "games.slots.lever-won.name",
                                "multiplier", Format.decimal(shown.multiplier())),
                        m.lines("games.slots.lever-idle.lore", "bet", stake));
            } else {
                set(slot, slot == slots.spotSlot ? slots.spotItem : slots.leverIdle, m.line("games.slots.lever-idle.name"),
                        m.lines("games.slots.lever-idle.lore", "bet", stake));
            }
        }
    }

    private List<Integer> leverAndSpot() {
        List<Integer> out = new java.util.ArrayList<>(slots.leverSlots);
        if (slots.spotSlot >= 0 && !out.contains(slots.spotSlot)) out.add(slots.spotSlot);
        return out;
    }
}
