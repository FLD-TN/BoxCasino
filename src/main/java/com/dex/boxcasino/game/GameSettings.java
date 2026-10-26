package com.dex.boxcasino.game;

import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.economy.Currency;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** The part of {@code games/<id>.yml} every game has: switch, GUI, bet limits, common buttons. */
public final class GameSettings {

    public final YamlConfiguration yml;
    public final boolean enabled;
    public final String title;
    public final String fallbackTitle;
    public final int rows;
    public final Map<Currency, Limits> limits;
    public final int betSlot;
    public final int currencySlot;
    public final int infoSlot;
    public final ItemSpec betItem;
    public final ItemSpec currencyMoneyItem;
    public final ItemSpec currencyGemItem;
    public final ItemSpec infoItem;

    public GameSettings(YamlConfiguration yml) {
        this.yml = yml;
        enabled = yml.getBoolean("enabled", true);
        title = yml.getString("title", "");
        fallbackTitle = yml.getString("fallback-title", "&8Casino");
        rows = Math.max(1, Math.min(6, yml.getInt("rows", 6)));
        Map<Currency, Limits> l = new EnumMap<>(Currency.class);
        for (Currency c : Currency.values()) {
            long min = Math.max(1, yml.getLong("bet." + c.name() + ".min", c == Currency.MONEY ? 1000 : 1));
            long max = Math.max(min, yml.getLong("bet." + c.name() + ".max", c == Currency.MONEY ? 1_000_000 : 1000));
            l.put(c, new Limits(min, max));
        }
        limits = Map.copyOf(l);
        betSlot = slot("bet", -1);
        currencySlot = slot("currency", -1);
        infoSlot = slot("info", -1);

        ConfigurationSection items = yml.getConfigurationSection("items");
        betItem = ItemSpec.of(items, "bet", ItemSpec.NONE);
        currencyMoneyItem = ItemSpec.of(items, "currency-money", ItemSpec.NONE);
        currencyGemItem = ItemSpec.of(items, "currency-gem", ItemSpec.NONE);
        infoItem = ItemSpec.of(items, "info", ItemSpec.NONE);
    }

    public ItemSpec item(String key) {
        return ItemSpec.of(yml.getConfigurationSection("items"), key, ItemSpec.NONE);
    }

    /** A slot from the server's file, else from the copy in the jar (new keys work without regenerating files). */
    public int slot(String key, int fallback) {
        var defaults = yml.getDefaults();
        return yml.getInt("slots." + key, defaults == null ? fallback : defaults.getInt("slots." + key, fallback));
    }

    public List<Integer> slots(String key) {
        List<Integer> out = new ArrayList<>();
        Object raw = yml.get("slots." + key);
        if (raw instanceof Number n) out.add(n.intValue());
        else for (Object o : yml.getList("slots." + key, List.of())) if (o instanceof Number n) out.add(n.intValue());
        return out;
    }

    public record Limits(long min, long max) {
        public long clamp(long amount) {
            return Math.max(min, Math.min(max, amount));
        }
    }
}
