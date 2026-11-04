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

    /**
     * Targeted upgrade without a config-version bump: when the server's {@code parent.key} block (2-space indent under
     * a top-level {@code parent:}) still contains {@code marker}, only that block is replaced, line for line, by the
     * jar's copy. Everything else in the file stays exactly as the owner wrote it. A copy of the old file goes to
     * {@code old-configs/}.
     */
    public static void replaceBlock(JavaPlugin plugin, String path, String parent, String key, String marker) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) return;
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) return;
            String server = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String jar = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String nl = server.contains("\r\n") ? "\r\n" : "\n";
            java.util.List<String> lines = new java.util.ArrayList<>(java.util.Arrays.asList(server.split("\r?\n", -1)));
            java.util.List<String> jarLines = java.util.Arrays.asList(jar.split("\r?\n", -1));
            int[] own = block(lines, parent, key);
            int[] fresh = block(jarLines, parent, key);
            if (own == null || fresh == null) return;
            boolean old = false;
            for (int i = own[0]; i < own[1]; i++) if (lines.get(i).startsWith(marker)) old = true;
            if (!old) return;
            File backup = new File(plugin.getDataFolder(), "old-configs/" + path.replace(".yml", "") + "-" + key + "-"
                    + LocalDateTime.now().format(STAMP) + ".yml");
            Files.createDirectories(backup.getParentFile().toPath());
            Files.copy(file.toPath(), backup.toPath());
            java.util.List<String> out = new java.util.ArrayList<>(lines.subList(0, own[0]));
            out.addAll(jarLines.subList(fresh[0], fresh[1]));
            out.addAll(lines.subList(own[1], lines.size()));
            Files.writeString(file.toPath(), String.join(nl, out), StandardCharsets.UTF_8);
            plugin.getLogger().warning(path + ": the '" + parent + "." + key + "' texts were replaced by the new ones; "
                    + "your old file is in " + plugin.getDataFolder().getName() + "/old-configs/" + backup.getName());
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not upgrade " + parent + "." + key + " in " + path + ": " + ex.getMessage());
        }
    }

    /** [start, end) of the lines of {@code parent.key}, or null. The block ends at the next line indented 2 or less. */
    static int[] block(java.util.List<String> lines, String parent, String key) {
        int p = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).stripTrailing().equals(parent + ":")) {
                p = i;
                break;
            }
        }
        if (p < 0) return null;
        int start = -1;
        for (int i = p + 1; i < lines.size(); i++) {
            String l = lines.get(i);
            if (!l.isBlank() && !l.startsWith(" ") && !l.startsWith("#")) break; // next top-level key
            if (l.stripTrailing().equals("  " + key + ":")) {
                start = i;
                break;
            }
        }
        if (start < 0) return null;
        int end = start + 1;
        while (end < lines.size()) {
            String l = lines.get(end);
            if (!l.isBlank() && indent(l) <= 2) break;
            end++;
        }
        // blank lines before the next key stay with the next key
        while (end - 1 > start && lines.get(end - 1).isBlank()) end--;
        return new int[] {start, end};
    }

    private static int indent(String l) {
        int i = 0;
        while (i < l.length() && l.charAt(i) == ' ') i++;
        return i;
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
