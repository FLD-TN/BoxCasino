package com.dex.boxcasino.game.slots;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.game.Game;
import com.dex.boxcasino.util.Payouts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Máy Xèng (Vegas "Drums"): a 5x5 screen of symbols. The prize is drawn first (a shape from the table, or nothing),
 * then the screen is built so that exactly that shape shows in one symbol and no other shape shows at all. The shapes are
 * fixed; their chance and prize come from {@code games/slots.yml}.
 */
public final class SlotsGame extends Game {

    static final int SIZE = 5;

    /** Every shape a win can take, all its positions on the 5x5 screen (true = part of the shape). */
    private static final Map<String, List<boolean[]>> SHAPES = new LinkedHashMap<>();

    static {
        SHAPES.put("square2", slide(new String[] {"11", "11"}));
        SHAPES.put("cross3", slide(new String[] {"101", "010", "101"}));
        List<boolean[]> rows = slide(new String[] {"11111"});
        SHAPES.put("hline", List.of(rows.get(0), rows.get(1), rows.get(3), rows.get(4)));
        SHAPES.put("vline", slide(new String[] {"1", "1", "1", "1", "1"}));
        SHAPES.put("hline-center", List.of(rows.get(2)));
        SHAPES.put("diagonal", List.of(mask(new String[] {"10000", "01000", "00100", "00010", "00001"}),
                mask(new String[] {"00001", "00010", "00100", "01000", "10000"})));
        SHAPES.put("square3", slide(new String[] {"111", "111", "111"}));
        SHAPES.put("cross5", List.of(mask(new String[] {"10001", "01010", "00100", "01010", "10001"})));
        SHAPES.put("square4", slide(new String[] {"1111", "1111", "1111", "1111"}));
        SHAPES.put("full", slide(new String[] {"11111", "11111", "11111", "11111", "11111"}));
    }

    record Pattern(String key, double chance, double multiplier, List<boolean[]> masks) {
    }

    record Symbol(int index, double weight, double bonus, ItemSpec look) {
    }

    /** A drawn spin: the screen (symbol index per cell, row by row) and what it pays. */
    record Spin(int[] grid, Pattern pattern, Symbol symbol, boolean[] mask, double multiplier) {
    }

    final List<Pattern> patterns = new ArrayList<>();
    final List<Symbol> symbols = new ArrayList<>();
    List<Integer> grid = List.of();
    int spotSlot;
    List<Integer> leverSlots = List.of();
    int frameTicks;
    int[] stopFrames = new int[SIZE];
    int resetTicks;
    ItemSpec spotItem;
    ItemSpec dimItem;
    ItemSpec leverIdle;
    ItemSpec leverRunning;
    ItemSpec leverEnd;
    private double totalPatternChance;
    private double totalSymbolWeight;

    public SlotsGame(BoxCasinoPlugin plugin) {
        super(plugin, "slots");
    }

    @Override
    protected String loadGame(YamlConfiguration yml) {
        patterns.clear();
        totalPatternChance = 0;
        ConfigurationSection ps = yml.getConfigurationSection("patterns");
        if (ps == null) return "patterns missing";
        for (String key : SHAPES.keySet()) {
            ConfigurationSection p = ps.getConfigurationSection(key);
            if (p == null) continue;
            double chance = p.getDouble("chance");
            double multiplier = p.getDouble("multiplier");
            if (chance < 0 || multiplier < 0) return "patterns." + key + " negative";
            if (chance > 0) patterns.add(new Pattern(key, chance, multiplier, SHAPES.get(key)));
            totalPatternChance += chance;
        }
        if (totalPatternChance > 100) return "pattern chances add up to more than 100%";
        // bigger prizes first: the screen check looks for the best shape on it
        patterns.sort((a, b) -> Double.compare(b.multiplier(), a.multiplier()));

        symbols.clear();
        totalSymbolWeight = 0;
        for (Map<?, ?> m : yml.getMapList("symbols")) {
            double w = m.get("weight") instanceof Number n ? n.doubleValue() : 1;
            double bonus = m.get("bonus") instanceof Number n ? n.doubleValue() : 1;
            if (w < 0 || bonus < 0) return "symbols need weight >= 0 and bonus >= 0";
            symbols.add(new Symbol(symbols.size(), w, bonus, ItemSpec.of(m, ItemSpec.NONE)));
            totalSymbolWeight += w;
        }
        // the fallback screen needs 7 symbols besides the winning one
        if (symbols.size() < 8 || totalSymbolWeight <= 0) return "symbols need at least 8 entries";

        grid = settings.slots("grid");
        if (grid.size() != SIZE * SIZE) return "slots.grid needs 25 slots (5 rows of 5, row by row)";
        spotSlot = settings.slot("spot", 26);
        leverSlots = settings.slots("lever");
        frameTicks = Math.max(1, yml.getInt("frame-ticks", 2));
        List<Integer> stops = yml.getIntegerList("stop-frames");
        for (int c = 0; c < SIZE; c++) stopFrames[c] = c < stops.size() ? Math.max(1, stops.get(c)) : 8 + c * 3;
        resetTicks = Math.max(10, yml.getInt("reset-ticks", 60));
        spotItem = settings.item("spot");
        dimItem = settings.item("dim");
        leverIdle = settings.item("lever-idle");
        leverRunning = settings.item("lever-running");
        leverEnd = settings.item("lever-end");
        return null;
    }

    double bonusAverage() {
        double sum = 0;
        for (Symbol s : symbols) sum += s.weight() * s.bonus();
        return sum / totalSymbolWeight;
    }

    @Override
    public double rtp() {
        double sum = 0;
        for (Pattern p : patterns) sum += p.chance() / 100.0 * p.multiplier();
        return sum * bonusAverage();
    }

    /** Draws the prize, then a screen that shows exactly it. */
    Spin spin(RandomGenerator random) {
        Pattern pattern = null;
        double r = random.nextDouble() * 100;
        for (Pattern p : patterns) {
            r -= p.chance();
            if (r < 0) {
                pattern = p;
                break;
            }
        }
        if (pattern == null) return new Spin(screen(null, null, random), null, null, null, 0);
        Symbol symbol = drawSymbol(random);
        boolean[] mask = pattern.masks().get(random.nextInt(pattern.masks().size()));
        return new Spin(screen(symbol, mask, random), pattern, symbol, mask, pattern.multiplier() * symbol.bonus());
    }

    private Symbol drawSymbol(RandomGenerator random) {
        double r = random.nextDouble() * totalSymbolWeight;
        for (Symbol s : symbols) {
            r -= s.weight();
            if (r < 0) return s;
        }
        return symbols.get(symbols.size() - 1);
    }

    /**
     * Random symbols outside the shape until the screen shows no other shape (a few tries); if that keeps failing, a
     * fixed spread of the other symbols that can never form a shape (see {@link #spread}).
     */
    private int[] screen(Symbol symbol, boolean[] mask, RandomGenerator random) {
        int[] g = new int[SIZE * SIZE];
        for (int attempt = 0; attempt < 200; attempt++) {
            for (int i = 0; i < g.length; i++) {
                g[i] = mask != null && mask[i] ? symbol.index() : random.nextInt(symbols.size());
            }
            if (showsOnly(g, mask)) return g;
        }
        int offset = random.nextInt(7);
        for (int i = 0; i < g.length; i++) {
            if (mask != null && mask[i]) {
                g[i] = symbol.index();
            } else {
                int other = spread(i / SIZE, i % SIZE, offset);
                g[i] = symbol != null && other >= symbol.index() ? other + 1 : other;
            }
        }
        return g;
    }

    /**
     * (2 × row + column + offset) mod 7: along every row, column, diagonal, 2x2 square and small X the values differ,
     * so these cells alone never form a shape.
     */
    static int spread(int row, int col, int offset) {
        return Math.floorMod(2 * row + col + offset, 7);
    }

    /**
     * True when the screen shows the drawn shape and nothing else that looks like a win: every shape on it lies inside
     * the drawn one (a 3x3 square holds 2x2 squares of its own symbol). A losing spin shows no shape at all, so the
     * player never sees a second line or square that is not paid.
     */
    private boolean showsOnly(int[] g, boolean[] drawn) {
        for (Pattern p : patterns) {
            for (boolean[] m : p.masks()) {
                if (filledBy(g, m) < 0) continue;
                if (drawn == null || !inside(m, drawn)) return false;
            }
        }
        return true;
    }

    static boolean inside(boolean[] mask, boolean[] outer) {
        for (int i = 0; i < mask.length; i++) if (mask[i] && !outer[i]) return false;
        return true;
    }

    /** The symbol filling every cell of the mask, or -1. */
    static int filledBy(int[] g, boolean[] mask) {
        int s = -1;
        for (int i = 0; i < g.length; i++) {
            if (!mask[i]) continue;
            if (s < 0) s = g[i];
            else if (g[i] != s) return -1;
        }
        return s;
    }

    private static boolean[] mask(String[] rows) {
        boolean[] m = new boolean[SIZE * SIZE];
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) m[r * SIZE + c] = rows[r].charAt(c) == '1';
        return m;
    }

    /** The shape at every position it fits on the screen. */
    private static List<boolean[]> slide(String[] shape) {
        List<boolean[]> out = new ArrayList<>();
        int h = shape.length;
        int w = shape[0].length();
        for (int r = 0; r + h <= SIZE; r++) {
            for (int c = 0; c + w <= SIZE; c++) {
                boolean[] m = new boolean[SIZE * SIZE];
                for (int i = 0; i < h; i++) for (int j = 0; j < w; j++) m[(r + i) * SIZE + c + j] = shape[i].charAt(j) == '1';
                out.add(m);
            }
        }
        return out;
    }

    @Override
    public void open(Player player, boolean npc) {
        new SlotsView(plugin, player, this, npc).open();
    }

    @Override
    public double simulate(long rounds, RandomGenerator random) {
        long bet = 1000;
        double paid = 0;
        for (long r = 0; r < rounds; r++) paid += Payouts.payout(bet, spin(random).multiplier());
        return paid / (rounds * (double) bet);
    }
}
