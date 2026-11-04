package com.dex.boxcasino.game.roulette;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.game.GameSettings;
import com.dex.boxcasino.game.GameView;
import com.dex.boxcasino.util.Format;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.scheduler.BukkitTask;

/**
 * One player's Roulette table. Chips are laid on the board (child GUI) without taking money; the spin takes the
 * total as one bet, draws the number, then turns the wheel until that number is under the pointer.
 */
final class RouletteView extends GameView {

    private final RouletteGame roulette;
    /** Spots laid for the next spin (amounts in {@link #currency()}). Insertion order = board order of laying. */
    final Map<String, Long> stakes = new LinkedHashMap<>();
    /** Every chip laid, newest last, for undo. */
    private final Deque<Map.Entry<String, Long>> laid = new ArrayDeque<>();
    /** Last spin's spots, for "repeat". */
    private Map<String, Long> lastStakes = Map.of();
    private Currency lastCurrency;
    private Bet bet;
    /** Spots of the running round (copy of stakes at spin time). */
    private Map<String, Long> running = Map.of();
    private int result = -1;
    /** Wheel index under the pointer cell. */
    private int offset;
    private int totalSteps;
    private int step;
    private BukkitTask task;
    /** Numbers drawn at this table, newest first. */
    private final Deque<Integer> history = new ArrayDeque<>();

    RouletteView(BoxCasinoPlugin plugin, Player player, RouletteGame game, boolean npc) {
        super(plugin, player, game, npc);
        this.roulette = game;
        render();
    }

    RouletteGame game() {
        return roulette;
    }

    @Override
    protected boolean locked() {
        return bet != null;
    }

    @Override
    public boolean busy() {
        return bet != null;
    }

    long total() {
        long sum = 0;
        for (long v : stakes.values()) sum += v;
        return sum;
    }

    /** Lays one chip (the current bet amount) on {@code spot}. False when refused (player told). */
    boolean lay(String spot) {
        if (bet != null) return false;
        Currency c = currency();
        GameSettings.Limits limits = gs.limits.get(c);
        long chip = amount();
        long after = total() + chip;
        if (after > limits.max()) {
            plugin.messages().send(player, "games.roulette.max-total", "max", plugin.wallet().format(c, limits.max()));
            return false;
        }
        long cap = roulette.maxStraight.getOrDefault(c, 0L);
        if (RouletteGame.isNumber(spot) && cap > 0 && stakes.getOrDefault(spot, 0L) + chip > cap) {
            plugin.messages().send(player, "games.roulette.max-straight", "max", plugin.wallet().format(c, cap));
            return false;
        }
        long balance = plugin.wallet().balance(player, c);
        if (balance >= 0 && after > balance) {
            plugin.messages().send(player, "games.roulette.no-money", "balance", plugin.wallet().format(c, balance));
            return false;
        }
        stakes.merge(spot, chip, Long::sum);
        laid.addLast(Map.entry(spot, chip));
        return true;
    }

    /** Takes one chip off {@code spot} (the last one laid there, else the current chip amount). */
    boolean takeOff(String spot) {
        Long current = stakes.get(spot);
        if (bet != null || current == null) return false;
        long chip = amount();
        for (var it = laid.descendingIterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getKey().equals(spot)) {
                chip = e.getValue();
                it.remove();
                break;
            }
        }
        long left = current - chip;
        if (left <= 0) stakes.remove(spot);
        else stakes.put(spot, left);
        return true;
    }

    boolean undo() {
        if (bet != null || laid.isEmpty()) return false;
        var e = laid.pollLast();
        long left = stakes.getOrDefault(e.getKey(), 0L) - e.getValue();
        if (left <= 0) stakes.remove(e.getKey());
        else stakes.put(e.getKey(), left);
        return true;
    }

    boolean clear() {
        if (bet != null || stakes.isEmpty()) return false;
        stakes.clear();
        laid.clear();
        return true;
    }

    /** Lays the last spin's spots again (same currency only). */
    boolean repeat() {
        if (bet != null || lastStakes.isEmpty()) return false;
        if (lastCurrency != currency()) {
            plugin.messages().send(player, "games.roulette.repeat-currency");
            return false;
        }
        Map<String, Long> before = new LinkedHashMap<>(stakes);
        var laidBefore = new ArrayDeque<>(laid);
        stakes.clear();
        laid.clear();
        Currency c = currency();
        long sum = 0;
        for (long v : lastStakes.values()) sum += v;
        long balance = plugin.wallet().balance(player, c);
        if (sum > gs.limits.get(c).max() || (balance >= 0 && sum > balance)) {
            stakes.putAll(before);
            laid.addAll(laidBefore);
            plugin.messages().send(player, "games.roulette.no-money", "balance", plugin.wallet().format(c, Math.max(0, balance)));
            return false;
        }
        for (var e : lastStakes.entrySet()) {
            stakes.put(e.getKey(), e.getValue());
            laid.addLast(Map.entry(e.getKey(), e.getValue()));
        }
        return true;
    }

    @Override
    protected void onCurrencyChanged() {
        if (!stakes.isEmpty()) {
            stakes.clear();
            laid.clear();
            plugin.messages().send(player, "games.roulette.cleared-currency");
            render();
        }
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (handlePanel(slot, click)) return;
        if (slot == roulette.spotSlot || slot == roulette.leverSlot) {
            spin();
            return;
        }
        if (slot == roulette.boardSlot) {
            if (bet != null) {
                reject();
                return;
            }
            sound(Sound.UI_BUTTON_CLICK);
            openChild(new RouletteBoard(plugin, player, this));
            return;
        }
        if (slot == roulette.clearSlot) {
            if (clear()) {
                sound(Sound.UI_BUTTON_CLICK);
                render();
            } else {
                reject();
            }
            return;
        }
        if (slot == roulette.repeatSlot) {
            if (repeat()) {
                sound(Sound.UI_BUTTON_CLICK);
                render();
            } else {
                reject();
            }
        }
    }

    /** Opens the bet amount menu over {@code over} (the board): changes the chip size. */
    void openChipMenu(com.dex.boxcasino.gui.CasinoView over) {
        openBetMenu(over);
    }

    void spin() {
        if (bet != null) {
            reject();
            return;
        }
        if (stakes.isEmpty()) {
            plugin.messages().send(player, "games.roulette.no-stakes");
            reject();
            return;
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (!mayStart()) {
            reject();
            return;
        }
        int drawn = roulette.draw(plugin.random());
        StringBuilder json = new StringBuilder("{\"number\":").append(drawn).append(",\"stakes\":{");
        boolean first = true;
        for (var e : stakes.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append('"').append(e.getKey()).append("\":").append(e.getValue());
        }
        json.append("}}");
        Bet placed = plugin.bets().place(player, game.id(), currency(), total(), json.toString());
        if (placed == null) {
            reject();
            return;
        }
        bet = placed;
        result = drawn;
        running = Map.copyOf(stakes);
        lastStakes = new LinkedHashMap<>(stakes);
        lastCurrency = currency();
        int target = RouletteGame.wheelIndex(drawn);
        totalSteps = roulette.laps * RouletteGame.WHEEL.length + Math.floorMod(target - offset, RouletteGame.WHEEL.length);
        if (totalSteps < 12) totalSteps += RouletteGame.WHEEL.length;
        step = 0;
        render();
        sound(Sound.UI_BUTTON_CLICK);
        plugin.dealers().actFor(player.getUniqueId());
        scheduleStep();
    }

    /** Fast at first, slower towards the end (1 → 6 ticks per step). */
    private void scheduleStep() {
        double t = step / (double) Math.max(1, totalSteps);
        long delay = 1 + (long) (5.0 * t * t);
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            offset = (offset + 1) % RouletteGame.WHEEL.length;
            step++;
            renderRing();
            sound(Sound.BLOCK_NOTE_BLOCK_HAT);
            if (step % 12 == 0) plugin.dealers().actFor(player.getUniqueId());
            if (step >= totalSteps) {
                task = null;
                finish(true);
            } else {
                scheduleStep();
            }
        }, delay);
    }

    private void finish(boolean visible) {
        offset = RouletteGame.wheelIndex(result);
        long payout = roulette.payout(running, result);
        plugin.bets().settle(bet, payout);
        Messages m = plugin.messages();
        String[] ph = {"number", String.valueOf(result), "color", colorName(result),
                "payout", plugin.wallet().format(bet.currency, payout), "bet", plugin.wallet().format(bet.currency, bet.amount())};
        boolean profit = payout > bet.amount();
        String key = profit ? "games.roulette.won" : payout > 0 ? "games.roulette.back" : "games.roulette.lost";
        m.send(player, key, ph);
        history.addFirst(result);
        while (history.size() > Math.max(1, roulette.historySlots.size())) history.removeLast();
        bet = null;
        running = Map.of();
        // the laid chips stay on the board for the next spin
        if (!visible) return;
        sound(profit ? Sound.ENTITY_PLAYER_LEVELUP : payout > 0 ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.BLOCK_NOTE_BLOCK_BASS);
        player.showTitle(net.kyori.adventure.title.Title.title(m.text("games.roulette.title", ph),
                m.text(key + "-subtitle", ph), net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO,
                        java.time.Duration.ofMillis(1500), java.time.Duration.ofMillis(300))));
        render();
    }

    private long lastTotal() {
        long sum = 0;
        for (long v : lastStakes.values()) sum += v;
        return sum;
    }

    String colorName(int n) {
        return plugin.messages().raw(n == 0 ? "games.roulette.color-green" : RouletteGame.isRed(n)
                ? "games.roulette.color-red" : "games.roulette.color-black");
    }

    /** ESC while the wheel turns: the GUI comes back (watch it to the end); standing up from the chair skips it. */
    @Override
    protected boolean holdOnEsc() {
        return bet != null;
    }

    @Override
    protected void onClose() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bet != null) finish(false);
    }

    @Override
    protected void redraw() {
        render();
    }

    /** Lore lines listing the laid spots ("Đỏ: 10.000$"), at most 8 then "...". */
    List<Component> stakeLines(Map<String, Long> spots, Currency c) {
        Messages m = plugin.messages();
        List<Component> out = new ArrayList<>();
        int shown = 0;
        for (var e : spots.entrySet()) {
            if (shown++ >= 8) {
                out.add(m.line("games.roulette.stake-more", "count", String.valueOf(spots.size() - 8)));
                break;
            }
            out.add(m.line("games.roulette.stake-line", "spot", spotName(e.getKey()),
                    "amount", plugin.wallet().format(c, e.getValue()),
                    "multiplier", Format.decimal(roulette.multiplier(e.getKey()))));
        }
        return out;
    }

    String spotName(String spot) {
        if (RouletteGame.isNumber(spot)) return plugin.messages().raw("games.roulette.spot-number", "number", spot);
        return plugin.messages().raw("games.roulette.spot." + spot);
    }

    void render() {
        if (isClosed()) return;
        Messages m = plugin.messages();
        renderPanel();
        renderRing();
        Currency c = currency();
        String totalText = plugin.wallet().format(c, total());
        // center: last number, spin button, history
        if (bet == null && result >= 0) {
            set(roulette.spotSlot, roulette.numberItems[result], m.line("games.roulette.last.name", "number",
                    String.valueOf(result), "color", colorName(result)), List.of(), true);
        } else {
            set(roulette.spotSlot, roulette.spotItem, m.line(bet != null ? "games.roulette.spinning.name"
                    : "games.roulette.last-none.name"), List.of());
        }
        List<Component> spinLore = new ArrayList<>();
        if (bet != null) {
            spinLore.addAll(stakeLines(running, bet.currency));
            set(roulette.leverSlot, roulette.spotItem, m.line("games.roulette.spinning.name"), spinLore);
        } else if (stakes.isEmpty()) {
            set(roulette.leverSlot, roulette.spotItem, m.line("games.roulette.lever-empty.name"),
                    m.lines("games.roulette.lever-empty.lore"));
        } else {
            spinLore.addAll(stakeLines(stakes, c));
            spinLore.addAll(m.lines("games.roulette.lever.lore", "total", totalText));
            set(roulette.leverSlot, roulette.spotItem, m.line("games.roulette.lever.name", "total", totalText), spinLore);
        }
        List<Integer> h = new ArrayList<>(history);
        for (int i = 0; i < roulette.historySlots.size(); i++) {
            int slot = roulette.historySlots.get(i);
            if (i < h.size()) {
                int n = h.get(i);
                set(slot, roulette.numberItems[n], m.line("games.roulette.history.name", "number", String.valueOf(n),
                        "color", colorName(n), "index", String.valueOf(i + 1)), List.of());
            } else {
                set(slot, roulette.spotItem, m.line("games.roulette.history-empty.name"), List.of());
            }
        }
        // right panel: board, summary, clear, repeat
        set(roulette.boardSlot, chip(c, amount(), m.line("games.roulette.board-button.name"),
                m.lines("games.roulette.board-button.lore", "chip", plugin.wallet().format(c, amount()))));
        if (stakes.isEmpty()) {
            set(roulette.summarySlot, roulette.spotItem, m.line("games.roulette.summary-empty.name"), List.of());
        } else {
            set(roulette.summarySlot, chip(c, total(), m.line("games.roulette.summary.name", "total", totalText),
                    stakeLines(stakes, c)));
        }
        set(roulette.clearSlot, roulette.clearItem, m.line("games.roulette.clear.name"), m.lines("games.roulette.clear.lore"));
        String lastText = lastStakes.isEmpty() || lastCurrency == null ? "-" : plugin.wallet().format(lastCurrency, lastTotal());
        set(roulette.repeatSlot, roulette.repeatItem, m.line("games.roulette.repeat.name"),
                m.lines(lastStakes.isEmpty() ? "games.roulette.repeat.lore-none" : "games.roulette.repeat.lore",
                        "total", lastText));
    }

    /** The 16 ring cells: a window of the wheel, the pointer cell shows the wheel number at {@link #offset}. */
    private void renderRing() {
        if (isClosed()) return;
        Messages m = plugin.messages();
        int size = roulette.ring.size();
        int len = RouletteGame.WHEEL.length;
        for (int i = 0; i < size; i++) {
            int n = RouletteGame.WHEEL[Math.floorMod(offset + i - roulette.pointer, len)];
            boolean atPointer = i == roulette.pointer;
            String key = atPointer ? (bet != null ? "games.roulette.ring-pointer-running.name" : "games.roulette.ring-pointer.name")
                    : "games.roulette.ring.name";
            set(roulette.ring.get(i), roulette.numberItems[n], m.line(key, "number", String.valueOf(n), "color", colorName(n)),
                    List.of(), atPointer);
        }
    }
}
