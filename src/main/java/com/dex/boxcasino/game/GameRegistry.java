package com.dex.boxcasino.game;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public final class GameRegistry {

    private final Map<String, Game> games = new LinkedHashMap<>();

    public void register(Game game) {
        games.put(game.id(), game);
    }

    public Game get(String id) {
        return id == null ? null : games.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Game> all() {
        return games.values();
    }

    public void shutdownAll() {
        for (Game game : games.values()) game.shutdown();
    }

    public void loadAll(Logger log) {
        for (Game game : games.values()) {
            String error;
            try {
                error = game.load();
            } catch (RuntimeException ex) {
                error = ex.toString();
            }
            if (error != null) log.severe("Game " + game.id() + " disabled: " + error);
            else if (!game.enabled()) log.info("Game " + game.id() + " is switched off in games/" + game.id() + ".yml");
        }
    }
}
