package com.dex.boxcasino.pvp.dice;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.pvp.Match;
import com.dex.boxcasino.pvp.MatchView;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * Every roll (re-rolls included) is drawn when the match starts and stored in the ledger; the animation then shows
 * one die per step: left 1, right 1, left 2, right 2, sums.
 */
final class DiceMatch extends Match {

    private static final int COUNTDOWN = 3;

    private final DiceGame dice;
    /** Each attempt: {left1, left2, right1, right2}. The last one has different sums unless all tied. */
    private final List<int[]> rolls = new ArrayList<>();
    private int countdown = COUNTDOWN;
    private int attempt;
    private int reveal;
    private boolean paused;

    DiceMatch(BoxCasinoPlugin plugin, DiceGame game, Player creator, Bet creatorBet, Player acceptor, Bet acceptorBet) {
        super(plugin, game, creator, creatorBet, acceptor, acceptorBet);
        this.dice = game;
        for (int i = 0; i < game.maxRolls; i++) {
            int[] r = new int[4];
            for (int d = 0; d < 4; d++) r[d] = 1 + plugin.random().nextInt(6);
            rolls.add(r);
            if (sum(r, 0) != sum(r, 1)) break;
        }
    }

    private static int sum(int[] r, int seat) {
        return seat == 0 ? r[0] + r[1] : r[2] + r[3];
    }

    @Override
    protected boolean decided() {
        return true;
    }

    @Override
    protected int decidedResult() {
        int[] last = rolls.get(rolls.size() - 1);
        int a = sum(last, 0);
        int b = sum(last, 1);
        return a == b ? -1 : a > b ? 0 : 1;
    }

    @Override
    protected void start() {
        String outcome = rolls.stream().map(r -> "[" + r[0] + "," + r[1] + "," + r[2] + "," + r[3] + "]")
                .collect(Collectors.joining(",", "{\"rolls\":[", "]}"));
        plugin.bets().setOutcome(bets[0], outcome);
        plugin.bets().setOutcome(bets[1], outcome);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, dice.stepTicks, dice.stepTicks);
    }

    private void tick() {
        if (countdown > 0) {
            countdown--;
            sound(Sound.BLOCK_NOTE_BLOCK_HAT);
            renderAll();
            return;
        }
        int[] r = rolls.get(attempt);
        if (reveal < 5) {
            reveal++;
            sound(reveal == 5 ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_WOODEN_BUTTON_CLICK_ON);
            renderAll();
            if (reveal == 5) {
                boolean tie = sum(r, 0) == sum(r, 1);
                if (!tie || attempt == rolls.size() - 1) finish(decidedResult());
                else tell("games.dice.tie", "sum", String.valueOf(sum(r, 0)));
            }
            return;
        }
        if (!paused) {
            paused = true;
            return;
        }
        paused = false;
        attempt++;
        reveal = 0;
        renderAll();
    }

    @Override
    protected void click(int seat, int slot, ClickType type) {
        // nothing to choose: the dice roll by themselves
    }

    @Override
    protected void render(MatchView view) {
        Messages m = plugin.messages();
        renderSeats(view, null, null);
        int[] r = rolls.get(Math.min(attempt, rolls.size() - 1));
        // reveal order: left 1, right 1, left 2, right 2
        int[][] order = {{0, 0, 0}, {1, 0, 2}, {0, 1, 1}, {1, 1, 3}};
        for (int i = 0; i < 4; i++) {
            int seat = order[i][0];
            int slot = dice.diceSlots[seat][order[i][1]];
            if (countdown == 0 && reveal > i) {
                int value = r[order[i][2]];
                set(view, slot, dice.faces.get(value - 1), m.line("games.dice.face.name", "value", String.valueOf(value)), List.of());
            } else {
                clear(view, slot);
            }
        }
        boolean shown = countdown == 0 && reveal >= 5;
        for (int seat = 0; seat < 2; seat++) {
            if (shown) {
                int s = sum(r, seat);
                set(view, dice.scoreSlots[seat], dice.sums.get(s - 2), m.line("games.dice.sum.name", "player", names[seat],
                        "sum", String.valueOf(s)), List.of());
            } else {
                clear(view, dice.scoreSlots[seat]);
            }
        }
        if (shown) {
            int a = sum(r, 0);
            int b = sum(r, 1);
            set(view, dice.compareSlot, a == b ? dice.compareDraw : a > b ? dice.compareLeft : dice.compareRight,
                    m.line(a == b ? "games.dice.compare-draw.name" : "games.dice.compare.name",
                            "player", a > b ? names[0] : names[1]), List.of());
        } else {
            clear(view, dice.compareSlot);
        }
        if (finished()) {
            set(view, dice.leverSlot, dice.leverEnd, m.line("games.dice.lever-end.name"), List.of());
        } else if (countdown > 0) {
            set(view, dice.leverSlot, dice.leverWait, m.line("games.dice.lever-wait.name", "seconds", String.valueOf(countdown)), List.of());
        } else {
            set(view, dice.leverSlot, dice.leverRolling, m.line("games.dice.lever-rolling.name",
                    "attempt", String.valueOf(attempt + 1)), List.of());
        }
    }
}
