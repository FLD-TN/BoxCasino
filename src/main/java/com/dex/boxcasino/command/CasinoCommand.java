package com.dex.boxcasino.command;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.menu.ExcludeMenu;
import com.dex.boxcasino.menu.MainMenu;
import com.dex.boxcasino.menu.StatsMenu;
import com.dex.boxcasino.storage.Ledger;
import com.dex.boxcasino.storage.ProfileStore;
import com.dex.boxcasino.util.Format;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /casino and its sub-commands (spec 11). */
public final class CasinoCommand implements TabExecutor {

    private static final String ADMIN = "boxcasino.admin";
    private static final String GOLDEN_HOUR = "boxcasino.giovang";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final BoxCasinoPlugin plugin;

    public CasinoCommand(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> openMenu(sender);
            case "tieptuc" -> {
                if (!(sender instanceof Player p)) return true;
                var room = plugin.rooms().roomOf(p.getUniqueId());
                if (room != null) room.reopen(p);
                else msg().send(sender, "command.nothing-running");
            }
            case "nhac" -> {
                if (sender instanceof Player p) plugin.music().toggle(p);
                else msg().send(sender, "command.player-only");
            }
            case "pvp" -> pvp(sender, args);
            case "nghi" -> exclude(sender, args);
            case "thongke" -> {
                if (sender instanceof Player p) new StatsMenu(plugin, p).open();
                else msg().send(sender, "command.player-only");
            }
            case "open" -> openFor(sender, args);
            case "giovang" -> goldenHour(sender, args);
            case "admin" -> admin(sender, args);
            default -> msg().send(sender, "command.usage");
        }
        return true;
    }

    private void openMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "command.player-only");
            return;
        }
        if (plugin.access().busyElsewhere(player, null) || !plugin.access().canPlay(player, true, false)) return;
        new MainMenu(plugin, player, false).open();
    }

    /** /casino pvp <accept|decline|cancel> <id>: targets of the clickable chat buttons. */
    private void pvp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "command.player-only");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[2]);
        } catch (RuntimeException ex) {
            msg().send(sender, "pvp.not-found");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "accept" -> plugin.pvp().accept(player, id, false);
            case "decline" -> plugin.pvp().decline(player, id);
            case "cancel" -> plugin.pvp().cancel(player, id);
            default -> msg().send(sender, "pvp.not-found");
        }
    }

    /**
     * /casino giovang: time left. /casino giovang <phút> [%]: start (replaces a running one). /casino giovang tat: stop.
     * Starting and stopping need boxcasino.giovang (console always may: CommandTimer, shops).
     */
    private void goldenHour(CommandSender sender, String[] args) {
        var gh = plugin.goldenHour();
        if (args.length < 2) {
            if (gh.active()) {
                msg().send(sender, "golden-hour.status-on", "time", Format.duration(gh.secondsLeft()),
                        "percent", Format.decimal(gh.percent()));
            } else {
                msg().send(sender, "golden-hour.status-off");
            }
            return;
        }
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission(GOLDEN_HOUR)) {
            msg().send(sender, "command.no-permission");
            return;
        }
        if (args[1].equalsIgnoreCase("tat") || args[1].equalsIgnoreCase("off")) {
            msg().send(sender, gh.stop() ? "golden-hour.stopped" : "golden-hour.status-off");
            return;
        }
        long maxMinutes = Math.max(1, plugin.getConfig().getLong("golden-hour.max-minutes", 240));
        double maxPercent = Math.max(1, plugin.getConfig().getDouble("golden-hour.max-percent", 50));
        long minutes;
        double percent = plugin.getConfig().getDouble("golden-hour.percent", 5);
        try {
            minutes = Long.parseLong(args[1]);
            if (args.length >= 3) percent = Double.parseDouble(args[2].replace("%", "").replace(',', '.'));
        } catch (NumberFormatException ex) {
            msg().send(sender, "golden-hour.usage", "max_minutes", String.valueOf(maxMinutes), "max_percent", Format.decimal(maxPercent));
            return;
        }
        if (minutes < 1 || minutes > maxMinutes || percent <= 0 || percent > maxPercent) {
            msg().send(sender, "golden-hour.usage", "max_minutes", String.valueOf(maxMinutes), "max_percent", Format.decimal(maxPercent));
            return;
        }
        gh.start(minutes, percent);
        if (!(sender instanceof Player)) {
            plugin.getLogger().info("Golden hour started: " + minutes + " min, +" + percent + "% profit");
        }
    }

    private void exclude(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "command.player-only");
            return;
        }
        long seconds = 0;
        if (args.length >= 2) {
            Long s = ExcludeMenu.DURATIONS.get(args[1].toLowerCase(Locale.ROOT));
            if (s == null) {
                msg().send(sender, "command.usage-nghi");
                return;
            }
            seconds = s;
        }
        new ExcludeMenu(plugin, player, seconds).open();
    }

    /** /casino open <player> <game|menu>, for NPCs (ZNPCsPlus console action with {player}). */
    private void openFor(CommandSender sender, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission(ADMIN)) {
            msg().send(sender, "command.no-permission");
            return;
        }
        if (args.length < 3) {
            msg().send(sender, "command.usage-open");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg().send(sender, "command.player-not-found", "player", args[1]);
            return;
        }
        if (args[2].equalsIgnoreCase("menu")) {
            if (!plugin.access().busyElsewhere(target, null) && plugin.access().canPlay(target, true, true)) new MainMenu(plugin, target, true).open();
            return;
        }
        Game game = plugin.games().get(args[2]);
        if (game == null) {
            msg().send(sender, "command.game-not-found", "game", args[2]);
            return;
        }
        if (!game.enabled()) {
            msg().send(target, "game.disabled");
            return;
        }
        if (!plugin.access().busyElsewhere(target, game.id())
                && game.permitted(target) && plugin.access().canPlay(target, game.casinoOnly(), true)) game.open(target, true);
    }

    // --- admin ---

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            msg().send(sender, "command.no-permission");
            return;
        }
        String sub = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "reload" -> {
                    plugin.reloadAll();
                    msg().send(sender, "admin.reloaded");
                }
                case "toggle" -> toggle(sender, args);
                case "stats" -> house(sender, args);
                case "simulate" -> simulate(sender, args);
                case "log" -> log(sender, args);
                case "refund" -> refund(sender, args);
                case "alt" -> alt(sender, args);
                case "unban" -> unban(sender, args);
                case "debug" -> debug(sender);
                case "jackpot" -> jackpot(sender, args);
                case "machine" -> machine(sender, args);
                case "dealer" -> {
                    int n = plugin.dealers().reskin();
                    msg().send(sender, n < 0 ? "machine.dealer-no-plugin" : "machine.dealer-reskin", "count", String.valueOf(n));
                }
                default -> msg().send(sender, "admin.usage");
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Admin command failed", ex);
            msg().send(sender, "admin.storage-error");
        }
    }

    private void toggle(CommandSender sender, String[] args) {
        Game game = args.length < 3 ? null : plugin.games().get(args[2]);
        if (game == null) {
            msg().send(sender, "command.game-not-found", "game", args.length < 3 ? "?" : args[2]);
            return;
        }
        game.setEnabled(!game.enabled());
        msg().send(sender, game.enabled() ? "admin.toggled-on" : "admin.toggled-off", "game", game.displayName());
    }

    private void house(CommandSender sender, String[] args) throws SQLException {
        String game = args.length >= 3 && plugin.games().get(args[2]) != null ? args[2].toLowerCase(Locale.ROOT) : null;
        String range = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT)
                : args.length >= 3 && game == null ? args[2].toLowerCase(Locale.ROOT) : "today";
        LocalDate today = LocalDate.parse(plugin.profiles().today());
        String from = switch (range) {
            case "7d" -> today.minusDays(6).toString();
            case "all" -> "0000-00-00";
            default -> today.toString();
        };
        List<ProfileStore.HouseRow> rows = plugin.profileStore().house(from, game);
        msg().send(sender, "admin.stats-header", "range", range);
        if (rows.isEmpty()) msg().send(sender, "admin.stats-empty");
        for (ProfileStore.HouseRow r : rows) {
            long net = r.wagered() - r.paid() + r.tax();
            Game g = plugin.games().get(r.game());
            msg().send(sender, "admin.stats-row", "game", g == null ? r.game() : g.displayName(),
                    "wagered", plugin.wallet().format(r.currency(), r.wagered()),
                    "paid", plugin.wallet().format(r.currency(), r.paid()),
                    "net", (net >= 0 ? "+" : "-") + plugin.wallet().format(r.currency(), Math.abs(net)),
                    "rtp", r.wagered() == 0 ? "-" : Format.percent((double) r.paid() / r.wagered()));
        }
    }

    private void simulate(CommandSender sender, String[] args) {
        Game game = args.length < 3 ? null : plugin.games().get(args[2]);
        if (game == null) {
            msg().send(sender, "command.game-not-found", "game", args.length < 3 ? "?" : args[2]);
            return;
        }
        long rounds;
        try {
            rounds = args.length >= 4 ? Long.parseLong(args[3]) : 1_000_000;
        } catch (NumberFormatException ex) {
            msg().send(sender, "admin.usage");
            return;
        }
        long n = Math.max(1000, Math.min(50_000_000, rounds));
        msg().send(sender, "admin.simulate-start", "game", game.displayName(), "rounds", Format.number(n));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            double measured = game.simulate(n, RandomGenerator.of("L64X128MixRandom"));
            Bukkit.getScheduler().runTask(plugin, () -> msg().send(sender, "admin.simulate-result",
                    "game", game.displayName(), "rounds", Format.number(n), "measured", Format.percent(measured),
                    "expected", Double.isNaN(game.rtp()) ? "-" : Format.percent(game.rtp())));
        });
    }

    private void log(CommandSender sender, String[] args) throws SQLException {
        OfflinePlayer target = args.length < 3 ? null : offline(args[2]);
        if (target == null) {
            msg().send(sender, "command.player-not-found", "player", args.length < 3 ? "?" : args[2]);
            return;
        }
        int page = 1;
        if (args.length >= 4) {
            try {
                page = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ignored) {
                // page 1
            }
        }
        List<Ledger.BetRow> rows = plugin.ledger().recent(target.getUniqueId(), 10, (page - 1) * 10);
        msg().send(sender, "admin.log-header", "player", String.valueOf(target.getName()), "page", String.valueOf(page));
        for (Ledger.BetRow r : rows) {
            Game g = plugin.games().get(r.game());
            msg().send(sender, "admin.log-row", "id", String.valueOf(r.id()),
                    "time", TIME.format(Instant.ofEpochMilli(r.createdAt()).atZone(plugin.settings().zone)),
                    "game", g == null ? r.game() : g.displayName(),
                    "bet", plugin.wallet().format(r.currency(), r.amount()),
                    "payout", plugin.wallet().format(r.currency(), r.payout()), "state", r.state());
        }
    }

    private void refund(CommandSender sender, String[] args) throws SQLException {
        long id;
        try {
            id = Long.parseLong(args[2]);
        } catch (RuntimeException ex) {
            msg().send(sender, "admin.usage");
            return;
        }
        msg().send(sender, plugin.bets().adminRefund(id) ? "admin.refunded" : "admin.refund-refused", "id", String.valueOf(id));
    }

    private void alt(CommandSender sender, String[] args) throws SQLException {
        if (args.length < 4) {
            msg().send(sender, "admin.usage");
            return;
        }
        OfflinePlayer target = offline(args[3]);
        if (target == null) {
            msg().send(sender, "command.player-not-found", "player", args[3]);
            return;
        }
        UUID uuid = target.getUniqueId();
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "info" -> {
                ProfileStore.AltInfo info = plugin.profileStore().altInfo(uuid);
                String mains = info.mainAccounts().stream().map(s -> {
                    String name = Bukkit.getOfflinePlayer(UUID.fromString(s)).getName();
                    return name == null ? s : name;
                }).collect(Collectors.joining(", "));
                msg().send(sender, "admin.alt-info", "player", String.valueOf(target.getName()),
                        "ips", String.valueOf(info.ipCount()), "allowed", info.allowed() ? "có" : "không",
                        "mains", mains.isEmpty() ? "-" : mains);
            }
            case "allow" -> {
                plugin.profileStore().allowAlt(uuid);
                resetCache(uuid);
                msg().send(sender, "admin.alt-allowed", "player", String.valueOf(target.getName()));
            }
            case "reset" -> {
                int n = plugin.profileStore().resetAlt(uuid);
                for (Player p : Bukkit.getOnlinePlayers()) plugin.access().resetAltCache(p);
                msg().send(sender, "admin.alt-reset", "player", String.valueOf(target.getName()), "count", String.valueOf(n));
            }
            default -> msg().send(sender, "admin.usage");
        }
    }

    private void unban(CommandSender sender, String[] args) {
        OfflinePlayer target = args.length < 3 ? null : offline(args[2]);
        if (target == null) {
            msg().send(sender, "command.player-not-found", "player", args.length < 3 ? "?" : args[2]);
            return;
        }
        plugin.profiles().updateOffline(target.getUniqueId(), p -> p.excludedUntil = 0);
        msg().send(sender, "admin.unbanned", "player", String.valueOf(target.getName()));
    }

    /** /casino admin jackpot <money|gem> [set|add <amount>]: shows or changes a pot. */
    private void jackpot(CommandSender sender, String[] args) {
        Currency currency = args.length >= 3 ? Currency.parse(args[2]) : null;
        if (currency == null) {
            for (Currency c : Currency.values()) {
                msg().send(sender, "admin.jackpot-show", "currency", plugin.wallet().name(c),
                        "pot", plugin.wallet().format(c, plugin.jackpot().pot(c)));
            }
            return;
        }
        if (args.length < 5) {
            msg().send(sender, "admin.jackpot-show", "currency", plugin.wallet().name(currency),
                    "pot", plugin.wallet().format(currency, plugin.jackpot().pot(currency)));
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[4]);
        } catch (NumberFormatException ex) {
            msg().send(sender, "admin.usage");
            return;
        }
        long now = plugin.jackpot().pot(currency);
        plugin.jackpot().set(currency, args[3].equalsIgnoreCase("add") ? now + amount : amount);
        msg().send(sender, "admin.jackpot-show", "currency", plugin.wallet().name(currency),
                "pot", plugin.wallet().format(currency, plugin.jackpot().pot(currency)));
    }

    /** /casino admin machine place <game> | remove (looking at the machine) | refit [radius]. */
    private void machine(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "command.player-only");
            return;
        }
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
        if (action.equals("remove")) {
            plugin.machines().remove(player);
            return;
        }
        if (action.equals("refit")) {
            double radius = 30;
            try {
                if (args.length >= 4) radius = Math.max(1, Math.min(200, Double.parseDouble(args[3])));
            } catch (NumberFormatException ignored) {
            }
            plugin.machines().refit(player, radius);
            return;
        }
        Game game = args.length >= 4 ? plugin.games().get(args[3]) : null;
        if (!action.equals("place") || game == null) {
            msg().send(sender, "admin.usage-machine");
            return;
        }
        plugin.machines().place(player, game);
    }

    /** Texture diagnostics: ItemsAdder hook, a few pack items and GUI font images. */
    private void debug(CommandSender sender) {
        var items = plugin.items();
        boolean iaEnabled = Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
        sender.sendMessage("[BoxCasino] ItemsAdder plugin bật: " + iaEnabled + " | BoxCasino kết nối API: " + items.hasItemsAdder());
        for (String line : items.describe(List.of("iavegasgui:chip_1", "iavegasgui:empty_button",
                "iavegasgui:minesweeper_info", "iavegasgui:pyramid_ball", "iavegasgui:poker_card_ace_pika",
                "iavegasgui:main_gui_logo_minesweeper"))) {
            sender.sendMessage("  " + line);
        }
        for (String id : List.of("iavegasgui:main_gui", "iavegasgui:minesweeper_gui", "iavegasgui:pyramid_gui")) {
            sender.sendMessage("  " + items.glyphInfo(id));
        }
        sender.sendMessage("  Dự phòng khi không tìm thấy: STICK/PAPER/DIAMOND + custom-model-data trong games/*.yml");
    }

    private void resetCache(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) plugin.access().resetAltCache(p);
    }

    private static OfflinePlayer offline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    // --- tab completion ---

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        boolean admin = sender.hasPermission(ADMIN);
        if (args.length == 1) {
            options.addAll(List.of("tieptuc", "nhac", "nghi", "thongke", "giovang"));
            if (admin) options.addAll(List.of("open", "admin"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("nghi")) {
            options.addAll(ExcludeMenu.DURATIONS.keySet());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("giovang") && sender.hasPermission(GOLDEN_HOUR)) {
            options.addAll(List.of("30", "60", "120", "tat"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("giovang") && sender.hasPermission(GOLDEN_HOUR)) {
            options.addAll(List.of("5", "10", "20"));
        } else if (admin && args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            options.addAll(List.of("reload", "toggle", "stats", "simulate", "log", "refund", "alt", "unban", "debug", "jackpot", "machine", "dealer"));
        } else if (admin && args.length == 2 && args[0].equalsIgnoreCase("open")) {
            return null;
        } else if (admin && args.length == 3 && args[0].equalsIgnoreCase("open")) {
            options.add("menu");
            for (Game g : plugin.games().all()) options.add(g.id());
        } else if (admin && args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "toggle", "stats", "simulate" -> {
                    for (Game g : plugin.games().all()) options.add(g.id());
                    if (args[1].equalsIgnoreCase("stats")) options.addAll(List.of("today", "7d", "all"));
                }
                case "alt" -> options.addAll(List.of("info", "allow", "reset"));
                case "jackpot" -> options.addAll(List.of("money", "gem"));
                case "machine" -> options.addAll(List.of("place", "remove", "refit"));
                case "log", "unban" -> {
                    return null;
                }
                default -> {
                }
            }
        } else if (admin && args.length == 4 && args[0].equalsIgnoreCase("admin")) {
            if (args[1].equalsIgnoreCase("alt")) return null;
            if (args[1].equalsIgnoreCase("stats")) options.addAll(List.of("today", "7d", "all"));
            if (args[1].equalsIgnoreCase("jackpot")) options.addAll(List.of("set", "add"));
            if (args[1].equalsIgnoreCase("machine") && args[2].equalsIgnoreCase("place")) {
                for (Game g : plugin.games().all()) options.add(g.id());
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(last)).collect(Collectors.toList());
    }
}
