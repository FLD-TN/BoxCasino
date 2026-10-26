package com.dex.boxcasino.pvp;

import com.dex.boxcasino.gui.Heads;
import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.chat.ChatRoom;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.util.Format;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

/**
 * One running 1v1. Seat 0 = challenge creator (left), seat 1 = acceptor (right). Both seats see the same board.
 * Closing a view never forfeits: timers keep running and auto-moves apply (spec 7.7). Main thread only.
 */
public abstract class Match implements ChatRoom {

    protected final BoxCasinoPlugin plugin;
    protected final PvpGame game;
    protected final UUID[] ids = new UUID[2];
    protected final String[] names = new String[2];
    protected final Bet[] bets = new Bet[2];
    protected final List<MatchView> views = new ArrayList<>();
    protected BukkitTask task;
    /** -2 = running, -1 = draw, 0/1 = winner seat. */
    protected int result = -2;
    /** Machine in the world where both players sit, null = remote match. */
    String machine;

    protected Match(BoxCasinoPlugin plugin, PvpGame game, Player creator, Bet creatorBet, Player acceptor, Bet acceptorBet) {
        this.plugin = plugin;
        this.game = game;
        ids[0] = creator.getUniqueId();
        ids[1] = acceptor.getUniqueId();
        names[0] = creator.getName();
        names[1] = acceptor.getName();
        bets[0] = creatorBet;
        bets[1] = acceptorBet;
    }

    public PvpGame game() {
        return game;
    }

    public boolean finished() {
        return result != -2;
    }

    public int seatOf(UUID player) {
        return ids[0].equals(player) ? 0 : ids[1].equals(player) ? 1 : -1;
    }

    /** Starts timers; views are opened by the service. */
    protected abstract void start();

    /** A click of {@code seat} on a top slot while the match runs. */
    protected abstract void click(int seat, int slot, ClickType type);

    /** Draws the whole board for one viewer. */
    protected abstract void render(MatchView view);

    /** True when the result is already fixed (plugin shutdown pays it instead of refunding). */
    protected boolean decided() {
        return false;
    }

    /** The decided result, only called when {@link #decided()} is true. */
    protected int decidedResult() {
        return -1;
    }

    public void open(Player player) {
        int seat = seatOf(player.getUniqueId());
        if (seat < 0) return;
        MatchView view = new MatchView(plugin, player, this, seat);
        views.add(view);
        render(view);
        view.open();
    }

    void viewClosed(MatchView view) {
        views.remove(view);
    }

    @Override
    public java.util.Collection<UUID> members() {
        return List.of(ids);
    }

    @Override
    public void refreshViews() {
        renderAll();
    }

    @Override
    public void reopen(Player player) {
        open(player);
    }

    protected void renderAll() {
        for (MatchView v : List.copyOf(views)) {
            if (!v.isClosed()) render(v);
        }
    }

    protected void sound(Sound sound) {
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && plugin.gameSounds()) p.playSound(p.getLocation(), sound, 0.6f, 1f);
        }
    }

    protected void tell(String path, String... ph) {
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.messages().send(p, path, ph);
        }
    }

    /** Ends the match: winner gets the pot minus tax, a draw refunds both (as a settled draw, no tax). */
    protected void finish(int winner) {
        if (finished()) return;
        result = winner;
        if (task != null) {
            task.cancel();
            task = null;
        }
        long stake = bets[0].amount();
        String money = plugin.wallet().format(bets[0].currency, stake);
        if (winner < 0) {
            plugin.bets().settle(bets[0], bets[0].amount());
            plugin.bets().settle(bets[1], bets[1].amount());
            tell("pvp.draw", "game", game.displayName(), "bet", money);
        } else {
            long payout = game.winnerPayout(stake);
            plugin.bets().settle(bets[winner], payout);
            plugin.bets().settle(bets[1 - winner], 0);
            String prize = plugin.wallet().format(bets[0].currency, payout);
            Player w = Bukkit.getPlayer(ids[winner]);
            Player l = Bukkit.getPlayer(ids[1 - winner]);
            if (w != null) plugin.messages().send(w, "pvp.won", "game", game.displayName(), "opponent", names[1 - winner], "payout", prize);
            if (l != null) plugin.messages().send(l, "pvp.lost", "game", game.displayName(), "opponent", names[winner], "bet", money);
        }
        sound(Sound.ENTITY_PLAYER_LEVELUP);
        plugin.pvp().ended(this);
        renderAll();
        if (machine != null) {
            // still seated: back to the machine's table for the next round
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (UUID id : ids) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && plugin.seats().seatedAt(id, machine)
                            && p.getOpenInventory().getTopInventory().getHolder(false) instanceof MatchView v && v.match == this) {
                        game.openAtMachine(p, machine);
                    }
                }
            }, 60L);
        }
    }

    /** Stood up from the stool during the match: the other player wins. */
    void forfeit(UUID player) {
        int seat = seatOf(player);
        if (seat < 0 || finished()) return;
        tell("pvp.forfeit", "player", names[seat], "game", game.displayName());
        finish(1 - seat);
    }

    /** Plugin disable / reload. */
    void shutdown() {
        if (finished()) return;
        if (decided()) {
            finish(decidedResult());
            return;
        }
        if (task != null) task.cancel();
        result = -1;
        plugin.bets().refund(bets[0]);
        plugin.bets().refund(bets[1]);
        tell("pvp.cancelled-shutdown", "game", game.displayName());
        plugin.pvp().ended(this);
    }

    // --- shared drawing ---

    protected void set(MatchView view, int slot, ItemSpec spec, Component name, List<Component> lore) {
        if (slot < 0 || slot >= view.getInventory().getSize() || spec.isNone()) return;
        view.getInventory().setItem(slot, plugin.items().make(spec, name, lore));
    }

    protected void clear(MatchView view, int slot) {
        if (slot >= 0 && slot < view.getInventory().getSize()) view.getInventory().setItem(slot, null);
    }

    /** Player heads, bet chips (or the final status) on both seats. */
    protected void renderSeats(MatchView view, String extraLorePath0, String extraLorePath1) {
        Messages m = plugin.messages();
        for (int seat = 0; seat < 2; seat++) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta skull) {
                Heads.apply(skull, ids[seat]);
                skull.displayName(m.line(seat == view.seat ? "pvp.head.you" : "pvp.head.opponent", "player", names[seat]));
                String extra = seat == 0 ? extraLorePath0 : extraLorePath1;
                skull.lore(extra == null ? List.of() : m.lines(extra));
                head.setItemMeta(skull);
            }
            view.getInventory().setItem(game.headSlots[seat], head);
            String bet = plugin.wallet().format(bets[seat].currency, bets[seat].amount());
            if (finished()) {
                ItemSpec look = result < 0 ? game.drawItem : result == seat ? game.victoryItem : game.defeatItem;
                String key = result < 0 ? "pvp.status.draw" : result == seat ? "pvp.status.won" : "pvp.status.lost";
                set(view, game.spotSlots[seat], look, m.line(key, "player", names[seat]),
                        m.lines("pvp.status.lore", "bet", bet));
            } else {
                view.getInventory().setItem(game.spotSlots[seat], chipItem(seat, m.line("pvp.seat.name", "player", names[seat], "bet", bet)));
            }
        }
    }



    protected ItemStack chipItem(int seat, Component name) {
        var chips = plugin.settings().chips.get(bets[seat].currency);
        var e = chips.floorEntry(bets[seat].amount());
        if (e == null) e = chips.firstEntry();
        return plugin.items().make(e.getValue(), name, List.of(plugin.messages().line("pvp.seat.lore",
                "prize", plugin.wallet().format(bets[seat].currency, game.winnerPayout(bets[seat].amount())),
                "tax", Format.decimal(game.taxPercent()))));
    }
}
