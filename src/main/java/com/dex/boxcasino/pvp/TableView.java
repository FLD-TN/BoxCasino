package com.dex.boxcasino.pvp;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.game.GameView;
import com.dex.boxcasino.gui.Heads;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * The PvP table before a match, on the game's own texture with the bet panel. Left seat = the viewer (open a
 * challenge / cancel it), right seat = an open challenge of someone else (accept it / look at the next one).
 * When the challenge is accepted both players move to the match on the same texture.
 * At a machine in the world ({@code machine} set) the table only shows the challenge of the player on the other stool.
 */
final class TableView extends GameView {

    private final PvpGame pvp;
    /** Machine where the viewer sits, null = remote table (menu / command). */
    private final String machine;
    /** Index of the other players' challenge shown on the right seat. */
    private int cursor;

    TableView(BoxCasinoPlugin plugin, Player player, PvpGame game, boolean npc, String machine) {
        super(plugin, player, game, npc);
        this.pvp = game;
        this.machine = machine;
        plugin.pvp().addTable(this);
        refresh();
    }

    @Override
    protected boolean locked() {
        return own() != null;
    }

    @Override
    protected void onClose() {
        plugin.pvp().removeTable(this);
    }

    private Challenge own() {
        return plugin.pvp().ownChallenge(pvp, player.getUniqueId());
    }

    /** Open challenges of other players visible to the viewer (direct invites to the viewer included). */
    private List<Challenge> others() {
        return plugin.pvp().challenges(pvp, player.getUniqueId(), machine).stream()
                .filter(c -> !c.creator.equals(player.getUniqueId())).toList();
    }

    private Challenge shown() {
        List<Challenge> list = others();
        if (list.isEmpty()) return null;
        cursor = Math.floorMod(cursor, list.size());
        return list.get(cursor);
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (handlePanel(slot, click)) {
            refresh();
            return;
        }
        PvpService service = plugin.pvp();
        if (slot == pvp.spotSlots[0]) {
            Challenge own = own();
            if (own == null && click.isLeftClick()) {
                if (service.create(player, pvp, null, currency(), amount(), npc, machine)) sound(Sound.BLOCK_NOTE_BLOCK_PLING);
                else reject();
            } else if (own != null && click.isRightClick()) {
                service.cancel(player, own.id);
            } else {
                reject();
            }
            return;
        }
        if (slot == pvp.spotSlots[1]) {
            Challenge c = shown();
            if (c == null) {
                reject();
            } else if (click.isRightClick()) {
                cursor++;
                sound(Sound.UI_BUTTON_CLICK);
                refresh();
            } else {
                service.accept(player, c.id, npc);
            }
        }
    }

    @Override
    protected void redraw() {
        refresh();
    }

    void refresh() {
        if (isClosed()) return;
        Messages m = plugin.messages();
        renderPanel();
        // left seat: the viewer
        head(pvp.headSlots[0], player.getUniqueId(), m.line("pvp.table.you", "player", player.getName()));
        Challenge own = own();
        if (own == null) {
            String bet = plugin.wallet().format(currency(), amount());
            set(pvp.spotSlots[0], chip(currency(), amount(), m.line("pvp.table.mine.name", "bet", bet),
                    m.lines("pvp.table.mine.lore", "bet", bet,
                            "prize", plugin.wallet().format(currency(), pvp.winnerPayout(amount())),
                            "tax", com.dex.boxcasino.util.Format.decimal(pvp.taxPercent()))));
        } else {
            String bet = plugin.wallet().format(own.currency, own.amount());
            set(pvp.spotSlots[0], chip(own.currency, own.amount(), m.line("pvp.table.waiting.name", "bet", bet),
                    m.lines("pvp.table.waiting.lore", "bet", bet, "time", PvpService.left(own),
                            "prize", plugin.wallet().format(own.currency, pvp.winnerPayout(own.amount())))));
        }
        // right seat: someone else's challenge
        Challenge c = shown();
        if (c == null) {
            inventory.setItem(pvp.headSlots[1], null);
            String none = machine == null ? "pvp.table.none" : "pvp.table.none-machine";
            set(pvp.spotSlots[1], pvp.spotItem, m.line(none + ".name"), m.lines(none + ".lore"));
        } else {
            String bet = plugin.wallet().format(c.currency, c.amount());
            head(pvp.headSlots[1], c.creator, m.line("pvp.table.opponent", "player", c.creatorName));
            set(pvp.spotSlots[1], chip(c.currency, c.amount(), m.line(c.target != null ? "pvp.table.offer.name-invite"
                            : "pvp.table.offer.name", "player", c.creatorName, "bet", bet),
                    m.lines("pvp.table.offer.lore", "bet", bet, "time", PvpService.left(c),
                            "prize", plugin.wallet().format(c.currency, pvp.winnerPayout(c.amount())),
                            "count", String.valueOf(others().size()), "index", String.valueOf(cursor + 1))));
        }
    }

    private void head(int slot, UUID owner, net.kyori.adventure.text.Component name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta skull) {
            Heads.apply(skull, owner);
            skull.displayName(name);
            head.setItemMeta(skull);
        }
        inventory.setItem(slot, head);
    }
}
