package com.dex.boxcasino.pvp.tictactoe;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.pvp.Match;
import com.dex.boxcasino.pvp.MatchView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** X (random seat) moves first; a timeout plays a random empty cell. A full board without a line is a draw. */
final class TicTacToeMatch extends Match {

    private final TicTacToeGame ttt;
    /** -1 empty, else the seat that marked it. */
    private final int[] marks = new int[9];
    private final int xSeat;
    private final List<Integer> moves = new ArrayList<>();
    private int turn;
    private int secondsLeft;
    private int[] winLine;

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
        tell("games.tictactoe.first", "player", names[xSeat]);
    }

    private void tick() {
        secondsLeft--;
        if (secondsLeft > 0) {
            renderAll();
            return;
        }
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < 9; i++) if (marks[i] < 0) empty.add(i);
        play(empty.get(plugin.random().nextInt(empty.size())));
    }

    @Override
    protected void click(int seat, int slot, ClickType type) {
        if (seat != turn) return;
        int index = ttt.board.indexOf(slot);
        if (index < 0 || marks[index] >= 0) return;
        play(index);
    }

    private void play(int index) {
        marks[index] = turn;
        moves.add(index);
        sound(Sound.BLOCK_WOODEN_BUTTON_CLICK_ON);
        winLine = TicTacToeGame.winLine(marks, turn);
        boolean full = moves.size() == 9;
        if (winLine != null || full) {
            String outcome = "{\"x\":" + xSeat + ",\"moves\":" + moves + "}";
            plugin.bets().setOutcome(bets[0], outcome);
            plugin.bets().setOutcome(bets[1], outcome);
            finish(winLine != null ? turn : -1);
            return;
        }
        turn = 1 - turn;
        secondsLeft = game.turnSeconds();
        renderAll();
    }

    @Override
    protected void render(MatchView view) {
        Messages m = plugin.messages();
        renderSeats(view, null, null);
        for (int seat = 0; seat < 2; seat++) {
            ItemStack head = view.getInventory().getItem(game.headSlot(seat));
            if (head == null) continue;
            ItemMeta meta = head.getItemMeta();
            String mark = m.raw(seat == xSeat ? "games.tictactoe.mark-x" : "games.tictactoe.mark-o");
            meta.lore(finished() || seat != turn
                    ? m.lines("games.tictactoe.head.lore", "mark", mark)
                    : m.lines("games.tictactoe.head.lore-turn", "mark", mark, "seconds", String.valueOf(Math.max(0, secondsLeft))));
            head.setItemMeta(meta);
        }
        for (int i = 0; i < 9; i++) {
            int slot = ttt.board.get(i);
            if (marks[i] < 0) {
                boolean mine = !finished() && view.seat() == turn;
                set(view, slot, ttt.emptyCell, m.line(mine ? "games.tictactoe.cell-free.name" : "games.tictactoe.cell-wait.name"),
                        List.of());
                continue;
            }
            boolean x = marks[i] == xSeat;
            boolean onLine = winLine != null && (winLine[0] == i || winLine[1] == i || winLine[2] == i);
            set(view, slot, onLine ? (x ? ttt.finalCross : ttt.finalCircle) : (x ? ttt.cross : ttt.circle),
                    m.line("games.tictactoe.cell-marked.name", "player", names[marks[i]],
                            "mark", m.raw(x ? "games.tictactoe.mark-x" : "games.tictactoe.mark-o")), List.of());
        }
    }
}
