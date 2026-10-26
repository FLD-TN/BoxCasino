package com.dex.boxcasino.pvp.rps;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.pvp.Match;
import com.dex.boxcasino.pvp.MatchView;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Rounds of picking (hidden until both picked or time is up), then a short reveal. */
final class RpsMatch extends Match {

    private final RpsGame rps;
    private final int[] picks = {-1, -1};
    private final int[] scores = new int[2];
    private final List<String> history = new ArrayList<>();
    private boolean showing;
    private int secondsLeft;
    /** Winner of the round being shown: 0/1, -1 = same figure. */
    private int roundWinner;

    RpsMatch(BoxCasinoPlugin plugin, RpsGame game, Player creator, Bet creatorBet, Player acceptor, Bet acceptorBet) {
        super(plugin, game, creator, creatorBet, acceptor, acceptorBet);
        this.rps = game;
    }

    @Override
    protected void start() {
        secondsLeft = game.turnSeconds();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        secondsLeft--;
        if (!showing) {
            if (secondsLeft <= 0) reveal();
            else renderAll();
            return;
        }
        if (secondsLeft <= 0) {
            showing = false;
            picks[0] = -1;
            picks[1] = -1;
            secondsLeft = game.turnSeconds();
            renderAll();
        }
    }

    @Override
    protected void click(int seat, int slot, ClickType type) {
        if (showing || picks[seat] >= 0) return;
        int figure = rps.figureAt(slot);
        if (figure < 0) return;
        picks[seat] = figure;
        Player p = Bukkit.getPlayer(ids[seat]);
        if (p != null && plugin.gameSounds()) p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
        if (picks[1 - seat] >= 0) reveal();
        else renderAll();
    }

    private void reveal() {
        for (int s = 0; s < 2; s++) if (picks[s] < 0) picks[s] = plugin.random().nextInt(3);
        int c = RpsGame.compare(picks[0], picks[1]);
        roundWinner = c > 0 ? 0 : c < 0 ? 1 : -1;
        if (roundWinner >= 0) scores[roundWinner]++;
        history.add("[" + picks[0] + "," + picks[1] + "]");
        showing = true;
        secondsLeft = rps.showSeconds;
        sound(roundWinner < 0 ? Sound.BLOCK_NOTE_BLOCK_BASS : Sound.BLOCK_NOTE_BLOCK_PLING);
        for (int s = 0; s < 2; s++) {
            if (scores[s] >= rps.gamesToWin) {
                String outcome = "{\"rounds\":[" + String.join(",", history) + "],\"winner\":" + s + "}";
                plugin.bets().setOutcome(bets[0], outcome);
                plugin.bets().setOutcome(bets[1], outcome);
                finish(s);
                return;
            }
        }
        renderAll();
    }

    @Override
    protected void render(MatchView view) {
        Messages m = plugin.messages();
        renderSeats(view, null, null);
        int me = view.seat();
        for (int s = 0; s < 2; s++) {
            int slot = rps.figureSlots[s];
            if (picks[s] < 0) {
                clear(view, slot);
            } else if (showing || finished() || s == me) {
                set(view, slot, rps.figureItems.get(picks[s]), m.line("games.rps.figure." + RpsGame.FIGURES[picks[s]]), List.of());
            } else {
                set(view, slot, rps.hiddenItem, m.line("games.rps.hidden.name", "player", names[s]), List.of());
            }
            int score = Math.min(scores[s], rps.numbers.size() - 1);
            set(view, rps.scoreSlots[s], rps.numbers.get(score), m.line("games.rps.score.name", "player", names[s],
                    "score", String.valueOf(scores[s]), "need", String.valueOf(rps.gamesToWin)), List.of());
        }
        if (showing || finished()) {
            set(view, rps.compareSlot, roundWinner < 0 ? rps.compareDraw : roundWinner == 0 ? rps.compareLeft : rps.compareRight,
                    m.line(roundWinner < 0 ? "games.rps.compare-draw.name" : "games.rps.compare.name",
                            "player", roundWinner < 0 ? "" : names[roundWinner]), List.of());
        } else {
            clear(view, rps.compareSlot);
        }
        String lever = finished() ? "games.rps.lever-end.name" : showing ? "games.rps.lever-show.name"
                : picks[me] >= 0 ? "games.rps.lever-wait.name" : "games.rps.lever-pick.name";
        set(view, rps.leverSlot, rps.leverItem, m.line(lever, "seconds", String.valueOf(Math.max(0, secondsLeft))), List.of());
        for (int f = 0; f < RpsGame.FIGURES.length; f++) {
            for (int slot : rps.buttons.get(f)) {
                set(view, slot, rps.figureItems.get(f), m.line("games.rps.button.name",
                        "figure", m.raw("games.rps.figure." + RpsGame.FIGURES[f])), m.lines("games.rps.button.lore"));
            }
        }
    }
}
