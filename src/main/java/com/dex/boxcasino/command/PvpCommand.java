package com.dex.boxcasino.command;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.pvp.PvpGame;
import com.dex.boxcasino.pvp.PvpService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * /xucxac and /kbb (spec 7.7): no argument = the challenge lobby; {@code <tien|gem> <amount>} = open challenge;
 * {@code <player> <tien|gem> <amount>} = direct challenge. Currency and amount may come in any order; the amount is plain
 * digits (10000).
 */
public final class PvpCommand implements TabExecutor {

    private final BoxCasinoPlugin plugin;
    private final String gameId;

    public PvpCommand(BoxCasinoPlugin plugin, String gameId) {
        this.plugin = plugin;
        this.gameId = gameId;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.player-only");
            return true;
        }
        Game g = plugin.games().get(gameId);
        if (!(g instanceof PvpGame game)) return true;
        if (!game.enabled()) {
            plugin.messages().send(player, "game.disabled");
            return true;
        }
        if (plugin.access().busyElsewhere(player, game.id())) return true;
        if (args.length == 0) {
            if (game.permitted(player) && plugin.access().canPlay(player, game.casinoOnly(), false)) game.open(player, false);
            return true;
        }
        Player target = null;
        int i = 0;
        if (args.length == 3) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                plugin.messages().send(player, "command.player-not-found", "player", args[0]);
                return true;
            }
            i = 1;
        } else if (args.length != 2) {
            plugin.messages().send(player, "pvp.usage", "label", label);
            return true;
        }
        // currency and amount in any order
        Currency currency = Currency.parse(args[i]);
        long amount = PvpService.parseAmount(args[i + 1]);
        if (currency == null) {
            currency = Currency.parse(args[i + 1]);
            amount = PvpService.parseAmount(args[i]);
        }
        if (amount <= 0 || currency == null) {
            plugin.messages().send(player, "pvp.usage", "label", label);
            return true;
        }
        plugin.pvp().create(player, game, target, currency, amount, false);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("tien", "gem"));
            for (Player p : Bukkit.getOnlinePlayers()) if (!p.equals(sender)) options.add(p.getName());
        } else if (args.length == 2 && Currency.parse(args[0]) == null) {
            options.addAll(List.of("tien", "gem"));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).collect(Collectors.toList());
    }
}
