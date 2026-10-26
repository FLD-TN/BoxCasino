package com.dex.boxcasino.extra;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.game.xidach.XiDachGame;
import com.dex.boxcasino.pvp.PvpGame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

/**
 * Players sitting on machine chairs (see {@link MachineService}). Sitting opens the machine's game for that chair:
 * a one-player machine takes one player, a xì dách machine is its own table (chair n = seat n), a PvP machine is a
 * table for the two players on its stools only. ESC / E only closes the GUI (right-click the machine to open it again);
 * standing up (Shift, or leaving the server) leaves the table, and in a running PvP match at the machine it forfeits.
 */
public final class SeatService implements Listener {

    /** Where a player sits: machine id (its display entity), game, chair number and the entity they ride. */
    public record Seated(String machine, String game, int index, UUID vehicle) {
    }

    private final BoxCasinoPlugin plugin;
    private final Map<UUID, Seated> seated = new HashMap<>();

    public SeatService(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    public Seated of(UUID player) {
        return seated.get(player);
    }

    /** True while {@code player} sits on a chair of {@code machine}. */
    public boolean seatedAt(UUID player, String machine) {
        Seated s = seated.get(player);
        return s != null && s.machine().equals(machine);
    }

    /** Chairs of a machine nobody sits on. */
    public int freeSeats(String machine, int total) {
        int taken = 0;
        for (Seated s : seated.values()) if (s.machine().equals(machine)) taken++;
        return Math.max(0, total - taken);
    }

    private boolean taken(String machine, int index) {
        for (Seated s : seated.values()) if (s.machine().equals(machine) && s.index() == index) return true;
        return false;
    }

    /** The model of a machine was clicked: a random free chair (or the player's own chair again). */
    public void sitAnywhere(Player player, String machine, String game, Location near) {
        Seated mine = seated.get(player.getUniqueId());
        if (mine != null && mine.machine().equals(machine)) {
            openGame(player, mine);
            return;
        }
        int count = plugin.machines().seatCount(game);
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < count; i++) if (!taken(machine, i)) free.add(i);
        if (free.isEmpty()) {
            plugin.messages().send(player, count == 1 ? "seat.busy" : "seat.full");
            return;
        }
        sit(player, machine, game, free.get(plugin.random().nextInt(free.size())), near);
    }

    /** A chair was clicked: sit there and open the game. */
    public void sit(Player player, String machine, String gameId, int index, Location near) {
        Seated mine = seated.get(player.getUniqueId());
        if (mine != null) {
            if (mine.machine().equals(machine)) openGame(player, mine);
            else plugin.messages().send(player, "seat.already");
            return;
        }
        if (taken(machine, index)) {
            plugin.messages().send(player, plugin.machines().seatCount(gameId) == 1 ? "seat.busy" : "seat.taken");
            return;
        }
        Game game = plugin.games().get(gameId);
        if (game == null || !game.enabled()) {
            plugin.messages().send(player, "game.disabled");
            return;
        }
        if (plugin.access().busyElsewhere(player, gameId)) return;
        if (!game.permitted(player) || !plugin.access().canPlay(player, game.casinoOnly(), false)) return;
        if (game instanceof PvpGame && plugin.pvp().matchOf(player.getUniqueId()) != null) {
            plugin.messages().send(player, "pvp.in-match");
            return;
        }
        if (game instanceof XiDachGame xd && !xd.leaveOtherTables(player)) return;
        if (player.isInsideVehicle()) {
            plugin.messages().send(player, "seat.riding");
            return;
        }
        Entity vehicle = plugin.machines().seatEntity(near, machine, index);
        if (vehicle == null) {
            plugin.messages().send(player, "seat.broken");
            return;
        }
        Location at = vehicle.getLocation();
        player.teleport(new Location(at.getWorld(), at.getX(), at.getY(), at.getZ(), at.getYaw(), 10f));
        if (!vehicle.addPassenger(player)) {
            plugin.messages().send(player, "seat.broken");
            return;
        }
        Seated s = new Seated(machine, gameId, index, vehicle.getUniqueId());
        seated.put(player.getUniqueId(), s);
        plugin.machines().updateHologram(machine, at);
        player.sendActionBar(plugin.messages().text(game instanceof PvpGame ? "seat.hint-pvp" : "seat.hint"));
        openGame(player, s);
    }

    private void openGame(Player player, Seated s) {
        Game game = plugin.games().get(s.game());
        if (game == null || !game.enabled()) {
            plugin.messages().send(player, "game.disabled");
            return;
        }
        if (game instanceof XiDachGame xd) xd.openAtMachine(player, s.machine(), s.index());
        else if (game instanceof PvpGame pvp) pvp.openAtMachine(player, s.machine());
        else game.open(player, false);
    }

    /** Shift (or quit / teleport / death): the player leaves the chair and the table. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        Seated s = seated.get(player.getUniqueId());
        if (s == null || !s.vehicle().equals(e.getDismounted().getUniqueId())) return;
        standUp(player, s, e.getDismounted().getLocation());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        Seated s = seated.get(e.getPlayer().getUniqueId());
        if (s == null) return;
        Entity vehicle = Bukkit.getEntity(s.vehicle());
        standUp(e.getPlayer(), s, null);
        if (vehicle != null) vehicle.removePassenger(e.getPlayer());
    }

    private void standUp(Player player, Seated s, Location seat) {
        seated.remove(player.getUniqueId());
        com.dex.boxcasino.gui.CasinoView.releaseHeld(player.getUniqueId());
        Entity vehicleEntity = Bukkit.getEntity(s.vehicle());
        if (vehicleEntity != null) plugin.machines().updateHologram(s.machine(), vehicleEntity.getLocation());
        Game game = plugin.games().get(s.game());
        if (game instanceof XiDachGame xd) xd.leftMachine(player.getUniqueId(), s.machine());
        else if (game instanceof PvpGame) plugin.pvp().leftMachine(player, s.machine());
        if (seat == null || !player.isOnline()) return;
        // step back from the chair (away from the machine) so the player does not stand inside it
        Vector back = seat.getDirection().setY(0).normalize().multiply(-0.8);
        Location out = seat.clone().add(back);
        out.setY(Math.floor(seat.getY()) + 0.01);
        out.setYaw(seat.getYaw());
        out.setPitch(player.getLocation().getPitch());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !player.isInsideVehicle()) player.teleport(out);
        });
    }

    /** Plugin disable: everybody stands up (tables are shut down by their games). */
    public void shutdown() {
        for (UUID id : List.copyOf(seated.keySet())) {
            Seated s = seated.remove(id);
            Entity vehicle = Bukkit.getEntity(s.vehicle());
            Player p = Bukkit.getPlayer(id);
            if (vehicle != null && p != null) vehicle.removePassenger(p);
        }
    }
}
