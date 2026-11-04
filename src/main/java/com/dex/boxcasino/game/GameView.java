package com.dex.boxcasino.game;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.gui.CasinoView;
import com.dex.boxcasino.util.Format;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * A game GUI with the common bet panel (spec 3.2): bet button (opens {@link BetMenu}), currency button (switch
 * $ / Gem) and info item with rules and RTP.
 */
public abstract class GameView extends CasinoView {


    protected final Game game;
    protected final GameSettings gs;
    protected final boolean npc;
    protected final BetMemory.Entry memory;
    /** Bet panel slots: from the game config, or set by a view with its own layout (PvP lobby). */
    protected int betSlot;
    protected int currencySlot;
    protected int infoSlot;

    protected GameView(BoxCasinoPlugin plugin, Player player, Game game, boolean npc) {
        this(plugin, player, game, npc, plugin.items().title(game.settings().title, game.settings().fallbackTitle),
                game.settings().rows);
    }

    protected GameView(BoxCasinoPlugin plugin, Player player, Game game, boolean npc, Component title, int rows) {
        super(plugin, player);
        this.game = game;
        this.gs = game.settings();
        this.npc = npc;
        this.memory = plugin.betMemory().of(player.getUniqueId());
        this.betSlot = gs.betSlot;
        this.currencySlot = gs.currencySlot;
        this.infoSlot = gs.infoSlot;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    /** True while the bet must not change (a round is running). */
    protected abstract boolean locked();

    public Currency currency() {
        return memory.currency;
    }

    public long amount() {
        Currency c = currency();
        GameSettings.Limits l = gs.limits.get(c);
        return l.clamp(memory.amount(game.id(), c, l.min()));
    }

    /** Sets the remembered bet of this game and currency (clamped to the limits). */
    void setAmount(long amount) {
        memory.setAmount(game.id(), currency(), gs.limits.get(currency()).clamp(amount));
    }

    /** Handles the bet panel slots; returns true when {@code slot} was one of them. */
    protected boolean handlePanel(int slot, ClickType click) {
        if (slot == betSlot) {
            if (locked()) {
                reject();
                return true;
            }
            sound(Sound.UI_BUTTON_CLICK);
            openChild(new BetMenu(plugin, player, this));
            return true;
        }
        if (slot == currencySlot) {
            if (locked()) {
                reject();
                return true;
            }
            memory.currency = currency().other();
            sound(Sound.UI_BUTTON_CLICK);
            redraw();
            onCurrencyChanged();
            return true;
        }
        if (slot == infoSlot) return true;
        return false;
    }

    protected void onCurrencyChanged() {
    }

    /** Back from the bet menu: the bet panel and the play button show the new amount. */
    @Override
    protected void onResume() {
        redraw();
    }

    /** Full redraw after the bet or currency changed: every view showing the bet (play button, seat) overrides it. */
    protected void redraw() {
        renderPanel();
    }

    protected void renderPanel() {
        Messages m = plugin.messages();
        Currency c = currency();
        GameSettings.Limits l = gs.limits.get(c);
        long balance = plugin.wallet().balance(player, c);
        String[] ph = {
                "bet", plugin.wallet().format(c, amount()),
                "currency", plugin.wallet().name(c),
                "balance", balance < 0 ? "?" : plugin.wallet().format(c, balance),
                "other", plugin.wallet().name(c.other()),
                "min", plugin.wallet().format(c, l.min()),
                "max", plugin.wallet().format(c, l.max())
        };
        set(betSlot, gs.betItem, m.line("panel.bet.name", ph), m.lines("panel.bet.lore", ph));
        set(currencySlot, c == Currency.MONEY ? gs.currencyMoneyItem : gs.currencyGemItem,
                m.line("panel.currency.name", ph), m.lines("panel.currency.lore", ph));
        double rtp = game.rtp();
        String rtpText = Double.isNaN(rtp) ? m.raw("panel.rtp-rules") : Format.percent(rtp);
        set(infoSlot, gs.infoItem, m.line("games." + game.id() + ".info.name"),
                m.lines("games." + game.id() + ".info.lore", "rtp", rtpText, "tax", Format.decimal(game.settings().yml.getDouble("tax-percent", 0))));
    }

    /** The chip picture of the biggest denomination not above {@code amount}. */
    protected ItemStack chip(Currency c, long amount, Component name, List<Component> lore) {
        NavigableMap<Long, ItemSpec> chips = plugin.settings().chips.get(c);
        Map.Entry<Long, ItemSpec> e = chips.floorEntry(amount);
        if (e == null) e = chips.firstEntry();
        return plugin.items().make(e.getValue(), name, lore);
    }

    protected void set(int slot, ItemSpec spec, Component name, List<Component> lore) {
        if (slot < 0 || slot >= inventory.getSize() || spec.isNone()) return;
        inventory.setItem(slot, plugin.items().make(spec, name, lore));
    }

    /** Like {@link #set(int, ItemSpec, Component, List)}, with the enchantment glint when {@code glow}. */
    protected void set(int slot, ItemSpec spec, Component name, List<Component> lore, boolean glow) {
        if (slot < 0 || slot >= inventory.getSize() || spec.isNone()) return;
        ItemStack item = plugin.items().make(spec, name, lore);
        if (glow) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setEnchantmentGlintOverride(true);
                item.setItemMeta(meta);
            }
        }
        inventory.setItem(slot, item);
    }

    protected void set(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, item);
    }

    /** Access checks before a new round (region, alt, limits, game switched off). */
    protected boolean mayStart() {
        if (!game.enabled()) {
            plugin.messages().send(player, "game.disabled");
            return false;
        }
        return game.permitted(player) && plugin.access().canPlay(player, game.casinoOnly(), npc);
    }

    protected void sound(Sound sound) {
        if (plugin.gameSounds()) player.playSound(player.getLocation(), sound, 0.6f, 1f);
    }

    protected void reject() {
        sound(Sound.BLOCK_NOTE_BLOCK_BASS);
    }
}
