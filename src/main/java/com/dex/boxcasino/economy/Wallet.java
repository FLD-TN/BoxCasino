package com.dex.boxcasino.economy;

import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.util.Format;
import com.dex.boxcore.api.GemResult;
import com.dex.boxcore.api.GemService;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault money and BoxCore gems behind one API. Main thread only. Every change goes through the result of the economy
 * call (spec 2.5): never "check the balance, then decide".
 */
public final class Wallet {

    private Economy vault;
    private final GemService gems;
    private final Messages messages;

    public Wallet(GemService gems, Messages messages) {
        this.gems = gems;
        this.messages = messages;
    }

    /**
     * Looks up the Vault economy. The economy plugin may enable after BoxCasino, so this is retried on the first
     * server tick. Until it returns true nobody can bet.
     */
    public boolean resolveVault() {
        if (vault != null) return true;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) vault = rsp.getProvider();
        return vault != null;
    }

    public boolean ready() {
        return vault != null;
    }

    /** Whole units; -1 when gems are not loaded. */
    public long balance(Player player, Currency currency) {
        return switch (currency) {
            case MONEY -> (long) Math.floor(vault.getBalance(player));
            case GEM -> gems.balance(player.getUniqueId());
        };
    }

    public boolean withdraw(Player player, Currency currency, long amount, String reason) {
        if (amount <= 0) return false;
        return switch (currency) {
            case MONEY -> vault.has(player, amount) && vault.withdrawPlayer(player, amount).transactionSuccess();
            case GEM -> gems.withdraw(player.getUniqueId(), amount, reason) == GemResult.SUCCESS;
        };
    }

    /** {@code false} when the deposit could not be made now (the caller stores a pending payout). */
    public boolean deposit(UUID player, Currency currency, long amount, String reason) {
        if (amount <= 0) return true;
        return switch (currency) {
            case MONEY -> {
                OfflinePlayer target = Bukkit.getOfflinePlayer(player);
                yield vault.depositPlayer(target, amount).transactionSuccess();
            }
            case GEM -> gems.deposit(player, amount, reason) == GemResult.SUCCESS;
        };
    }

    /** "&a1.250$" / "&#8245F81.250 Gem" from messages.yml. */
    public String format(Currency currency, long amount) {
        return messages.raw("currency." + currency.key(), "amount", Format.number(amount), "gem", gems.displayName());
    }

    public String name(Currency currency) {
        return messages.raw("currency-name." + currency.key(), "gem", gems.displayName());
    }
}
