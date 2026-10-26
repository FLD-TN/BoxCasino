package com.dex.boxcasino.hook;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * ItemsAdder 4.x API through reflection (no compile dependency, same approach as BoxClans): GUI background font images
 * and custom items of the Vegas packs. Main thread only.
 */
public final class ItemsAdderHook {

    private static final String API = "dev.lone.itemsadder.api.";

    private final Plugin plugin;
    private final Constructor<?> fontImage;
    private final Method exists;
    private final Method string;
    private final Method customStack;
    private final Method itemStack;
    private boolean warned;

    private ItemsAdderHook(Plugin plugin) throws ReflectiveOperationException {
        this.plugin = plugin;
        Class<?> wrapper = Class.forName(API + "FontImages.FontImageWrapper");
        this.fontImage = wrapper.getConstructor(String.class);
        this.exists = wrapper.getMethod("exists");
        this.string = wrapper.getMethod("getString");
        Class<?> stack = Class.forName(API + "CustomStack");
        this.customStack = stack.getMethod("getInstance", String.class);
        this.itemStack = stack.getMethod("getItemStack");
    }

    /** {@code null} when ItemsAdder is not installed. */
    public static ItemsAdderHook create(Plugin plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return null;
        try {
            return new ItemsAdderHook(plugin);
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().log(Level.WARNING, "ItemsAdder API not found: GUI textures disabled", ex);
            return null;
        }
    }

    /** Runs {@code task} every time ItemsAdder (re)loads its data. */
    @SuppressWarnings("unchecked")
    public void onLoad(Runnable task) {
        try {
            Class<? extends Event> type = (Class<? extends Event>) Class.forName(API + "Events.ItemsAdderLoadDataEvent");
            Bukkit.getPluginManager().registerEvent(type, new Listener() {}, EventPriority.MONITOR,
                    (listener, event) -> {
                        if (type.isInstance(event)) task.run();
                    }, plugin);
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().log(Level.WARNING, "Could not listen to ItemsAdder reloads: run /casino admin reload "
                    + "after /iareload", ex);
        }
    }

    /** The font image character, "" when it does not exist. */
    public String glyph(String fontImageId) {
        try {
            Object wrapper = fontImage.newInstance(fontImageId);
            if (!(Boolean) exists.invoke(wrapper)) return "";
            Object s = string.invoke(wrapper);
            return s == null ? "" : s.toString();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            warn(ex);
            return "";
        }
    }

    /** A copy of the ItemsAdder item, {@code null} when it does not exist. */
    public ItemStack item(String id) {
        if (id == null || id.isEmpty()) return null;
        try {
            Object stack = customStack.invoke(null, id);
            return stack == null ? null : ((ItemStack) itemStack.invoke(stack)).clone();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            warn(ex);
            return null;
        }
    }

    private void warn(Exception ex) {
        if (warned) return;
        warned = true;
        plugin.getLogger().log(Level.WARNING, "ItemsAdder API call failed (reported once)", ex);
    }
}
