package com.dex.boxcasino.gui;

import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.hook.ItemsAdderHook;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Builds GUI items and GUI titles from config, with or without ItemsAdder. Main thread only. */
public final class Items {

    private static final Pattern IMG = Pattern.compile("\\{img:([a-z0-9_.-]+:[a-z0-9_./-]+)}");

    private ItemsAdderHook itemsAdder;

    public void setItemsAdder(ItemsAdderHook itemsAdder) {
        this.itemsAdder = itemsAdder;
    }

    public boolean hasItemsAdder() {
        return itemsAdder != null;
    }

    /** One line per id for /casino admin debug: found in ItemsAdder or not, material and custom model data. */
    public java.util.List<String> describe(java.util.List<String> ids) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String id : ids) {
            ItemStack item = itemsAdder == null ? null : itemsAdder.item(id);
            if (item == null) {
                out.add(id + " -> KHÔNG TÌM THẤY trong ItemsAdder");
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            String cmd = meta != null && meta.hasCustomModelData() ? String.valueOf(meta.getCustomModelData()) : "không có";
            out.add(id + " -> " + item.getType() + ", custom-model-data " + cmd);
        }
        return out;
    }

    public String glyphInfo(String fontImageId) {
        if (itemsAdder == null) return fontImageId + " -> không có ItemsAdder";
        String g = itemsAdder.glyph(fontImageId);
        return fontImageId + " -> " + (g.isEmpty() ? "KHÔNG TÌM THẤY" : "có (" + g.codePoints()
                .mapToObj(c -> "U+" + Integer.toHexString(c).toUpperCase()).reduce((a, b) -> a + " " + b).orElse("") + ")");
    }

    public ItemStack make(ItemSpec spec, Component name, List<Component> lore) {
        ItemStack item = null;
        if (itemsAdder != null && !spec.itemsAdderId().isEmpty()) item = itemsAdder.item(spec.itemsAdderId());
        if (item == null) {
            Material material = spec.material() == Material.AIR ? Material.STICK : spec.material();
            item = new ItemStack(material);
            if (spec.customModelData() != 0) {
                ItemMeta meta = item.getItemMeta();
                meta.setCustomModelData(spec.customModelData());
                item.setItemMeta(meta);
            }
        }
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * GUI title: {@code {img:namespace:id}} becomes the ItemsAdder font image; other ItemsAdder shortcodes such as
     * {@code :offset_-48:} are left for ItemsAdder to replace. Without ItemsAdder, {@code fallback} is used.
     */
    public Component title(String raw, String fallback) {
        if (itemsAdder == null) return Messages.parse(fallback);
        Matcher m = IMG.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(itemsAdder.glyph(m.group(1))));
        m.appendTail(sb);
        return Messages.parse(sb.toString());
    }
}
