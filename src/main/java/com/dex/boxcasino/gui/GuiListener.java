package com.dex.boxcasino.gui;

import com.dex.boxcasino.BoxCasinoPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;

/** Spec 2.3 / 2.4: nothing moves in or out of a casino GUI, one handled click per player per cooldown. */
public final class GuiListener implements Listener {

    private final BoxCasinoPlugin plugin;
    private final Map<UUID, Integer> lastClick = new HashMap<>();

    public GuiListener(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        InventoryView view = e.getView();
        if (!(view.getTopInventory().getHolder(false) instanceof CasinoView casino)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= view.getTopInventory().getSize()) return;
        int now = Bukkit.getCurrentTick();
        Integer last = lastClick.get(player.getUniqueId());
        if (last != null && now - last < plugin.settings().clickCooldownTicks) return;
        lastClick.put(player.getUniqueId(), now);
        if (casino.isClosed()) return;
        casino.onClick(raw, e.getClick());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof CasinoView) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder(false) instanceof CasinoView casino) casino.handleClose(e.getReason());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        lastClick.remove(e.getPlayer().getUniqueId());
        CasinoView.releaseHeld(e.getPlayer().getUniqueId());
        if (e.getPlayer().getOpenInventory().getTopInventory().getHolder(false) instanceof CasinoView casino) {
            casino.handleClose(InventoryCloseEvent.Reason.DISCONNECT);
        }
    }

    /** Plugin disable / reload: close every casino GUI so each game settles or refunds. */
    public static void closeAll() {
        CasinoView.releaseAllHeld();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof CasinoView casino) {
                casino.handleClose(InventoryCloseEvent.Reason.PLUGIN);
                p.closeInventory();
            }
        }
    }
}
