package com.dex.boxcasino.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Every player-facing string, from messages.yml ('&' colours and '&#rrggbb' hex). Missing keys fall back to the copy
 * inside the jar. Placeholders are passed as pairs: {@code text("key", "amount", "10")}.
 */
public final class Messages {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    /** Replaces %img_...% (ItemsAdder font images through PlaceholderAPI) before colours are parsed. */
    private static volatile java.util.function.UnaryOperator<String> images = s -> s;

    public static void setImageResolver(java.util.function.UnaryOperator<String> resolver) {
        images = resolver == null ? s -> s : resolver;
    }

    private final JavaPlugin plugin;
    private volatile YamlConfiguration yml = new YamlConfiguration();

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        ConfigFiles.ensure(plugin, "messages.yml");
        File file = new File(plugin.getDataFolder(), "messages.yml");
        // Vòng Quay (0.18, "difficulty:") and the board Roulette (0.19, "board-button:") texts become the one-screen Roulette
        ConfigFiles.replaceBlock(plugin, "messages.yml", "games", "roulette", "    difficulty:");
        ConfigFiles.replaceBlock(plugin, "messages.yml", "games", "roulette", "    board-button:");
        // 0.21.0: Tên Lửa lost its auto cash-out button ("auto:" texts)
        ConfigFiles.replaceBlock(plugin, "messages.yml", "games", "crash", "    auto:");
        // 0.23.0: Tên Lửa became Cắt Dây (the pack draws a rope and scissors); untouched default block only
        ConfigFiles.replaceBlock(plugin, "messages.yml", "games", "crash", "    name: '&#FF6F91Tên Lửa'");
        // 0.22.0: Roulette takes one spot per round (only the untouched default lines change)
        ConfigFiles.replaceLine(plugin, "messages.yml", "        - '&8Đặt nhiều ô cùng lúc được'",
                "        - '&8Mỗi ván chỉ đặt 1 ô, quay 1 lần'");
        ConfigFiles.replaceLine(plugin, "messages.yml", "    no-stakes: '&#FF6B6B✘ Hãy đặt cược vào ít nhất 1 ô'",
                "    no-stakes: '&#FF6B6B✘ Hãy chọn 1 ô Đỏ / Đen / Xanh để cược'");
        ConfigFiles.replaceLine(plugin, "messages.yml", "    max-total: '&#FF6B6B✘ Tổng cược 1 ván tối đa {max}'",
                "    max-total: '&#FF6B6B✘ Cược 1 ván tối đa {max}'");
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read default messages.yml: " + e.getMessage());
        }
        yml = loaded;
    }

    public String raw(String path, String... placeholders) {
        String s = yml.getString(path);
        return s == null ? path : apply(s, placeholders);
    }

    public List<String> rawList(String path, String... placeholders) {
        List<String> out = new ArrayList<>();
        for (String s : yml.getStringList(path)) out.add(apply(s, placeholders));
        return out;
    }

    public Component text(String path, String... placeholders) {
        return parse(raw(path, placeholders));
    }

    /** One item name line: no default italics. */
    public Component line(String path, String... placeholders) {
        return itemLine(raw(path, placeholders));
    }

    public List<Component> lines(String path, String... placeholders) {
        List<Component> out = new ArrayList<>();
        for (String s : rawList(path, placeholders)) out.add(itemLine(s));
        return out;
    }

    /** Prefixed chat message. Empty string in messages.yml = message disabled. */
    public void send(CommandSender to, String path, String... placeholders) {
        String s = raw(path, placeholders);
        if (s.isEmpty()) return;
        to.sendMessage(parse(raw("prefix") + s));
    }

    public String plain(String path, String... placeholders) {
        return PlainTextComponentSerializer.plainText().serialize(text(path, placeholders));
    }

    public static Component parse(String legacy) {
        if (legacy.indexOf("%img_") >= 0) legacy = images.apply(legacy);
        return LEGACY.deserialize(legacy);
    }

    public static Component itemLine(String legacy) {
        return parse(legacy).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static String apply(String s, String... placeholders) {
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            s = s.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return s;
    }
}
