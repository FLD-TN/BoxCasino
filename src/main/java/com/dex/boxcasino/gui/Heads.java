package com.dex.boxcasino.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Player head skins without network lookups. {@code setOwningPlayer} makes Paper ask Mojang for the skin, which always
 * times out for offline-mode UUIDs (console spam). The skin of an online player is already on the server (online
 * mode or a skin plugin), so it is copied from there; otherwise the head stays the default one.
 */
public final class Heads {

    private Heads() {
    }

    public static void apply(SkullMeta skull, UUID player) {
        Player online = Bukkit.getPlayer(player);
        if (online == null) return;
        PlayerProfile profile = online.getPlayerProfile();
        if (profile.hasTextures()) skull.setPlayerProfile(profile);
    }
}
