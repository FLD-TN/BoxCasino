package com.dex.boxcasino.pvp;

import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.game.Bet;
import java.util.UUID;
import org.bukkit.scheduler.BukkitTask;

/** An open or direct challenge; the creator's stake is already held. */
public final class Challenge {

    final int id;
    final PvpGame game;
    final UUID creator;
    final String creatorName;
    /** Null = open to everyone. */
    final UUID target;
    final String targetName;
    final Currency currency;
    final Bet bet;
    final long expiresAt;
    /** Machine in the world where the creator sits; only the player on the other stool can accept. Null = remote. */
    String machine;
    BukkitTask expireTask;

    Challenge(int id, PvpGame game, UUID creator, String creatorName, UUID target, String targetName, Currency currency,
              Bet bet, long expiresAt) {
        this.id = id;
        this.game = game;
        this.creator = creator;
        this.creatorName = creatorName;
        this.target = target;
        this.targetName = targetName;
        this.currency = currency;
        this.bet = bet;
        this.expiresAt = expiresAt;
    }

    public long amount() {
        return bet.amount();
    }

    boolean visibleTo(UUID player) {
        return target == null || target.equals(player) || creator.equals(player);
    }
}
