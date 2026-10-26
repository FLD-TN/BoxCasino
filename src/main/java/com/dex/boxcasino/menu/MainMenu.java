package com.dex.boxcasino.menu;

import com.dex.boxcasino.gui.Heads;
import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.gui.CasinoView;
import com.dex.boxcasino.util.Format;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** /casino: game icons on the main_gui texture, player info, stats and self-exclusion buttons (spec 5.4). */
public final class MainMenu extends CasinoView {

    private final boolean npc;
    private final Map<Integer, Game> gameSlots = new HashMap<>();
    private final int statsSlot;
    private final int excludeSlot;
    private final int leaderboardSlot;

    public MainMenu(BoxCasinoPlugin plugin, Player player, boolean npc) {
        super(plugin, player);
        this.npc = npc;
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("menu");
        int rows = c == null ? 6 : Math.max(1, Math.min(6, c.getInt("rows", 6)));
        this.inventory = Bukkit.createInventory(this, rows * 9, plugin.items().title(
                c == null ? "" : c.getString("title", ""), c == null ? "&8Sòng Bạc" : c.getString("fallback-title", "&8Sòng Bạc")));
        this.statsSlot = c == null ? -1 : c.getInt("stats.slot", -1);
        this.excludeSlot = c == null ? -1 : c.getInt("exclude.slot", -1);
        this.leaderboardSlot = c == null ? -1 : c.getInt("leaderboard.slot", -1);
        render(c);
    }

    private void render(ConfigurationSection c) {
        if (c == null) return;
        Messages m = plugin.messages();
        for (Game game : plugin.games().all()) {
            ConfigurationSection g = gameEntry(c, game.id());
            if (g == null) continue;
            int slot = g.getInt("slot", -1);
            if (slot < 0 || slot >= inventory.getSize()) continue;
            if (gameSlots.containsKey(slot)) {
                plugin.getLogger().warning("menu.games." + game.id() + ".slot " + slot + " is already used by "
                        + gameSlots.get(slot).id() + " in config.yml: " + game.id() + " is not shown in /casino");
                continue;
            }
            double rtp = game.rtp();
            String rtpText = Double.isNaN(rtp) ? m.raw("panel.rtp-rules") : Format.percent(rtp);
            String path = game.enabled() ? "menu.game" : "menu.game-disabled";
            inventory.setItem(slot, plugin.items().make(ItemSpec.of(g.getValues(false), ItemSpec.NONE),
                    m.line(path + ".name", "game", game.displayName()),
                    m.lines(path + ".lore", "rtp", rtpText, "description", m.raw("games." + game.id() + ".description"))));
            gameSlots.put(slot, game);
        }
        if (statsSlot >= 0) {
            inventory.setItem(statsSlot, plugin.items().make(ItemSpec.of(c.get("stats"), ItemSpec.NONE),
                    m.line("menu.stats.name"), m.lines("menu.stats.lore")));
        }
        if (excludeSlot >= 0) {
            inventory.setItem(excludeSlot, plugin.items().make(ItemSpec.of(c.get("exclude"), ItemSpec.NONE),
                    m.line("menu.exclude.name"), m.lines("menu.exclude.lore")));
        }
        if (leaderboardSlot >= 0) {
            inventory.setItem(leaderboardSlot, plugin.items().make(ItemSpec.of(c.get("leaderboard"), ItemSpec.NONE),
                    m.line("menu.leaderboard.name"), m.lines("menu.leaderboard.lore")));
        }
        int jackpotSlot = c.getInt("jackpot.slot", -1);
        if (jackpotSlot >= 0 && plugin.jackpot().enabled()) {
            inventory.setItem(jackpotSlot, plugin.items().make(ItemSpec.of(c.get("jackpot"), ItemSpec.NONE),
                    m.line("menu.jackpot.name"), m.lines("menu.jackpot.lore",
                            "money", plugin.wallet().format(Currency.MONEY, plugin.jackpot().pot(Currency.MONEY)),
                            "gem", plugin.wallet().format(Currency.GEM, plugin.jackpot().pot(Currency.GEM)),
                            "chance", Format.decimal(plugin.jackpot().chancePercent()) + "%",
                            "min_money", plugin.wallet().format(Currency.MONEY, plugin.jackpot().minBet(Currency.MONEY)),
                            "min_gem", plugin.wallet().format(Currency.GEM, plugin.jackpot().minBet(Currency.GEM)))));
        }
        int profileSlot = c.getInt("profile.slot", -1);
        if (profileSlot >= 0) {
            long play = plugin.access().playLeft(player);
            long lossMoney = plugin.access().lossLeft(player, Currency.MONEY);
            long lossGem = plugin.access().lossLeft(player, Currency.GEM);
            ItemStack head = plugin.items().make(ItemSpec.of(c.get("profile"), new ItemSpec("", Material.PLAYER_HEAD, 0)),
                    m.line("menu.profile.name", "player", player.getName()),
                    m.lines("menu.profile.lore",
                            "money", plugin.wallet().format(Currency.MONEY, Math.max(0, plugin.wallet().balance(player, Currency.MONEY))),
                            "gem", plugin.wallet().format(Currency.GEM, Math.max(0, plugin.wallet().balance(player, Currency.GEM))),
                            "playtime", play < 0 ? m.raw("menu.unlimited") : Format.duration(play),
                            "loss_money", lossMoney < 0 ? m.raw("menu.unlimited") : plugin.wallet().format(Currency.MONEY, lossMoney),
                            "loss_gem", lossGem < 0 ? m.raw("menu.unlimited") : plugin.wallet().format(Currency.GEM, lossGem)));
            if (head.getItemMeta() instanceof SkullMeta skull) {
                Heads.apply(skull, player.getUniqueId());
                head.setItemMeta(skull);
            }
            inventory.setItem(profileSlot, head);
        }
    }

    /**
     * The game's icon entry: from the server's config.yml, else from the copy in the jar, so a game added by an update
     * shows up without touching a hand-edited config.
     */
    private ConfigurationSection gameEntry(ConfigurationSection menu, String id) {
        if (menu.contains("games." + id, true)) return menu.getConfigurationSection("games." + id);
        var defaults = plugin.getConfig().getDefaults();
        return defaults == null ? null : defaults.getConfigurationSection("menu.games." + id);
    }

    @Override
    public void onClick(int slot, ClickType click) {
        Game game = gameSlots.get(slot);
        if (game != null) {
            if (!game.enabled()) {
                plugin.messages().send(player, "game.disabled");
                return;
            }
            if (plugin.access().busyElsewhere(player, game.id())
                    || !game.permitted(player) || !plugin.access().canPlay(player, game.casinoOnly(), npc)) return;
            if (plugin.gameSounds()) player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
            // casino games are played at their machines: go to a free one (games playable anywhere open here)
            if (game.casinoOnly() && plugin.getConfig().getBoolean("menu.teleport-to-machine", true)) {
                player.closeInventory();
                plugin.machines().goTo(player, game);
            } else {
                game.open(player, npc);
            }
            return;
        }
        if (slot == leaderboardSlot) {
            new LeaderboardMenu(plugin, player, Currency.MONEY).open();
        } else if (slot == statsSlot) {
            new StatsMenu(plugin, player).open();
        } else if (slot == excludeSlot) {
            new ExcludeMenu(plugin, player, 0).open();
        }
    }
}
