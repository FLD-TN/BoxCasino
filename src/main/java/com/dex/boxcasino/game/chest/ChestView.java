package com.dex.boxcasino.game.chest;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.game.GameView;
import com.dex.boxcasino.util.Format;
import com.dex.boxcasino.util.Payouts;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.scheduler.BukkitTask;

/** One player's chests. States: IDLE → RUNNING (opening) → ENDED → IDLE. */
final class ChestView extends GameView {

    private enum State { IDLE, RUNNING, ENDED }

    private final ChestGame chest;
    private State state = State.IDLE;
    private ChestGame.Difficulty difficulty;
    private Bet bet;
    private List<ChestGame.Open> drawn = List.of();
    private final List<ChestGame.Open> revealed = new ArrayList<>();
    /** Chest slot that shows "opened" for a moment after a click. */
    private int lastChest = -1;
    private BukkitTask task;

    ChestView(BoxCasinoPlugin plugin, Player player, ChestGame game, boolean npc) {
        super(plugin, player, game, npc);
        this.chest = game;
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
        if (slot == chest.spotSlot) {
            if (state == State.RUNNING) reject();
            else start();
            return;
        }
        for (ChestGame.Difficulty d : chest.difficulties.values()) {
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
        if (chest.chestSlots.contains(slot)) {
            if (state == State.RUNNING) openChest(slot);
            else reject();
        }
    }

    private void start() {
        if (state == State.ENDED) reset();
        if (!mayStart()) {
            reject();
            return;
        }
        List<ChestGame.Open> openings = chest.draw(difficulty, plugin.random());
        String outcome = "{\"difficulty\":\"" + difficulty.key() + "\",\"opens\":" + openings.stream()
                .map(o -> "\"" + o.kind().name().toLowerCase(java.util.Locale.ROOT) + ":" + o.value() + "\"")
                .collect(Collectors.joining(",", "[", "]")) + "}";
        Bet placed = plugin.bets().place(player, game.id(), currency(), amount(), outcome);
        if (placed == null) {
            reject();
            return;
        }
        bet = placed;
        drawn = openings;
        revealed.clear();
        lastChest = -1;
        state = State.RUNNING;
        sound(Sound.BLOCK_CHEST_OPEN);
        render();
    }

    private void openChest(int slot) {
        ChestGame.Open o = drawn.get(revealed.size());
        revealed.add(o);
        lastChest = slot;
        switch (o.kind()) {
            case GOLD -> {
                sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
                plugin.messages().send(player, "games.chest.found-gold", "value", Format.decimal(o.value()));
            }
            case MULTIPLIER -> {
                sound(Sound.BLOCK_NOTE_BLOCK_PLING);
                plugin.messages().send(player, "games.chest.found-multiplier", "value", Format.decimal(o.value()));
            }
            case TRAP -> sound(Sound.ENTITY_GENERIC_EXPLODE);
        }
        if (o.kind() == ChestGame.Kind.TRAP || revealed.size() >= chest.opens) {
            finish(true);
            return;
        }
        render();
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            task = null;
            lastChest = -1;
            if (!isClosed() && state == State.RUNNING) renderChests();
        }, 10L);
    }

    private void finish(boolean visible) {
        while (revealed.size() < drawn.size()) revealed.add(drawn.get(revealed.size()));
        double multiplier = ChestGame.total(revealed);
        long payout = Payouts.payout(bet.amount(), multiplier);
        plugin.bets().settle(bet, payout);
        state = State.ENDED;
        String stake = plugin.wallet().format(bet.currency, bet.amount());
        boolean trapped = revealed.get(revealed.size() - 1).kind() == ChestGame.Kind.TRAP;
        if (trapped) {
            plugin.messages().send(player, "games.chest.trapped", "bet", stake);
        } else {
            plugin.messages().send(player, payout > bet.amount() ? "games.chest.won" : payout == 0 ? "games.chest.empty"
                            : "games.chest.partial", "multiplier", Format.decimal(multiplier),
                    "payout", plugin.wallet().format(bet.currency, payout), "bet", stake);
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (!visible) return;
        sound(payout > bet.amount() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_BASS);
        render();
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            task = null;
            if (state == State.ENDED && !isClosed()) reset();
        }, chest.resetTicks);
    }

    private void reset() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        state = State.IDLE;
        bet = null;
        drawn = List.of();
        revealed.clear();
        lastChest = -1;
        render();
    }

    /** ESC: the remaining openings are made for the player and the round is paid. */
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
        renderChests();
        renderResults();
        renderDifficulties();
        renderSpot();
        renderLever();
    }

    private void renderChests() {
        Messages m = plugin.messages();
        int left = chest.opens - revealed.size();
        for (int slot : chest.chestSlots) {
            boolean open = slot == lastChest || state == State.ENDED;
            set(slot, open ? chest.chestOpened : chest.chestClosed,
                    m.line(state == State.RUNNING ? "games.chest.chest.name" : "games.chest.chest-idle.name"),
                    state == State.RUNNING ? m.lines("games.chest.chest.lore", "left", String.valueOf(left)) : List.of());
        }
    }

    private void renderResults() {
        Messages m = plugin.messages();
        for (int i = 0; i < chest.opens; i++) {
            int slot = chest.resultSlots.get(i);
            if (i >= revealed.size()) {
                set(slot, chest.emptyResult, m.line("games.chest.result-empty.name", "number", String.valueOf(i + 1)), List.of());
                continue;
            }
            ChestGame.Open o = revealed.get(i);
            String v = Format.decimal(o.value());
            switch (o.kind()) {
                case GOLD -> set(slot, chest.goldItem, m.line("games.chest.result-gold.name", "value", v), List.of());
                case MULTIPLIER -> set(slot, chest.multiplierItem, m.line("games.chest.result-multiplier.name", "value", v), List.of());
                case TRAP -> set(slot, chest.trapItem, m.line("games.chest.result-trap.name"), List.of());
            }
        }
    }

    private void renderDifficulties() {
        Messages m = plugin.messages();
        for (ChestGame.Difficulty d : chest.difficulties.values()) {
            String[] ph = {"name", m.raw("games.chest.difficulty." + d.key()),
                    "trap", Format.percent(d.trap() / d.total()), "rtp", Format.percent(chest.rtp(d)),
                    "gold", Format.decimal(d.goldMin()) + " - " + Format.decimal(d.goldMax()),
                    "multiplier", Format.decimal(d.multiplierMin()) + " - " + Format.decimal(d.multiplierMax())};
            set(d.slot(), d == difficulty ? d.active() : d.inactive(), m.line("games.chest.difficulty.name", ph),
                    m.lines(d == difficulty ? "games.chest.difficulty.lore-active" : "games.chest.difficulty.lore", ph));
        }
    }

    private void renderSpot() {
        Messages m = plugin.messages();
        if (state == State.RUNNING) {
            String b = plugin.wallet().format(bet.currency, bet.amount());
            set(chest.spotSlot, chip(bet.currency, bet.amount(), m.line("games.chest.spot-running.name", "bet", b),
                    m.lines("games.chest.spot-running.lore")));
        } else {
            set(chest.spotSlot, chest.spotItem, m.line("games.chest.spot.name"), m.lines("games.chest.spot.lore",
                    "bet", plugin.wallet().format(currency(), amount()), "name", m.raw("games.chest.difficulty." + difficulty.key()),
                    "opens", String.valueOf(chest.opens)));
        }
    }

    private void renderLever() {
        Messages m = plugin.messages();
        for (int slot : chest.leverSlots) {
            switch (state) {
                case IDLE -> set(slot, chest.leverIdle, m.line("games.chest.lever-idle.name"), List.of());
                case RUNNING -> {
                    double total = ChestGame.total(revealed);
                    set(slot, chest.leverRunning, m.line("games.chest.lever-running.name"),
                            m.lines("games.chest.lever-running.lore", "gold", Format.decimal(ChestGame.gold(revealed)),
                                    "multiplier", Format.decimal(ChestGame.multiplier(revealed)),
                                    "payout", plugin.wallet().format(bet.currency, Payouts.payout(bet.amount(), total)),
                                    "left", String.valueOf(chest.opens - revealed.size())));
                }
                case ENDED -> set(slot, chest.leverEnd, m.line("games.chest.lever-end.name",
                        "multiplier", Format.decimal(ChestGame.total(revealed))), List.of());
            }
        }
    }
}
