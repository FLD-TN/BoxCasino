package com.dex.boxcasino.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Keeps the files in the data folder in step with the jar (same idea as BoxClans' text-version): when the copy in the
 * jar has a higher {@code config-version}, the server's file is moved to {@code old-configs/} and replaced.
 * Nothing is deleted; data.db is never touched.
 */
public final class ConfigFiles {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ConfigFiles() {
    }

    /** Creates or upgrades {@code path} (relative to the data folder and to the jar root). */
    public static void ensure(JavaPlugin plugin, String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            plugin.saveResource(path, false);
            return;
        }
        int jarVersion;
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) return;
            jarVersion = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getInt("config-version", 0);
        } catch (IOException ex) {
            return;
        }
        int fileVersion = YamlConfiguration.loadConfiguration(file).getInt("config-version", 0);
        if (fileVersion >= jarVersion) return;
        File backup = new File(plugin.getDataFolder(), "old-configs/" + path.replace(".yml", "")
                + "-v" + fileVersion + "-" + LocalDateTime.now().format(STAMP) + ".yml");
        try {
            Files.createDirectories(backup.getParentFile().toPath());
            Files.move(file.toPath(), backup.toPath());
            plugin.saveResource(path, false);
            plugin.getLogger().warning(path + " was upgraded to config-version " + jarVersion
                    + "; your old file is in " + plugin.getDataFolder().getName() + "/old-configs/" + backup.getName());
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not upgrade " + path + ": " + ex.getMessage());
        }
    }
}
