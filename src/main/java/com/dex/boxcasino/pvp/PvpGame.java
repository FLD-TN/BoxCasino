package com.dex.boxcasino.pvp;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.util.Payouts;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * A 1v1 game (spec 7.7): challenges, escrow, tax on the pot. The house edge is the tax only, so the RTP of a player is
 * {@code 1 - tax}.
 */
public abstract class PvpGame extends Game {

    double taxPercent;
    int challengeMinutes;
    int inviteSeconds;
    protected int turnSeconds;
    /** Seat look: left (challenge creator) and right (acceptor). */
    protected int[] spotSlots = new int[2];
    protected int[] headSlots = new int[2];
    protected ItemSpec spotItem;
    protected ItemSpec victoryItem;
    protected ItemSpec defeatItem;
    protected ItemSpec drawItem;

    protected PvpGame(BoxCasinoPlugin plugin, String id) {
        super(plugin, id);
    }

    @Override
    protected final String loadGame(YamlConfiguration yml) {
        taxPercent = yml.getDouble("tax-percent", 5);
        if (taxPercent < 0 || taxPercent >= 100) return "tax-percent must be 0..99";
        challengeMinutes = Math.max(1, yml.getInt("challenge-minutes", 5));
        inviteSeconds = Math.max(10, yml.getInt("invite-seconds", 60));
        turnSeconds = Math.max(3, yml.getInt("turn-seconds", 10));
        var spots = settings.slots("seats");
        var heads = settings.slots("heads");
        if (spots.size() != 2 || heads.size() != 2) return "slots.seats and slots.heads need 2 slots";
        spotSlots = new int[] {spots.get(0), spots.get(1)};
        headSlots = new int[] {heads.get(0), heads.get(1)};
        spotItem = settings.item("spot");
        victoryItem = settings.item("victory");
        defeatItem = settings.item("defeat");
        drawItem = settings.item("draw");
        return loadPvp(yml);
    }

    protected abstract String loadPvp(YamlConfiguration yml);

    @Override
    public boolean house() {
        return false;
    }

    /** Starts a match between two escrowed bets (seat 0 = creator). */
    protected abstract Match newMatch(Player creator, Bet creatorBet, Player acceptor, Bet acceptorBet);

    public int headSlot(int seat) {
        return headSlots[seat];
    }

    /** Seconds a player has for one move. */
    public int turnSeconds() {
        return turnSeconds;
    }

    public double taxPercent() {
        return taxPercent;
    }

    /** What the winner receives: the pot minus the tax. */
    public long winnerPayout(long stake) {
        return Payouts.payout(stake * 2, 1 - taxPercent / 100.0);
    }

    @Override
    public double rtp() {
        return 1 - taxPercent / 100.0;
    }

    /** Games without a table (Xúc Xắc, Oẳn Tù Tì): a lobby of challenges instead of the table on the texture. */
    protected boolean lobby() {
        return false;
    }

    @Override
    public void open(Player player, boolean npc) {
        Match match = plugin.pvp().matchOf(player.getUniqueId());
        if (match != null) match.open(player);
        else if (lobby()) new LobbyView(plugin, player, this).open();
        else new TableView(plugin, player, this, npc, null).open();
    }

    /**
     * Sat down on a stool of a machine: the match there, or the table of that machine. A lobby game (an old config
     * may still give it stools) opens its lobby instead.
     */
    public void openAtMachine(Player player, String machine) {
        Match match = plugin.pvp().matchOf(player.getUniqueId());
        if (lobby()) {
            open(player, false);
        } else if (match == null) {
            new TableView(plugin, player, this, false, machine).open();
        } else if (machine.equals(match.machine)) {
            match.open(player);
        } else {
            plugin.messages().send(player, "pvp.in-match");
        }
    }

    @Override
    public void shutdown() {
        plugin.pvp().shutdown(this);
    }

    /** Fair 1v1: each side wins half the time; the measured RTP is the payout ratio. */
    @Override
    public double simulate(long rounds, RandomGenerator random) {
        long stake = 1000;
        double paid = 0;
        for (long i = 0; i < rounds; i++) if (random.nextBoolean()) paid += winnerPayout(stake);
        return paid / (rounds * (double) stake);
    }
}
