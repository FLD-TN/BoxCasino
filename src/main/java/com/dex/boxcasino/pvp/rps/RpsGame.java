package com.dex.boxcasino.pvp.rps;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.pvp.Match;
import com.dex.boxcasino.pvp.PvpGame;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Oẳn Tù Tì 1v1 (spec 7.7): first to {@code games-to-win} rounds. Playable anywhere. */
public final class RpsGame extends PvpGame {

    /** 0 = búa (rock), 1 = bao (paper), 2 = kéo (scissors). */
    static final String[] FIGURES = {"rock", "paper", "scissors"};

    int gamesToWin;
    int showSeconds;
    int[] figureSlots = new int[2];
    int[] scoreSlots = new int[2];
    int compareSlot;
    int leverSlot;
    /** Button slots of each figure. */
    final List<List<Integer>> buttons = new ArrayList<>();
    final List<ItemSpec> figureItems = new ArrayList<>();
    List<ItemSpec> numbers = List.of();
    ItemSpec hiddenItem;
    ItemSpec compareLeft;
    ItemSpec compareRight;
    ItemSpec compareDraw;
    ItemSpec leverItem;

    public RpsGame(BoxCasinoPlugin plugin) {
        super(plugin, "rps");
    }

    @Override
    public boolean casinoOnly() {
        return false;
    }

    @Override
    protected boolean lobby() {
        return true;
    }

    @Override
    protected String loadPvp(YamlConfiguration yml) {
        gamesToWin = Math.max(1, yml.getInt("games-to-win", 2));
        showSeconds = Math.max(1, yml.getInt("show-seconds", 2));
        List<Integer> fig = settings.slots("figure");
        List<Integer> score = settings.slots("score");
        if (fig.size() != 2 || score.size() != 2) return "slots.figure and slots.score need 2 slots";
        figureSlots = new int[] {fig.get(0), fig.get(1)};
        scoreSlots = new int[] {score.get(0), score.get(1)};
        compareSlot = settings.slot("compare", 22);
        leverSlot = settings.slot("lever", 40);
        buttons.clear();
        figureItems.clear();
        for (String f : FIGURES) {
            List<Integer> s = settings.slots("buttons-" + f);
            if (s.isEmpty()) return "slots.buttons-" + f + " missing";
            buttons.add(s);
            figureItems.add(settings.item(f));
        }
        List<ItemSpec> n = new ArrayList<>();
        for (Object o : yml.getList("items.numbers", List.of())) n.add(ItemSpec.of(o, ItemSpec.NONE));
        if (n.size() <= gamesToWin) return "items.numbers needs at least games-to-win + 1 entries";
        numbers = n;
        hiddenItem = settings.item("hidden");
        compareLeft = settings.item("compare-left");
        compareRight = settings.item("compare-right");
        compareDraw = settings.item("compare-draw");
        leverItem = settings.item("lever");
        return null;
    }

    int figureAt(int slot) {
        for (int f = 0; f < buttons.size(); f++) if (buttons.get(f).contains(slot)) return f;
        return -1;
    }

    /** 1 = a wins, -1 = b wins, 0 = same figure. */
    static int compare(int a, int b) {
        if (a == b) return 0;
        return Math.floorMod(a - b, 3) == 1 ? 1 : -1;
    }

    @Override
    protected Match newMatch(Player creator, Bet creatorBet, Player acceptor, Bet acceptorBet) {
        return new RpsMatch(plugin, this, creator, creatorBet, acceptor, acceptorBet);
    }
}
