package com.dex.boxcasino.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.Collection;
import java.util.Locale;
import org.bukkit.Location;

/** The only class touching WorldGuard; loaded only when WorldGuard is enabled. */
public final class WorldGuardHook {

    private WorldGuardHook() {
    }

    public static boolean inAnyRegion(Location location, Collection<String> regionIds) {
        ApplicableRegionSet set = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                .getApplicableRegions(BukkitAdapter.adapt(location));
        for (ProtectedRegion region : set) {
            if (regionIds.contains(region.getId().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
