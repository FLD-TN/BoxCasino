package com.dex.boxcasino.game.roulette;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.gui.CasinoView;
import com.dex.boxcasino.util.Format;
import com.dex.boxcasino.util.Payouts;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The betting board over a Roulette table (6 rows): numbers 1-36 in rows 1-4, zero and the outside bets in row 5,
 * tools in row 6. Left click lays one chip, right click takes one off. ESC / back returns to the wheel.
 */
final class RouletteBoard extends CasinoView {

    /** Row 5: zero, then red, black, even, odd, 1-18, 19-36, dozen 1, dozen 2. Row 6 starts with dozen 3. */
    private static final int ZERO = 36;
    private static final int DOZEN3 = 45;
    private static final int CHIP = 47, UNDO = 48, CLEAR = 49, SPIN = 51, BACK = 53;

    private final RouletteView view;
    private final RouletteGame game;

    RouletteBoard(BoxCasinoPlugin plugin, Player player, RouletteView view) {
        super(plugin, player);
        this.view = view;
        this.game = view.game();
        this.inventory = Bukkit.createInventory(this, 54,
                plugin.items().title(game.boardTitle, plugin.messages().raw("games.roulette.board.title")));
        render();
    }

    /** Spot of a board slot, or null. */
    private String spotAt(int slot) {
        if (slot >= 0 && slot < 36) return String.valueOf(slot + 1);
        if (slot == ZERO) return "0";
        if (slot > ZERO && slot < 45) return RouletteGame.OUTSIDE.get(slot - ZERO - 1);
        if (slot == DOZEN3) return "dozen3";
        return null;
    }

    private int slotOf(String spot) {
        if (RouletteGame.isNumber(spot)) {
            int n = Integer.parseInt(spot);
            return n == 0 ? ZERO : n - 1;
        }
        return spot.equals("dozen3") ? DOZEN3 : ZERO + 1 + RouletteGame.OUTSIDE.indexOf(spot);
    }

    @Override
    public void onClick(int slot, ClickType click) {
        String spot = spotAt(slot);
        if (spot != null) {
            boolean ok = click.isRightClick() ? view.takeOff(spot) : view.lay(spot);
            sound(ok ? (click.isRightClick() ? Sound.UI_BUTTON_CLICK : Sound.BLOCK_NOTE_BLOCK_HAT) : Sound.BLOCK_NOTE_BLOCK_BASS,
                    click.isRightClick() ? 0.8f : 1.4f);
            if (ok) render();
            return;
        }
        switch (slot) {
            case CHIP -> {
                sound(Sound.UI_BUTTON_CLICK, 1f);
                view.openChipMenu(this);
            }
            case UNDO -> {
                boolean ok = view.undo();
                sound(ok ? Sound.UI_BUTTON_CLICK : Sound.BLOCK_NOTE_BLOCK_BASS, 1f);
                if (ok) render();
            }
            case CLEAR -> {
                boolean ok = view.clear();
                sound(ok ? Sound.UI_BUTTON_CLICK : Sound.BLOCK_NOTE_BLOCK_BASS, 1f);
                if (ok) render();
            }
            case SPIN -> {
                if (view.stakes.isEmpty()) {
                    plugin.messages().send(player, "games.roulette.no-stakes");
                    sound(Sound.BLOCK_NOTE_BLOCK_BASS, 1f);
                    return;
                }
                back();
                view.spin();
            }
            case BACK -> {
                sound(Sound.UI_BUTTON_CLICK, 1f);
                back();
            }
            default -> {
            }
        }
    }

    /** Back from the chip menu: chip size changed. */
    @Override
    protected void onResume() {
        render();
    }

    private void render() {
        inventory.clear();
        Messages m = plugin.messages();
        Currency c = view.currency();
        String chip = plugin.wallet().format(c, view.amount());
        for (int n = 0; n <= 36; n++) {
            String spot = String.valueOf(n);
            put(slotOf(spot), game.numberItems[n], spot, m.line("games.roulette.board.number.name", "number", spot,
                    "color", view.colorName(n)), c, chip);
        }
        for (String spot : RouletteGame.OUTSIDE) {
            put(slotOf(spot), game.outsideItems.get(spot), spot, m.line("games.roulette.board.outside.name",
                    "spot", view.spotName(spot)), c, chip);
        }
        String total = plugin.wallet().format(c, view.total());
        button(CHIP, null, m.line("games.roulette.board.chip.name", "chip", chip), m.lines("games.roulette.board.chip.lore", "chip", chip));
        button(UNDO, game.undoItem, m.line("games.roulette.board.undo.name"), m.lines("games.roulette.board.undo.lore"));
        button(CLEAR, game.clearItem, m.line("games.roulette.clear.name"), m.lines("games.roulette.clear.lore"));
        List<Component> spinLore = new ArrayList<>(view.stakeLines(view.stakes, c));
        spinLore.addAll(m.lines(view.stakes.isEmpty() ? "games.roulette.lever-empty.lore" : "games.roulette.lever.lore", "total", total));
        button(SPIN, game.spinItem, m.line(view.stakes.isEmpty() ? "games.roulette.lever-empty.name" : "games.roulette.lever.name",
                "total", total), spinLore);
        button(BACK, game.backItem, m.line("games.roulette.board.back.name"), List.of());
    }

    /** One spot: glows when chips are on it; lore shows the stake and what it pays. */
    private void put(int slot, ItemSpec spec, String spot, Component name, Currency c, String chip) {
        Messages m = plugin.messages();
        long stake = view.stakes.getOrDefault(spot, 0L);
        double mul = game.multiplier(spot);
        List<Component> lore = new ArrayList<>(m.lines("games.roulette.board.spot-lore." + (RouletteGame.isNumber(spot) ? "number" : spot),
                "multiplier", Format.decimal(mul)));
        if (stake > 0) {
            lore.addAll(m.lines("games.roulette.board.staked", "amount", plugin.wallet().format(c, stake),
                    "payout", plugin.wallet().format(c, Payouts.payout(stake, mul))));
        } else {
            lore.addAll(m.lines("games.roulette.board.empty", "multiplier", Format.decimal(mul)));
        }
        lore.addAll(m.lines(stake > 0 ? "games.roulette.board.hint-staked" : "games.roulette.board.hint", "chip", chip));
        ItemStack item = plugin.items().make(spec, name, lore);
        if (stake > 0) {
            ItemMeta meta = item.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    private void button(int slot, ItemSpec spec, Component name, List<Component> lore) {
        ItemStack item;
        if (spec == null) {
            // chip picture of the current chip size
            var chips = plugin.settings().chips.get(view.currency());
            var e = chips.floorEntry(view.amount());
            if (e == null) e = chips.firstEntry();
            item = plugin.items().make(e.getValue(), name, lore);
        } else {
            item = plugin.items().make(spec, name, lore);
        }
        inventory.setItem(slot, item);
    }

    private void sound(Sound sound, float pitch) {
        if (plugin.gameSounds()) player.playSound(player.getLocation(), sound, 0.6f, pitch);
    }
}
