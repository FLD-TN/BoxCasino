package com.dex.boxcasino.pvp;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.game.GameSettings;
import com.dex.boxcasino.gui.CasinoView;
import com.dex.boxcasino.util.Format;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Challenges and running matches of every PvP game (spec 7.7). Main thread only. */
public final class PvpService {

    private final BoxCasinoPlugin plugin;
    private final Map<Integer, Challenge> challenges = new LinkedHashMap<>();
    private final Map<UUID, Match> matches = new HashMap<>();
    private final Set<TableView> tables = new HashSet<>();
    private final Set<LobbyView> lobbies = new HashSet<>();
    private int nextId = 1;

    public PvpService(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    public Match matchOf(UUID player) {
        return matches.get(player);
    }

    /** Challenges a viewer can see: at their machine only (machine set), or the remote ones (machine null). */
    List<Challenge> challenges(PvpGame game, UUID viewer, String machine) {
        List<Challenge> out = new ArrayList<>();
        for (Challenge c : challenges.values()) {
            if (c.game == game && c.visibleTo(viewer) && java.util.Objects.equals(c.machine, machine)) out.add(c);
        }
        return out;
    }

    Challenge ownChallenge(PvpGame game, UUID player) {
        for (Challenge c : challenges.values()) if (c.game == game && c.creator.equals(player)) return c;
        return null;
    }

    void addTable(TableView view) {
        tables.add(view);
    }

    void removeTable(TableView view) {
        tables.remove(view);
    }

    void addLobby(LobbyView view) {
        lobbies.add(view);
    }

    void removeLobby(LobbyView view) {
        lobbies.remove(view);
    }

    Challenge challenge(int id) {
        return challenges.get(id);
    }

    private void refreshTables() {
        for (TableView v : List.copyOf(tables)) v.refresh();
        for (LobbyView v : List.copyOf(lobbies)) v.refresh();
    }

    /** Creates a challenge; {@code target} null = open to everyone. Returns false when refused (player told). */
    public boolean create(Player player, PvpGame game, Player target, Currency currency, long amount, boolean npc) {
        return create(player, game, target, currency, amount, npc, null);
    }

    /** {@code machine} set = a challenge at a machine in the world, only for the player on the other stool. */
    public boolean create(Player player, PvpGame game, Player target, Currency currency, long amount, boolean npc,
                          String machine) {
        if (!game.enabled()) {
            plugin.messages().send(player, "game.disabled");
            return false;
        }
        if (!game.permitted(player) || !plugin.access().canPlay(player, game.casinoOnly(), npc)) return false;
        if (matches.containsKey(player.getUniqueId())) {
            plugin.messages().send(player, "pvp.in-match");
            return false;
        }
        if (ownChallenge(game, player.getUniqueId()) != null) {
            plugin.messages().send(player, "pvp.already-open");
            return false;
        }
        if (target != null && target.equals(player)) {
            plugin.messages().send(player, "pvp.self");
            return false;
        }
        GameSettings.Limits limits = game.settings().limits.get(currency);
        if (amount < limits.min() || amount > limits.max()) {
            plugin.messages().send(player, "pvp.limits", "min", plugin.wallet().format(currency, limits.min()),
                    "max", plugin.wallet().format(currency, limits.max()));
            return false;
        }
        Bet bet = plugin.bets().place(player, game.id(), currency, amount, null);
        if (bet == null) return false;
        long life = target == null ? game.challengeMinutes * 60L : game.inviteSeconds;
        Challenge c = new Challenge(nextId++, game, player.getUniqueId(), player.getName(),
                target == null ? null : target.getUniqueId(), target == null ? null : target.getName(), currency, bet,
                System.currentTimeMillis() + life * 1000);
        c.machine = machine;
        challenges.put(c.id, c);
        c.expireTask = Bukkit.getScheduler().runTaskLater(plugin, () -> expire(c), life * 20);
        String money = plugin.wallet().format(currency, amount);
        if (machine != null) {
            plugin.messages().send(player, "pvp.created-machine", "game", game.displayName(), "bet", money);
            announce(c, player, money);
        } else if (target == null) {
            plugin.messages().send(player, "pvp.created-open", "game", game.displayName(), "bet", money,
                    "minutes", String.valueOf(game.challengeMinutes), "table", String.valueOf(c.id));
            announce(c, player, money);
        } else {
            plugin.messages().send(player, "pvp.created-direct", "game", game.displayName(), "bet", money,
                    "player", target.getName());
            target.sendMessage(plugin.messages().text("prefix").append(plugin.messages().text("pvp.invite",
                    "player", player.getName(), "game", game.displayName(), "bet", money,
                    "seconds", String.valueOf(game.inviteSeconds))));
            target.sendMessage(buttons(c.id));
        }
        refreshTables();
        return true;
    }

    /**
     * Tells other players about a new open challenge: games playable anywhere go to the whole server with a clickable
     * accept button; casino-only games only to players inside the casino, as plain text (they may be far from it).
     */
    private void announce(Challenge c, Player creator, String money) {
        if (!plugin.getConfig().getBoolean("broadcast.challenges", true)) return;
        if (c.machine != null) {
            // at a machine: only players in the casino, they can walk to it and sit down
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.equals(creator) || !plugin.access().inCasino(p)) continue;
                plugin.messages().send(p, "pvp.broadcast-machine", "player", creator.getName(), "game", c.game.displayName(),
                        "bet", money, "table", String.valueOf(plugin.machines().number(c.machine)),
                        "seats", plugin.machines().freeText(plugin.seats().freeSeats(c.machine, 2), 2));
            }
            return;
        }
        boolean everywhere = !c.game.casinoOnly();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(creator) || !p.hasPermission("boxcasino.game." + c.game.id())) continue;
            if (everywhere) {
                p.sendMessage(plugin.messages().text("prefix").append(plugin.messages().text("pvp.broadcast-open",
                        "player", creator.getName(), "game", c.game.displayName(), "bet", money, "table", String.valueOf(c.id)))
                        .append(Component.text(" "))
                        .append(plugin.messages().text("pvp.button-join").clickEvent(ClickEvent.runCommand("/casino pvp accept " + c.id))));
            } else if (plugin.access().inCasino(p)) {
                plugin.messages().send(p, "pvp.broadcast-casino", "player", creator.getName(), "game", c.game.displayName(),
                        "bet", money);
            }
        }
    }

    private Component buttons(int id) {
        return plugin.messages().text("pvp.button-accept").clickEvent(ClickEvent.runCommand("/casino pvp accept " + id))
                .append(Component.text("  "))
                .append(plugin.messages().text("pvp.button-decline").clickEvent(ClickEvent.runCommand("/casino pvp decline " + id)));
    }

    public void accept(Player player, int id, boolean npc) {
        Challenge c = challenges.get(id);
        if (c == null) {
            plugin.messages().send(player, "pvp.not-found");
            return;
        }
        if (c.creator.equals(player.getUniqueId())) {
            plugin.messages().send(player, "pvp.self");
            return;
        }
        if (c.target != null && !c.target.equals(player.getUniqueId())) {
            plugin.messages().send(player, "pvp.not-for-you");
            return;
        }
        if (c.machine != null && !plugin.seats().seatedAt(player.getUniqueId(), c.machine)) {
            plugin.messages().send(player, "pvp.machine-only");
            return;
        }
        if (c.machine == null && plugin.seats().of(player.getUniqueId()) != null) {
            plugin.messages().send(player, "pvp.seated-remote");
            return;
        }
        PvpGame game = c.game;
        if (!game.enabled()) {
            plugin.messages().send(player, "game.disabled");
            return;
        }
        if (!game.permitted(player) || !plugin.access().canPlay(player, game.casinoOnly(), npc)) return;
        if (matches.containsKey(player.getUniqueId())) {
            plugin.messages().send(player, "pvp.in-match");
            return;
        }
        Player creator = Bukkit.getPlayer(c.creator);
        if (creator == null) {
            plugin.messages().send(player, "pvp.creator-offline");
            return;
        }
        if (matches.containsKey(c.creator)) {
            plugin.messages().send(player, "pvp.creator-busy");
            return;
        }
        Bet bet = plugin.bets().place(player, game.id(), c.currency, c.amount(), null);
        if (bet == null) return;
        remove(c);
        // one match at a time: every other challenge of both players is refunded
        refundAllOf(c.creator);
        refundAllOf(player.getUniqueId());
        Match match = game.newMatch(creator, c.bet, player, bet);
        match.machine = c.machine;
        matches.put(c.creator, match);
        matches.put(player.getUniqueId(), match);
        match.start();
        match.open(player);
        boolean creatorBusy = creator.getOpenInventory().getTopInventory().getHolder() instanceof CasinoView v && v.busy();
        if (creatorBusy) {
            plugin.messages().send(creator, "pvp.accepted-resume", "player", player.getName(), "game", game.displayName());
        } else {
            match.open(creator);
        }
        refreshTables();
    }

    public void decline(Player player, int id) {
        Challenge c = challenges.get(id);
        if (c == null || c.target == null || !c.target.equals(player.getUniqueId())) {
            plugin.messages().send(player, "pvp.not-found");
            return;
        }
        remove(c);
        plugin.bets().refund(c.bet);
        plugin.messages().send(player, "pvp.declined");
        Player creator = Bukkit.getPlayer(c.creator);
        if (creator != null) plugin.messages().send(creator, "pvp.declined-creator", "player", player.getName());
        refreshTables();
    }

    public void cancel(Player player, int id) {
        Challenge c = challenges.get(id);
        if (c == null || !c.creator.equals(player.getUniqueId())) {
            plugin.messages().send(player, "pvp.not-found");
            return;
        }
        remove(c);
        plugin.bets().refund(c.bet);
        plugin.messages().send(player, "pvp.cancelled");
        refreshTables();
    }

    private void expire(Challenge c) {
        if (challenges.get(c.id) != c) return;
        challenges.remove(c.id);
        plugin.bets().refund(c.bet);
        Player creator = Bukkit.getPlayer(c.creator);
        if (creator != null) plugin.messages().send(creator, "pvp.expired", "game", c.game.displayName());
        refreshTables();
    }

    private void remove(Challenge c) {
        challenges.remove(c.id);
        if (c.expireTask != null) c.expireTask.cancel();
    }

    /** Stood up from a stool of a machine: the challenge there is cancelled, a running match there is lost. */
    public void leftMachine(Player player, String machine) {
        for (Challenge c : List.copyOf(challenges.values())) {
            if (c.creator.equals(player.getUniqueId()) && machine.equals(c.machine)) {
                remove(c);
                plugin.bets().refund(c.bet);
                if (player.isOnline()) plugin.messages().send(player, "pvp.cancelled-stand");
            }
        }
        Match m = matches.get(player.getUniqueId());
        if (m != null && machine.equals(m.machine)) m.forfeit(player.getUniqueId());
        refreshTables();
    }

    /** The creator left the server: their open challenges are refunded (no match against an empty seat). */
    public void quit(UUID player) {
        refundAllOf(player);
        refreshTables();
    }

    private void refundAllOf(UUID player) {
        for (Challenge c : List.copyOf(challenges.values())) {
            if (c.creator.equals(player)) {
                remove(c);
                plugin.bets().refund(c.bet);
            }
        }
    }

    void ended(Match match) {
        matches.values().removeIf(m -> m == match);
    }

    /** Plugin disable / reload of one game: challenges refunded, matches paid (if decided) or refunded. */
    void shutdown(PvpGame game) {
        for (Challenge c : List.copyOf(challenges.values())) {
            if (c.game != game) continue;
            remove(c);
            plugin.bets().refund(c.bet);
        }
        for (Match m : new HashSet<>(matches.values())) if (m.game() == game) m.shutdown();
        refreshTables();
    }

    public static long parseAmount(String s) {
        String t = s.toLowerCase(java.util.Locale.ROOT).replace(".", "").replace(",", "");
        long mul = 1;
        if (t.endsWith("k")) {
            mul = 1_000;
            t = t.substring(0, t.length() - 1);
        } else if (t.endsWith("m")) {
            mul = 1_000_000;
            t = t.substring(0, t.length() - 1);
        }
        try {
            long v = Long.parseLong(t);
            return v <= 0 || v > Long.MAX_VALUE / mul ? -1 : v * mul;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    static String left(Challenge c) {
        return Format.duration(Math.max(0, (c.expiresAt - System.currentTimeMillis()) / 1000));
    }
}
