package com.dex.boxcasino.pvp.tictactoe;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.pvp.Match;
import com.dex.boxcasino.pvp.PvpGame;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Cờ Caro 3x3 1v1 (spec 7.7). Casino only. */
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
        return null;
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
