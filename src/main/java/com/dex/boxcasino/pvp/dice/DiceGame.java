package com.dex.boxcasino.pvp.dice;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.pvp.Match;
import com.dex.boxcasino.pvp.PvpGame;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Xúc Xắc 1v1 (spec 7.7): each side rolls 2 dice, higher sum takes the pot, a tie rolls again. Playable anywhere. */
public final class DiceGame extends PvpGame {

    int[][] diceSlots = new int[2][2];
    int[] scoreSlots = new int[2];
    int compareSlot;
    int leverSlot;
    int maxRolls;
    int stepTicks;
    List<ItemSpec> faces = List.of();
    List<ItemSpec> sums = List.of();
    ItemSpec compareLeft;
    ItemSpec compareRight;
    ItemSpec compareDraw;
    ItemSpec leverWait;
    ItemSpec leverRolling;
    ItemSpec leverEnd;

    public DiceGame(BoxCasinoPlugin plugin) {
        super(plugin, "dice");
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
        List<Integer> left = settings.slots("dice-left");
        List<Integer> right = settings.slots("dice-right");
        List<Integer> score = settings.slots("score");
        if (left.size() != 2 || right.size() != 2 || score.size() != 2) return "slots.dice-left/right and score need 2 slots";
        diceSlots = new int[][] {{left.get(0), left.get(1)}, {right.get(0), right.get(1)}};
        scoreSlots = new int[] {score.get(0), score.get(1)};
        compareSlot = settings.slot("compare", 22);
        leverSlot = settings.slot("lever", 13);
        maxRolls = Math.max(1, yml.getInt("max-rolls", 10));
        stepTicks = Math.max(5, yml.getInt("step-ticks", 15));
        faces = list(yml, "items.faces");
        sums = list(yml, "items.sums");
        if (faces.size() != 6 || sums.size() != 11) return "items.faces needs 6 entries and items.sums 11 (2..12)";
        compareLeft = settings.item("compare-left");
        compareRight = settings.item("compare-right");
        compareDraw = settings.item("compare-draw");
        leverWait = settings.item("lever-wait");
        leverRolling = settings.item("lever-rolling");
        leverEnd = settings.item("lever-end");
        return null;
    }

    private static List<ItemSpec> list(YamlConfiguration yml, String path) {
        List<ItemSpec> out = new ArrayList<>();
        for (Object o : yml.getList(path, List.of())) out.add(ItemSpec.of(o, ItemSpec.NONE));
        return out;
    }

    @Override
    protected Match newMatch(Player creator, Bet creatorBet, Player acceptor, Bet acceptorBet) {
        return new DiceMatch(plugin, this, creator, creatorBet, acceptor, acceptorBet);
    }
}
