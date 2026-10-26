package com.dex.boxcasino.config;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * How a GUI item looks: an ItemsAdder id (preferred) with a vanilla fallback (material + custom model data) used when
 * ItemsAdder or the id is missing. Name and lore always come from messages.yml.
 *
 * <pre>info: {item: 'iavegasgui:minesweeper_info', material: STICK, cmd: 5625101}</pre>
 */
public record ItemSpec(String itemsAdderId, Material material, int customModelData) {

    public static final ItemSpec NONE = new ItemSpec("", Material.AIR, 0);

    public static ItemSpec of(Object raw, ItemSpec fallback) {
        if (raw instanceof ConfigurationSection section) return of(section.getValues(false), fallback);
        if (!(raw instanceof Map<?, ?> map)) return fallback;
        String id = str(map.get("item"));
        Material material = Material.matchMaterial(str(map.get("material")).isEmpty() ? "STICK" : str(map.get("material")));
        if (material == null) material = Material.STICK;
        int cmd = map.get("cmd") instanceof Number n ? n.intValue() : 0;
        return new ItemSpec(id, material, cmd);
    }

    public static ItemSpec of(ConfigurationSection parent, String key, ItemSpec fallback) {
        return parent == null ? fallback : of(parent.get(key), fallback);
    }

    public boolean isNone() {
        return itemsAdderId.isEmpty() && material == Material.AIR;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
