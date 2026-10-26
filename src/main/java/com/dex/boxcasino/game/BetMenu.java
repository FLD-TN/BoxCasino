package com.dex.boxcasino.game;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.gui.CasinoView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongUnaryOperator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * Bet amount picker opened by the bet button of a game (texture shop_gui of the Vegas pack, 7x5 grid):
 * row 2 = −/+ steps around the current bet, row 3 = preset amounts (chip pictures), row 4 = min / half / double / max,
 * row 5 = done. Every button shows its amount, so no click type has to be learned. ESC also goes back to the game.
 */
final class BetMenu extends CasinoView {

    private static final int DISPLAY = 13;
    private static final int[] MINUS = {12, 11, 10};
    private static final int[] PLUS = {14, 15, 16};
    private static final int[] PRESETS = {19, 20, 21, 22, 23, 24, 25};
    private static final int MIN = 28, HALF = 30, DOUBLE = 32, MAX = 34;
    private static final int DONE = 40;
    /** The lock drawn on the right tab of the texture: an invisible button over it is "done" too. */
    private static final int DONE_TAB = 26;

    private final GameView view;
    private final Map<Integer, LongUnaryOperator> actions = new HashMap<>();

    BetMenu(BoxCasinoPlugin plugin, Player player, GameView view) {
        super(plugin, player);
        this.view = view;
        FileConfiguration c = plugin.getConfig();
        this.inventory = Bukkit.createInventory(this, 45,
                plugin.items().title(c.getString("bet-menu.title", ""), plugin.messages().raw("bet-menu.fallback-title")));
        render();
    }

    private ItemSpec spec(String key, Material fallback) {
        return ItemSpec.of(plugin.getConfig().get("bet-menu.items." + key), new ItemSpec("", fallback, 0));
    }

    private void render() {
        inventory.clear();
        actions.clear();
        Messages m = plugin.messages();
        Currency cur = view.currency();
        GameSettings.Limits limits = view.gs.limits.get(cur);
        long bet = view.amount();
        long balance = plugin.wallet().balance(player, cur);
        String[] ph = {
                "bet", fmt(bet), "currency", plugin.wallet().name(cur), "game", view.game.displayName(),
                "balance", balance < 0 ? "?" : fmt(balance), "min", fmt(limits.min()), "max", fmt(limits.max())
        };
        inventory.setItem(DISPLAY, view.chip(cur, bet, m.line("bet-menu.display.name", ph), m.lines("bet-menu.display.lore", ph)));

        List<Long> steps = plugin.getConfig().getLongList("bet-menu.steps." + cur.name());
        for (int i = 0; i < steps.size() && i < PLUS.length; i++) {
            long step = steps.get(i);
            button(MINUS[i], spec("minus", Material.RED_STAINED_GLASS_PANE), "bet-menu.minus", v -> v - step, "amount", fmt(step));
            button(PLUS[i], spec("plus", Material.LIME_STAINED_GLASS_PANE), "bet-menu.plus", v -> v + step, "amount", fmt(step));
        }

        List<Long> presets = plugin.getConfig().getLongList("bet-menu.presets." + cur.name()).stream()
                .filter(v -> v >= limits.min() && v <= limits.max()).limit(PRESETS.length).toList();
        int first = (PRESETS.length - presets.size()) / 2;
        for (int i = 0; i < presets.size(); i++) {
            long value = presets.get(i);
            String key = value == bet ? "bet-menu.preset-selected" : "bet-menu.preset";
            int slot = PRESETS[first + i];
            inventory.setItem(slot, view.chip(cur, value, m.line(key + ".name", "amount", fmt(value)),
                    m.lines(key + ".lore", "amount", fmt(value))));
            actions.put(slot, v -> value);
        }

        button(MIN, spec("min", Material.BLUE_DYE), "bet-menu.min", v -> limits.min(), "amount", fmt(limits.min()));
        button(HALF, spec("half", Material.ORANGE_DYE), "bet-menu.half", v -> v / 2, "amount", fmt(limits.clamp(bet / 2)));
        button(DOUBLE, spec("double", Material.YELLOW_DYE), "bet-menu.double", v -> v * 2, "amount", fmt(limits.clamp(bet * 2)));
        long max = balance >= limits.min() ? Math.min(limits.max(), balance) : limits.max();
        button(MAX, spec("max", Material.PURPLE_DYE), "bet-menu.max", v -> max, "amount", fmt(max));

        inventory.setItem(DONE, plugin.items().make(spec("done", Material.LIME_DYE), m.line("bet-menu.done.name", ph),
                m.lines("bet-menu.done.lore", ph)));
        inventory.setItem(DONE_TAB, plugin.items().make(spec("done-tab", Material.LIME_DYE), m.line("bet-menu.done.name", ph),
                m.lines("bet-menu.done.lore", ph)));
    }

    private void button(int slot, ItemSpec spec, String key, LongUnaryOperator action, String... ph) {
        inventory.setItem(slot, plugin.items().make(spec, plugin.messages().line(key + ".name", ph),
                plugin.messages().lines(key + ".lore", ph)));
        actions.put(slot, action);
    }

    private String fmt(long amount) {
        return plugin.wallet().format(view.currency(), amount);
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (slot == DONE || slot == DONE_TAB) {
            if (plugin.gameSounds()) player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
            back();
            return;
        }
        LongUnaryOperator action = actions.get(slot);
        if (action == null) return;
        if (view.locked()) {
            if (plugin.gameSounds()) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 1f);
            back();
            return;
        }
        long before = view.amount();
        view.setAmount(action.applyAsLong(before));
        boolean changed = view.amount() != before;
        if (plugin.gameSounds()) player.playSound(player.getLocation(), changed ? Sound.UI_BUTTON_CLICK : Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f,
                changed ? 1.2f : 1f);
        render();
    }
}
