package com.dex.boxcasino.gui;

import com.dex.boxcasino.BoxCasinoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Every BoxCasino GUI. Identified by this holder, never by its title (spec 2.2). {@link GuiListener} cancels every
 * click and drag while one is open and forwards top-inventory clicks to {@link #onClick}.
 */
public abstract class CasinoView implements InventoryHolder {

    protected final BoxCasinoPlugin plugin;
    protected final Player player;
    protected Inventory inventory;
    private boolean closed;
    /** Set on a small menu opened on top of another GUI (bet menu over a game): ESC / "done" go back to it. */
    private CasinoView parent;
    /** True on the GUI under an open child menu: it stays alive (round, seat, timers) while hidden. */
    private boolean suspended;
    private boolean returning;
    /** GUIs closed with ESC while they must stay open; they come back a few ticks later. */
    private static final java.util.Map<java.util.UUID, CasinoView> HELD = new java.util.HashMap<>();

    protected CasinoView(BoxCasinoPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player player() {
        return player;
    }

    public void open() {
        closed = false;
        player.openInventory(inventory);
    }

    /** A click on a top-inventory slot (already cancelled and rate-limited). */
    public abstract void onClick(int slot, ClickType click);

    /** True while a round is running that must not be interrupted (play-time limit waits for it). */
    public boolean busy() {
        return false;
    }

    /** Called once when the GUI closes for any reason (close, quit, kick, plugin disable). */
    protected void onClose() {
    }

    /** Opens {@code child} on top of this GUI; this one is not closed meanwhile. */
    public void openChild(CasinoView child) {
        child.parent = this;
        suspended = true;
        child.open();
    }

    /** Child menu: back to the GUI under it (redrawn by {@link #onResume}). Plain GUI: just closes. */
    protected void back() {
        if (parent == null) {
            player.closeInventory();
            return;
        }
        returning = true;
        resumeParent();
    }

    private void resumeParent() {
        CasinoView p = parent;
        p.suspended = false;
        p.onResume();
        player.openInventory(p.inventory);
    }

    /**
     * True while ESC must not close this GUI (a ball still falling, a match at a machine): the GUI opens again a few
     * ticks later. Standing up from the chair in that moment ({@link #releaseHeld}) ends it for good.
     */
    protected boolean holdOnEsc() {
        return false;
    }

    /** Stood up / left the server / reload: a GUI held after ESC is closed for good now (its round settles). */
    public static void releaseHeld(java.util.UUID player) {
        CasinoView v = HELD.remove(player);
        if (v != null) v.handleClose(InventoryCloseEvent.Reason.PLUGIN);
    }

    static void releaseAllHeld() {
        for (java.util.UUID id : java.util.List.copyOf(HELD.keySet())) releaseHeld(id);
    }

    /** Called when this GUI shows again after a child menu. */
    protected void onResume() {
    }

    /**
     * ESC on a child menu goes back to the GUI under it; any other close (play limit, quit, reload, another GUI
     * opened by a plugin) closes both.
     */
    final void handleClose(InventoryCloseEvent.Reason reason) {
        if (suspended || closed) return;
        if (reason == InventoryCloseEvent.Reason.PLAYER && holdOnEsc() && plugin.isEnabled() && player.isOnline()) {
            HELD.put(player.getUniqueId(), this);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (HELD.get(player.getUniqueId()) != this) return;
                HELD.remove(player.getUniqueId());
                if (player.isOnline() && holdOnEsc()
                        && !(player.getOpenInventory().getTopInventory().getHolder(false) instanceof CasinoView)) {
                    player.openInventory(inventory);
                } else {
                    handleClose(player.isOnline() ? InventoryCloseEvent.Reason.PLUGIN : InventoryCloseEvent.Reason.DISCONNECT);
                }
            }, Math.max(1, plugin.getConfig().getLong("esc-reopen-ticks", 8)));
            return;
        }
        closed = true;
        onClose();
        if (parent == null || returning) return;
        returning = true;
        if (reason == InventoryCloseEvent.Reason.PLAYER && player.isOnline() && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && !(player.getOpenInventory().getTopInventory().getHolder(false) instanceof CasinoView)) {
                    resumeParent();
                } else {
                    parent.suspended = false;
                    parent.handleClose(InventoryCloseEvent.Reason.PLUGIN);
                }
            });
        } else {
            parent.suspended = false;
            parent.handleClose(reason);
        }
    }

    public boolean isClosed() {
        return closed;
    }
}
