package com.dex.boxcasino.pvp;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.gui.CasinoView;
import com.dex.boxcasino.gui.Heads;
import com.dex.boxcasino.util.Format;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

/**
 * Challenge lobby of a game played without a table (Xúc Xắc, Oẳn Tù Tì): a plain chest GUI (no resource pack).
 * Top row: the game's rules in the middle. Rows 2-4: the head of every player with an open challenge (bet, prize, time
 * left in the lore; a click accepts it and the match opens on the game's texture; the viewer's own challenge comes first
 * and a click cancels it). Bottom row: pages and "create challenge" in the middle (currency picker, then the amount is
 * typed in chat). Everything else is grey glass.
 */
final class LobbyView extends CasinoView {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int INFO = 4, EMPTY = 22, PREVIOUS = 48, CREATE = 49, NEXT = 50;

    private final PvpGame game;
    private final Map<Integer, Integer> challengeAt = new HashMap<>();
    private final BukkitTask timer;
    private int page;

    LobbyView(BoxCasinoPlugin plugin, Player player, PvpGame game) {
        super(plugin, player);
        this.game = game;
        this.inventory = Bukkit.createInventory(this, 54, plugin.messages().text("pvp.lobby.title", "game", game.displayName()));
        plugin.pvp().addLobby(this);
        refresh();
        // the time left in the lore counts down
        timer = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 20L, 20L);
    }

    private List<Challenge> list() {
        List<Challenge> mine = new ArrayList<>(), others = new ArrayList<>();
        for (Challenge c : plugin.pvp().challenges(game, player.getUniqueId(), null)) {
            (c.creator.equals(player.getUniqueId()) ? mine : others).add(c);
        }
        mine.addAll(others);
        return mine;
    }

    private String[] limits() {
        var money = game.settings().limits.get(Currency.MONEY);
        var gem = game.settings().limits.get(Currency.GEM);
        return new String[] {
                "game", game.displayName(), "tax", Format.decimal(game.taxPercent()),
                "min", plugin.wallet().format(Currency.MONEY, money.min()),
                "max", plugin.wallet().format(Currency.MONEY, money.max()),
                "gemmin", plugin.wallet().format(Currency.GEM, gem.min()),
                "gemmax", plugin.wallet().format(Currency.GEM, gem.max()),
                "minutes", String.valueOf(game.challengeMinutes), "label", label()
        };
    }

    void refresh() {
        if (isClosed()) return;
        Messages m = plugin.messages();
        challengeAt.clear();
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
        for (int slot : SLOTS) inventory.setItem(slot, null);

        List<Challenge> all = list();
        int pages = Math.max(1, (all.size() + SLOTS.length - 1) / SLOTS.length);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < SLOTS.length; i++) {
            int index = page * SLOTS.length + i;
            if (index >= all.size()) break;
            Challenge c = all.get(index);
            boolean own = c.creator.equals(player.getUniqueId());
            String[] ph = {
                    "player", c.creatorName, "table", String.valueOf(c.id), "game", game.displayName(),
                    "bet", plugin.wallet().format(c.currency, c.amount()),
                    "prize", plugin.wallet().format(c.currency, game.winnerPayout(c.amount())),
                    "tax", Format.decimal(game.taxPercent()), "time", PvpService.left(c)
            };
            String key = own ? "pvp.lobby.own" : c.target != null ? "pvp.lobby.invite" : "pvp.lobby.offer";
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta skull) {
                Heads.apply(skull, c.creator);
                skull.displayName(m.line(key + ".name", ph));
                skull.lore(m.lines(key + ".lore", ph));
                head.setItemMeta(skull);
            }
            inventory.setItem(SLOTS[i], head);
            challengeAt.put(SLOTS[i], c.id);
        }
        String[] ph = limits();
        if (all.isEmpty()) inventory.setItem(EMPTY, item(Material.LIGHT_GRAY_DYE, m.line("pvp.lobby.empty.name"), m.lines("pvp.lobby.empty.lore", ph)));
        inventory.setItem(INFO, item(infoMaterial(),
                m.line("pvp.lobby.info.name", ph), m.lines("pvp.lobby.info-" + game.id() + ".lore", ph)));
        boolean hasOwn = plugin.pvp().ownChallenge(game, player.getUniqueId()) != null;
        inventory.setItem(CREATE, hasOwn
                ? item(Material.CLOCK, m.line("pvp.lobby.create-waiting.name", ph), m.lines("pvp.lobby.create-waiting.lore", ph))
                : plugin.items().make(new com.dex.boxcasino.config.ItemSpec("", Material.EMERALD, 0),
                        m.line("pvp.lobby.create.name", ph), m.lines("pvp.lobby.create.lore", ph)));
        if (page > 0) inventory.setItem(PREVIOUS, item(Material.ARROW, m.line("pvp.lobby.previous", "page", String.valueOf(page)), List.of()));
        if (page < pages - 1) inventory.setItem(NEXT, item(Material.ARROW, m.line("pvp.lobby.next", "page", String.valueOf(page + 2)), List.of()));
    }

    private Material infoMaterial() {
        return switch (game.id()) {
            case "dice" -> Material.BONE;
            case "tictactoe" -> Material.PAPER;
            default -> Material.SHEARS;
        };
    }

    /** Command shown in the rules: /xucxac, /kbb, /caro. */
    private String label() {
        return switch (game.id()) {
            case "dice" -> "xucxac";
            case "tictactoe" -> "caro";
            default -> "kbb";
        };
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private void click() {
        if (plugin.gameSounds()) player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (slot == PREVIOUS && page > 0) {
            page--;
            click();
            refresh();
            return;
        }
        if (slot == NEXT) {
            page++;
            click();
            refresh();
            return;
        }
        if (slot == CREATE) {
            click();
            if (plugin.pvp().ownChallenge(game, player.getUniqueId()) != null) {
                plugin.messages().send(player, "pvp.already-open");
                return;
            }
            new CurrencyPickView(plugin, player, game).open();
            return;
        }
        Integer id = challengeAt.get(slot);
        if (id == null) return;
        Challenge c = plugin.pvp().challenge(id);
        if (c == null) {
            refresh();
            return;
        }
        click();
        if (c.creator.equals(player.getUniqueId())) plugin.pvp().cancel(player, id);
        else plugin.pvp().accept(player, id, false);
    }

    @Override
    protected void onClose() {
        timer.cancel();
        plugin.pvp().removeLobby(this);
    }
}
