package com.dex.boxcasino.hook;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.extra.LeaderboardService;
import com.dex.boxcasino.storage.Profile;
import com.dex.boxcasino.util.Format;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

/**
 * %boxcasino_...%: won_/lost_/net_/biggest_win_/profit_left_ + money|gem, playtime_left, rest_left. Numbers only, in
 * the server's dot format. Leaderboards and jackpot come with 0.5.0.
 */
public final class CasinoExpansion extends PlaceholderExpansion {

    private final BoxCasinoPlugin plugin;

    public CasinoExpansion(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "boxcasino";
    }

    @Override
    public String getAuthor() {
        return "Dex";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        String p = params.toLowerCase(Locale.ROOT);
        // player-independent: jackpot_<money|gem>, top_net_ / top_loss_<money|gem>_<1-10>_<name|value>,
        // giovang (time left, empty when off), giovang_percent
        if (p.startsWith("jackpot_")) {
            Currency c = Currency.parse(p.substring(8));
            return c == null ? null : Format.number(plugin.jackpot().pot(c));
        }
        if (p.equals("giovang")) {
            long s = plugin.goldenHour().secondsLeft();
            return s <= 0 ? "" : Format.duration(s);
        }
        if (p.equals("giovang_percent")) {
            return plugin.goldenHour().active() ? Format.decimal(plugin.goldenHour().percent()) : "0";
        }
        if (p.startsWith("top_net_") || p.startsWith("top_loss_")) {
            boolean loss = p.startsWith("top_loss_");
            String[] parts = p.substring(loss ? 9 : 8).split("_");
            if (parts.length != 3) return null;
            Currency c = Currency.parse(parts[0]);
            int rank;
            try {
                rank = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ex) {
                return null;
            }
            if (c == null || rank < 1 || rank > LeaderboardService.SIZE) return null;
            var top = plugin.leaderboard().top(loss ? LeaderboardService.Board.LOSERS : LeaderboardService.Board.WINNERS, c);
            if (rank > top.size()) return parts[2].equals("name") ? "-" : "0";
            var e = top.get(rank - 1);
            return parts[2].equals("name") ? e.name() : Format.number(e.net());
        }
        if (player == null) return "";
        switch (p) {
            case "playtime_left" -> {
                long s = plugin.access().playLeft(player);
                return s < 0 ? plugin.messages().raw("menu.unlimited") : Format.duration(s);
            }
            case "rest_left" -> {
                return Format.duration(plugin.access().restLeft(player));
            }
            default -> {
            }
        }
        int cut = p.lastIndexOf('_');
        if (cut < 0) return null;
        Currency currency = Currency.parse(p.substring(cut + 1));
        if (currency == null) return null;
        String key = p.substring(0, cut);
        if (key.equals("profit_left")) {
            long left = plugin.access().profitLeft(player, currency);
            return left < 0 ? plugin.messages().raw("menu.unlimited") : Format.number(left);
        }
        Profile profile = plugin.profiles().get(player.getUniqueId());
        if (profile == null) return "0";
        Profile.Totals t = profile.totals.get(currency);
        return switch (key) {
            case "won" -> Format.number(t.won);
            case "lost" -> Format.number(t.lost);
            case "net" -> (t.paid >= t.wagered ? "" : "-") + Format.number(Math.abs(t.paid - t.wagered));
            case "biggest_win" -> Format.number(t.biggestWin);
            case "rounds" -> Format.number(t.rounds);
            default -> null;
        };
    }
}
