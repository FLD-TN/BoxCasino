package com.dex.boxcasino.extra;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import lol.pyr.znpcsplus.api.NpcApi;
import lol.pyr.znpcsplus.api.NpcApiProvider;
import lol.pyr.znpcsplus.api.npc.Npc;
import lol.pyr.znpcsplus.api.npc.NpcEntry;
import lol.pyr.znpcsplus.api.skin.SkinDescriptor;
import lol.pyr.znpcsplus.util.LookType;
import lol.pyr.znpcsplus.util.NpcLocation;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;

/** The only class that touches ZNPCsPlus: dealer NPCs (player type, skin from a png, name above the head). */
final class ZnpcDealers {

    private final BoxCasinoPlugin plugin;
    /** The skin is uploaded once (ZNPCsPlus sends the png to MineSkin) and shared by every dealer. */
    private CompletableFuture<SkinDescriptor> skin;

    ZnpcDealers(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    private static NpcApi api() {
        return NpcApiProvider.get();
    }

    static String id(String machine) {
        return "boxcasino_dealer_" + machine.replace("-", "");
    }

    void spawn(String machine, Location at, String legacyName, String[] skinSource) {
        NpcApi api = api();
        String id = id(machine);
        if (api.getNpcRegistry().getById(id) != null) api.getNpcRegistry().delete(id);
        NpcEntry entry = api.getNpcRegistry().create(id, at.getWorld(), api.getNpcTypeRegistry().getByName("player"),
                new NpcLocation(at));
        entry.enableEverything();
        Npc npc = entry.getNpc();
        String name = MiniMessage.miniMessage().serialize(Messages.parse(legacyName));
        if (!name.isBlank()) npc.getHologram().addLine(name);
        npc.setProperty(api.getPropertyRegistry().getByName("look", LookType.class), LookType.CLOSEST_PLAYER);
        skin(skinSource).thenAccept(d -> Bukkit.getScheduler().runTask(plugin, () -> applySkin(id, d)));
        api.getNpcRegistry().save();
    }

    private void applySkin(String id, SkinDescriptor d) {
        NpcEntry e = api().getNpcRegistry().getById(id);
        if (e == null || d == null) return;
        e.getNpc().setProperty(api().getPropertyRegistry().getByName("skin", SkinDescriptor.class), d);
        api().getNpcRegistry().save();
    }

    /** Puts the (new) skin on every dealer; returns how many dealers there are. */
    int reskin(String[] skinSource) {
        skin = null;
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (String id : api().getNpcRegistry().getAllIds()) if (id.startsWith("boxcasino_dealer_")) ids.add(id);
        skin(skinSource).thenAccept(d -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (d == null) return;
            for (String id : ids) applySkin(id, d);
            plugin.getLogger().info("Dealer skin applied to " + ids.size() + " dealer(s)");
        }));
        return ids.size();
    }

    void forgetSkin() {
        skin = null;
    }

    /**
     * {texture, signature, file}: a signed skin from the config when given (always works), otherwise the png in
     * plugins/ZNPCsPlus/skins that ZNPCsPlus sends to MineSkin (needs MineSkin to accept it).
     */
    private CompletableFuture<SkinDescriptor> skin(String[] source) {
        if (skin != null) return skin;
        if (!source[0].isBlank() && !source[1].isBlank()) {
            skin = CompletableFuture.completedFuture(api().getSkinDescriptorFactory().createStaticDescriptor(source[0], source[1]));
            return skin;
        }
        String file = source[2];
        File folder = new File(Bukkit.getPluginManager().getPlugin("ZNPCsPlus").getDataFolder(), "skins");
        File target = new File(folder, file);
        if (!target.isFile() && "casino_dealer.png".equals(file)) {
            try (InputStream in = plugin.getResource("casino_dealer.png")) {
                if (folder.isDirectory() || folder.mkdirs()) Files.copy(in, target.toPath());
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not copy the dealer skin: " + ex.getMessage());
            }
        }
        skin = CompletableFuture.supplyAsync(() -> {
            try {
                SkinDescriptor d = api().getSkinDescriptorFactory().createFileDescriptor(file);
                plugin.getLogger().info("Dealer skin requested from " + file + " (if the dealer stays without a skin, MineSkin"
                        + " refused it: put texture + signature in config.yml > machines.dealers)");
                return d;
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Dealer skin " + file + " could not be loaded (ZNPCsPlus/MineSkin): " + ex.getMessage());
                return null;
            }
        });
        return skin;
    }

    void remove(String machine) {
        String id = id(machine);
        if (api().getNpcRegistry().getById(id) == null) return;
        api().getNpcRegistry().delete(id);
        api().getNpcRegistry().save();
    }

    /** A short hand swing, like dealing a card or spinning the wheel. */
    void act(String machine) {
        NpcEntry e = api().getNpcRegistry().getById(id(machine));
        if (e != null) e.getNpc().swingHand(false);
    }
}
