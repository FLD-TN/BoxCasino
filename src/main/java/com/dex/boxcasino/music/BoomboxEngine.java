package com.dex.boxcasino.music;

import com.github.Shimado.boomboxapi.BoomBoxAPI;
import com.github.Shimado.boomboxapi.api.SongsPlayerAPI;
import com.github.Shimado.boomboxapi.instances.NbsSong;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 * The only class that touches the Boombox API: loads .nbs files and plays them to one player. Only created when the
 * Boombox plugin is enabled, so BoxCasino runs fine without it.
 */
final class BoomboxEngine {

    private final Map<String, CasinoSong> songs = new ConcurrentHashMap<>();

    /** Parses the songs (call off the main thread). Missing or broken files are skipped with a warning. */
    void load(File folder, List<String> ids, Logger log) {
        for (String id : ids) {
            File file = new File(folder, id + ".nbs");
            if (!file.isFile()) {
                log.warning("music/" + id + ".nbs not found, that song is skipped");
                continue;
            }
            try {
                NbsSong nbs = BoomBoxAPI.getNbsConverter().parse(file, false);
                if (nbs != null) songs.put(id, new CasinoSong(id, nbs));
                else log.warning("music/" + id + ".nbs could not be read");
            } catch (RuntimeException ex) {
                log.warning("music/" + id + ".nbs could not be read: " + ex.getMessage());
            }
        }
    }

    boolean has(String id) {
        return songs.containsKey(id);
    }

    /** Starts a song for {@code player} only; {@code loop} repeats it. Returns a handle for {@link #stop}, or null. */
    Object play(Player player, String id, boolean loop, int volume) {
        CasinoSong song = songs.get(id);
        if (song == null || BoomBoxAPI.getSongsPlayerAPI() == null) return null;
        SongsPlayerAPI p = BoomBoxAPI.getSongsPlayerAPI().create();
        p.setLocToPlay(player.getLocation())
                .setSongsToList(List.of(song))
                .setCurrentSong(song)
                .setVolume(volume)
                .enableAutoPlay(loop)
                .setPlayerToPlay(player, true)
                .play();
        return p;
    }

    /** Length of the song behind a handle in seconds (0 when unknown). */
    int seconds(Object handle) {
        try {
            return handle instanceof SongsPlayerAPI p ? p.getFullTimeInSeconds() : 0;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    void stop(Object handle) {
        if (handle instanceof SongsPlayerAPI p) {
            try {
                p.stopAndClear();
            } catch (RuntimeException ignored) {
                // the Boombox player may already be gone (player left)
            }
        }
    }
}
