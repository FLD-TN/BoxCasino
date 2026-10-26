package com.dex.boxcasino.game.races;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.game.GameView;
import com.dex.boxcasino.util.Format;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** One viewer of the shared race. Closing it never touches the bet: the race settles it. */
final class RacesView extends GameView {

    private final RacesGame races;
    private final RaceTable table;
    private int selected = -1;

    RacesView(BoxCasinoPlugin plugin, Player player, RacesGame game, boolean npc) {
        super(plugin, player, game, npc);
        this.races = game;
        this.table = game.table();
        RaceTable.Entry mine = table.betOf(player.getUniqueId());
        if (mine != null) selected = mine.horse();
        table.join(this);
        refresh();
    }

    private RaceTable.Entry mine() {
        return table.betOf(player.getUniqueId());
    }

    @Override
    protected boolean locked() {
        return mine() != null;
    }

    @Override
    public boolean busy() {
        return mine() != null;
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (handlePanel(slot, click)) return;
        int lane = races.laneSlots.indexOf(slot);
        if (lane >= 0) {
            if (mine() != null || !table.acceptsBets()) {
                reject();
                return;
            }
            selected = lane;
            sound(Sound.UI_BUTTON_CLICK);
            refresh();
            return;
        }
        if (slot != races.spotSlot) return;
        RaceTable.Entry mine = mine();
        if (mine != null) {
            if (click.isRightClick() && table.cancel(player)) {
                plugin.messages().send(player, "games.races.cancelled");
            } else {
                reject();
            }
            return;
        }
        if (!table.acceptsBets()) {
            plugin.messages().send(player, "games.races.wait");
            reject();
            return;
        }
        if (selected < 0) {
            plugin.messages().send(player, "games.races.choose-first");
            reject();
            return;
        }
        if (!mayStart()) {
            reject();
            return;
        }
        if (table.place(player, selected, currency(), amount())) {
            sound(Sound.BLOCK_NOTE_BLOCK_PLING);
            plugin.messages().send(player, "games.races.placed", "horse", String.valueOf(selected + 1),
                    "bet", plugin.wallet().format(currency(), amount()));
        } else {
            reject();
        }
    }

    @Override
    protected void onClose() {
        table.leave(this);
        if (busy()) plugin.rooms().offerReturn(player);
    }

    @Override
    protected void redraw() {
        refresh();
    }

    /** Full redraw (called by the table on every change). */
    void refresh() {
        if (isClosed()) return;
        renderPanel();
        renderLanes();
        renderSpot();
        refreshLever();
    }

    private void renderLanes() {
        Messages m = plugin.messages();
        RaceTable.Phase phase = table.phase();
        RaceTable.Entry mine = mine();
        for (RacesGame.Horse h : races.horses) {
            int lane = races.laneSlots.get(h.index());
            int pos = table.position(h.index());
            for (int col = 0; col <= races.finishColumn; col++) {
                if (col != pos) inventory.setItem(lane + col, null);
            }
            ItemSpec look;
            if (phase == RaceTable.Phase.RACING) look = h.running();
            else if (phase == RaceTable.Phase.RESULT) look = h.index() == table.winner() ? h.selected() : h.running();
            else if (h.index() == selected) look = h.selected();
            else look = mine == null ? h.choose() : h.idle();
            set(lane + pos, look, m.line("games.races.horse.name", "horse", String.valueOf(h.index() + 1)),
                    m.lines(phase == RaceTable.Phase.RESULT && h.index() == table.winner() ? "games.races.horse.lore-winner"
                                    : "games.races.horse.lore",
                            "bettors", String.valueOf(table.bettors(h.index())),
                            "multiplier", Format.decimal(races.multiplier)));
        }
        for (int i = 0; i < races.flagSlots.size(); i++) {
            set(races.flagSlots.get(i), i % 2 == 0 ? races.flagA : races.flagB, m.line("games.races.finish.name"), List.of());
        }
    }

    private void renderSpot() {
        Messages m = plugin.messages();
        RaceTable.Entry mine = mine();
        if (mine != null) {
            set(races.spotSlot, chip(mine.bet().currency, mine.bet().amount(), m.line("games.races.spot-bet.name",
                            "horse", String.valueOf(mine.horse() + 1), "bet", plugin.wallet().format(mine.bet().currency, mine.bet().amount())),
                    m.lines(table.phase() == RaceTable.Phase.BETTING ? "games.races.spot-bet.lore-cancel" : "games.races.spot-bet.lore",
                            "multiplier", Format.decimal(races.multiplier))));
        } else if (selected < 0) {
            set(races.spotSlot, races.spotItem, m.line("games.races.spot-choose.name"), m.lines("games.races.spot-choose.lore"));
        } else {
            set(races.spotSlot, races.spotItem, m.line("games.races.spot.name", "horse", String.valueOf(selected + 1)),
                    m.lines("games.races.spot.lore", "bet", plugin.wallet().format(currency(), amount()),
                            "multiplier", Format.decimal(races.multiplier)));
        }
    }

    void refreshLever() {
        if (isClosed()) return;
        Messages m = plugin.messages();
        RaceTable.Phase phase = table.phase();
        int left = table.secondsLeft();
        ItemSpec look = switch (phase) {
            case IDLE -> selected < 0 ? races.leverIdle : races.leverChoose;
            case BETTING -> left <= races.countdown.size() && left > 0 ? races.countdown.get(left - 1) : races.leverBetting;
            case RACING -> races.leverRacing;
            case RESULT -> races.leverEnd;
        };
        String key = switch (phase) {
            case IDLE -> "games.races.lever-idle";
            case BETTING -> "games.races.lever-betting";
            case RACING -> "games.races.lever-racing";
            case RESULT -> "games.races.lever-result";
        };
        List<Component> lore = m.lines(key + ".lore", "seconds", String.valueOf(left));
        for (int slot : races.leverSlots) {
            set(slot, look, m.line(key + ".name", "seconds", String.valueOf(left), "winner", String.valueOf(table.winner() + 1)), lore);
        }
    }
}
