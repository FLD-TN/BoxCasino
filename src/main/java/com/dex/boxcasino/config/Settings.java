package com.dex.boxcasino.config;

import com.dex.boxcasino.economy.Currency;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** config.yml, read once per load/reload. Immutable. */
public final class Settings {

    public final Set<String> regions;
    public final boolean npcIgnoresRegion;
    public final boolean playLimitEnabled;
    public final long playSeconds;
    public final long restSeconds;
    /** Net profit of one day (won − lost) after which no new bet is taken; 0 = no limit. */
    public final Map<Currency, Long> dailyProfitLimit;
    public final ZoneId zone;
    public final int clickCooldownTicks;
    public final int logRetentionDays;
    /** Chip value → look, ascending. */
    public final Map<Currency, NavigableMap<Long, ItemSpec>> chips;

    public Settings(FileConfiguration c, Logger log) {
        List<String> ids = new ArrayList<>();
        for (String s : c.getStringList("access.regions")) ids.add(s.toLowerCase(Locale.ROOT));
        regions = Set.copyOf(ids);
        npcIgnoresRegion = c.getBoolean("access.npc-ignores-region", false);
        playLimitEnabled = c.getBoolean("play-limit.enabled", true);
        playSeconds = Math.max(60, c.getLong("play-limit.play-minutes", 180) * 60);
        restSeconds = Math.max(60, c.getLong("play-limit.rest-minutes", 60) * 60);
        Map<Currency, Long> profit = new EnumMap<>(Currency.class);
        for (Currency cur : Currency.values()) profit.put(cur, Math.max(0, c.getLong("daily-profit-limit." + cur.name(), 0)));
        dailyProfitLimit = Map.copyOf(profit);
        ZoneId z;
        try {
            z = ZoneId.of(c.getString("timezone", "Asia/Ho_Chi_Minh"));
        } catch (RuntimeException ex) {
            log.warning("Invalid timezone in config.yml, using Asia/Ho_Chi_Minh");
            z = ZoneId.of("Asia/Ho_Chi_Minh");
        }
        zone = z;
        clickCooldownTicks = Math.max(1, c.getInt("click-cooldown-ticks", 4));
        logRetentionDays = Math.max(1, c.getInt("log-retention-days", 90));

        Map<Currency, NavigableMap<Long, ItemSpec>> chipMap = new EnumMap<>(Currency.class);
        for (Currency cur : Currency.values()) {
            NavigableMap<Long, ItemSpec> values = new TreeMap<>();
            ConfigurationSection section = c.getConfigurationSection("chips." + cur.name());
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        long value = Long.parseLong(key);
                        if (value > 0) values.put(value, ItemSpec.of(section.get(key), ItemSpec.NONE));
                    } catch (NumberFormatException ex) {
                        log.warning("chips." + cur.name() + "." + key + " is not a number, ignored");
                    }
                }
            }
            if (values.isEmpty()) values.put(cur == Currency.MONEY ? 1000L : 1L, ItemSpec.NONE);
            chipMap.put(cur, values);
        }
        chips = Map.copyOf(chipMap);
    }
}
