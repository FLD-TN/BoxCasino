package com.dex.boxcasino.pvp;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.gui.CasinoView;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** "Create challenge" from the lobby: pick money or gem, then the amount is typed in chat ({@link ChallengePrompt}). */
final class CurrencyPickView extends CasinoView {

    private static final int MONEY = 11, GEM = 15, BACK = 22;

    private final PvpGame game;

    CurrencyPickView(BoxCasinoPlugin plugin, Player player, PvpGame game) {
        super(plugin, player);
        this.game = game;
        Messages m = plugin.messages();
        this.inventory = Bukkit.createInventory(this, 27, m.text("pvp.lobby.pick.title", "game", game.displayName()));
        ItemStack filler = plain(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler);
        inventory.setItem(MONEY, currency(Currency.MONEY, game.settings().currencyMoneyItem));
        inventory.setItem(GEM, currency(Currency.GEM, game.settings().currencyGemItem));
        inventory.setItem(BACK, plain(Material.ARROW, m.line("pvp.lobby.pick.back.name"), m.lines("pvp.lobby.pick.back.lore")));
    }

    private ItemStack currency(Currency c, com.dex.boxcasino.config.ItemSpec look) {
        var limits = game.settings().limits.get(c);
        long balance = plugin.wallet().balance(player, c);
        String[] ph = {
                "currency", plugin.wallet().name(c),
                "balance", balance < 0 ? "?" : plugin.wallet().format(c, balance),
                "min", plugin.wallet().format(c, limits.min()), "max", plugin.wallet().format(c, limits.max())
        };
        return plugin.items().make(look.isNone() ? new com.dex.boxcasino.config.ItemSpec("",
                        c == Currency.MONEY ? Material.GOLD_INGOT : Material.AMETHYST_SHARD, 0) : look,
                plugin.messages().line("pvp.lobby.pick.currency.name", ph), plugin.messages().lines("pvp.lobby.pick.currency.lore", ph));
    }

    private static ItemStack plain(Material material, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    @Override
    public void onClick(int slot, ClickType click) {
        Currency c = slot == MONEY ? Currency.MONEY : slot == GEM ? Currency.GEM : null;
        if (slot == BACK) {
            game.open(player, false);
            return;
        }
        if (c == null) return;
        if (plugin.gameSounds()) player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
        player.closeInventory();
        plugin.challengePrompt().ask(player, game, c);
    }
}
