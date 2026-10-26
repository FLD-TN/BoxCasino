package com.dex.boxcasino.pvp.tictactoe;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.pvp.Match;
import com.dex.boxcasino.pvp.PvpGame;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Cờ Caro 3x3 1v1 (spec 7.7). Playable anywhere, challenges in a lobby like Xúc Xắc / Oẳn Tù Tì. Vanishing marks:
 * each player keeps at most {@code max-marks} marks, the oldest one goes when a new one is placed, so a round never
 * ends in a full board. Best of {@code games-to-win × 2 − 1} rounds, the first move changes hands every round.
 */
public final class TicTacToeGame extends PvpGame {

    static final int[][] LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}
    };

    List<Integer> board = List.of();
    ItemSpec cross;
    ItemSpec circle;
    ItemSpec finalCross;
    ItemSpec finalCircle;
    ItemSpec emptyCell;
    /** Marks a player keeps on the board; 0 = classic (a full board is a drawn round). */
    int maxMarks;
    int gamesToWin;
    /** Rounds played at most; then the higher score wins (equal = draw, stakes back). */
    int maxRounds;
    /** Moves after which a vanishing round is called a draw. */
    int roundMoves;
    int pauseSeconds;

    public TicTacToeGame(BoxCasinoPlugin plugin) {
        super(plugin, "tictactoe");
    }

    @Override
    protected String loadPvp(YamlConfiguration yml) {
        board = settings.slots("board");
        if (board.size() != 9) return "slots.board needs 9 slots";
        cross = settings.item("cross");
        circle = settings.item("circle");
        finalCross = settings.item("final-cross");
        finalCircle = settings.item("final-circle");
        emptyCell = settings.item("empty");
        maxMarks = Math.max(0, yml.getInt("max-marks", 3));
        if (maxMarks > 0 && maxMarks < 3) return "max-marks must be 0 (classic) or at least 3";
        gamesToWin = Math.max(1, yml.getInt("games-to-win", 2));
        maxRounds = Math.max(gamesToWin * 2 - 1, yml.getInt("max-rounds", 5));
        roundMoves = Math.max(9, yml.getInt("round-moves", 30));
        pauseSeconds = Math.max(1, yml.getInt("round-pause-seconds", 3));
        return null;
    }

    @Override
    public boolean casinoOnly() {
        return false;
    }

    @Override
    protected boolean lobby() {
        return true;
    }

    /** The winning line (3 board indexes) of {@code seat}, or null. */
    static int[] winLine(int[] marks, int seat) {
        for (int[] line : LINES) {
            if (marks[line[0]] == seat && marks[line[1]] == seat && marks[line[2]] == seat) return line;
        }
        return null;
    }

    @Override
    protected Match newMatch(Player creator, Bet creatorBet, Player acceptor, Bet acceptorBet) {
        return new TicTacToeMatch(plugin, this, creator, creatorBet, acceptor, acceptorBet);
    }
}
