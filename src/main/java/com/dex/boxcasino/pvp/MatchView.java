package com.dex.boxcasino.pvp;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.gui.CasinoView;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** One player's window on a {@link Match}. */
public final class MatchView extends CasinoView {

    final Match match;
    final int seat;

    MatchView(BoxCasinoPlugin plugin, Player player, Match match, int seat) {
        super(plugin, player);
        this.match = match;
        this.seat = seat;
        var gs = match.game().settings();
        this.inventory = Bukkit.createInventory(this, gs.rows * 9, plugin.items().title(gs.title, gs.fallbackTitle));
    }

    public int seat() {
        return seat;
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (!match.finished()) match.click(seat, slot, click);
    }

    @Override
    public boolean busy() {
        return !match.finished();
    }

    /** At a machine in the casino the match must be finished: ESC brings the GUI back (Shift = stand up = lose). */
    @Override
    protected boolean holdOnEsc() {
        return match.machine != null && !match.finished();
    }

    @Override
    protected void onClose() {
        match.viewClosed(this);
        if (!match.finished() && match.machine == null) plugin.rooms().offerReturn(player);
    }
}
