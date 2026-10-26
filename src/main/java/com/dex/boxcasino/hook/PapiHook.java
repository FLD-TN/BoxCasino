package com.dex.boxcasino.hook;

import java.util.function.UnaryOperator;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;

/** The only class calling PlaceholderAPI directly; loaded only when PlaceholderAPI is enabled. */
public final class PapiHook {

    private PapiHook() {
    }

    /** %img_money%, %img_gem%... from ItemsAdder's PlaceholderAPI expansion (no player needed). */
    public static UnaryOperator<String> imageResolver() {
        return s -> PlaceholderAPI.setPlaceholders((OfflinePlayer) null, s);
    }
}
