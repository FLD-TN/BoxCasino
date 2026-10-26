package com.dex.boxcasino.pvp.tictactoe;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.extra.Countdown;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.pvp.Match;
import com.dex.boxcasino.pvp.MatchView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Rounds of 3x3 with vanishing marks (see {@link TicTacToeGame}). X moves first and goes to the other player every
 * round; a timeout plays a random empty cell. The first to win {@code games-to-win} rounds takes the pot.
 */
final class TicTacToeMatch extends Match {

    private final TicTacToeGame ttt;
    /** -1 empty, else the seat that marked it. */
    private final int[] marks = new int[9];
    /** Marks of each seat on the board, oldest first. */
    @SuppressWarnings("unchecked")
    private final Deque<Integer>[] placed = new Deque[] {new ArrayDeque<Integer>(), new ArrayDeque<Integer>()};
    private final int[] scores = new int[2];
    private final List<String> history = new ArrayList<>();
    private final List<Integer> moves = new ArrayList<>();
    private int xSeat;
    private int round = 1;
    private int turn;
    private int secondsLeft;
    private int[] winLine;
    /** Showing the end of a round (-1 = draw, 0/1 = winner seat) before the next one; null while playing. */
    private Integer roundResult;
    /** The oldest mark of the player to move blinks before it goes. */
    private boolean blink;

    TicTacToeMatch(BoxCasinoPlugin plugin, TicTacToeGame game, Player creator, Bet creatorBet, Player acceptor, Bet acceptorBet) {
        super(plugin, game, creator, creatorBet, acceptor, acceptorBet);
        this.ttt = game;
        Arrays.fill(marks, -1);
        this.xSeat = plugin.random().nextInt(2);
        this.turn = xSeat;
    }

    @Override
    protected void start() {
        secondsLeft = game.turnSeconds();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        tell("games.tictactoe.first", "player", names[xSeat], "round", String.valueOf(round));
    }

    private void tick() {
        secondsLeft--;
        blink = !blink;
        if (roundResult != null) {
            if (secondsLeft <= 0) nextRound();
            return;
        }
        if (secondsLeft > 0) {
            Countdown.turn(plugin, List.of(ids[turn]), secondsLeft, "games.tictactoe.turn-countdown");
            renderAll();
            return;
        }
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < 9; i++) if (marks[i] < 0) empty.add(i);
        play(empty.get(plugin.random().nextInt(empty.size())));
    }

    @Override
    protected void click(int seat, int slot, ClickType type) {
        if (roundResult != null || seat != turn) return;
        int index = ttt.board.indexOf(slot);
        if (index < 0 || marks[index] >= 0) return;
        play(index);
    }

    /** The cell of {@code seat}'s mark that goes with their next move, or -1. */
    private int vanishing(int seat) {
        return ttt.maxMarks > 0 && placed[seat].size() >= ttt.maxMarks ? placed[seat].peekFirst() : -1;
    }

    private void play(int index) {
        int gone = vanishing(turn);
        if (gone >= 0) {
            placed[turn].pollFirst();
            marks[gone] = -1;
        }
        marks[index] = turn;
        placed[turn].addLast(index);
        moves.add(index);
        sound(Sound.BLOCK_WOODEN_BUTTON_CLICK_ON);
        winLine = TicTacToeGame.winLine(marks, turn);
        boolean drawn = ttt.maxMarks > 0 ? moves.size() >= ttt.roundMoves : moves.size() == 9;
        if (winLine != null || drawn) {
            endRound(winLine != null ? turn : -1);
            return;
        }
        turn = 1 - turn;
        secondsLeft = game.turnSeconds();
        renderAll();
    }

    private void endRound(int winner) {
        history.add("{\"x\":" + xSeat + ",\"moves\":" + moves + "}");
        if (winner >= 0) {
            scores[winner]++;
            tell("games.tictactoe.round-won", "round", String.valueOf(round), "player", names[winner],
                    "score0", String.valueOf(scores[0]), "score1", String.valueOf(scores[1]),
                    "player0", names[0], "player1", names[1]);
        } else {
            tell("games.tictactoe.round-draw", "round", String.valueOf(round));
        }
        sound(winner >= 0 ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS);
        int matchWinner = scores[0] >= ttt.gamesToWin ? 0 : scores[1] >= ttt.gamesToWin ? 1 : -2;
        if (matchWinner == -2 && round >= ttt.maxRounds) {
            matchWinner = scores[0] == scores[1] ? -1 : scores[0] > scores[1] ? 0 : 1;
        }
        if (matchWinner != -2) {
            String outcome = "{\"rounds\":[" + String.join(",", history) + "],\"score\":[" + scores[0] + "," + scores[1] + "]}";
            plugin.bets().setOutcome(bets[0], outcome);
            plugin.bets().setOutcome(bets[1], outcome);
            finish(matchWinner);
            return;
        }
        roundResult = winner;
        secondsLeft = ttt.pauseSeconds;
        renderAll();
    }

    /** Clean board, the other player takes X and moves first. */
    private void nextRound() {
        round++;
        Arrays.fill(marks, -1);
        placed[0].clear();
        placed[1].clear();
        moves.clear();
        winLine = null;
        roundResult = null;
        xSeat = 1 - xSeat;
        turn = xSeat;
        secondsLeft = game.turnSeconds();
        tell("games.tictactoe.first", "player", names[xSeat], "round", String.valueOf(round));
        renderAll();
    }

    @Override
    protected void render(MatchView view) {
        Messages m = plugin.messages();
        renderSeats(view, null, null);
        boolean playing = !finished() && roundResult == null;
        for (int seat = 0; seat < 2; seat++) {
            ItemStack head = view.getInventory().getItem(game.headSlot(seat));
            if (head == null) continue;
            ItemMeta meta = head.getItemMeta();
            String mark = m.raw(seat == xSeat ? "games.tictactoe.mark-x" : "games.tictactoe.mark-o");
            String[] ph = {"mark", mark, "score", String.valueOf(scores[seat]), "need", String.valueOf(ttt.gamesToWin),
                    "seconds", String.valueOf(Math.max(0, secondsLeft))};
            meta.lore(!playing || seat != turn ? m.lines("games.tictactoe.head.lore", ph) : m.lines("games.tictactoe.head.lore-turn", ph));
            head.setItemMeta(meta);
        }
        int gone = playing ? vanishing(turn) : -1;
        for (int i = 0; i < 9; i++) {
            int slot = ttt.board.get(i);
            boolean hidden = i == gone && blink;
            if (marks[i] < 0 || hidden) {
                boolean mine = playing && view.seat() == turn;
                String key = hidden ? "games.tictactoe.cell-vanishing.name"
                        : mine ? "games.tictactoe.cell-free.name" : "games.tictactoe.cell-wait.name";
                set(view, slot, ttt.emptyCell, m.line(key), List.of());
                continue;
            }
            boolean x = marks[i] == xSeat;
            boolean onLine = winLine != null && (winLine[0] == i || winLine[1] == i || winLine[2] == i);
            set(view, slot, onLine ? (x ? ttt.finalCross : ttt.finalCircle) : (x ? ttt.cross : ttt.circle),
                    m.line(i == gone ? "games.tictactoe.cell-vanishing.name" : "games.tictactoe.cell-marked.name",
                            "player", names[marks[i]], "mark", m.raw(x ? "games.tictactoe.mark-x" : "games.tictactoe.mark-o")),
                    List.of());
        }
    }
}
