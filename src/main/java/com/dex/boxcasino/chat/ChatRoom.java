package com.dex.boxcasino.chat;

import java.util.Collection;
import java.util.UUID;
import org.bukkit.entity.Player;

/** A table where several players sit together (PvP match, xì dách table, the shared race) and can talk. */
public interface ChatRoom {

    /** Players who belong to the room right now. */
    Collection<UUID> members();

    /** Redraws every open GUI of the room (new chat bubble, bubble expired). */
    void refreshViews();

    /** Opens the room's GUI again for a member (after chatting outside the GUI). */
    void reopen(Player player);
}
