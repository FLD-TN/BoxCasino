package com.dex.boxcasino.game.xidach;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.chat.ChatRoom;
import com.dex.boxcasino.economy.Currency;
import com.dex.boxcasino.extra.Countdown;
import com.dex.boxcasino.game.Bet;
import com.dex.boxcasino.util.Payouts;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * One xì dách table. WAITING → BETTING (countdown from the first bet) → PLAYING (simultaneous action rounds; a timeout
 * or a closed GUI plays automatically: stand from 16, otherwise hit) → DEALER (draws below 16) → RESULT → WAITING.
 * The deck is shuffled when the hand starts and its order is stored in the ledger. Main thread only.
 */
final class XiDachTable implements ChatRoom {

    enum Phase { WAITING, BETTING, PLAYING, DEALER, RESULT }

    enum Action { HIT, STAND, DOUBLE }

    static final class Seat {
        final UUID player;
        final String name;
        Bet bet;
        final List<Card> cards = new ArrayList<>();
        boolean done;
        boolean firstAction = true;
        boolean surrendered;
        boolean doubled;
        Action pending;
        Hand.Outcome outcome;
        long payout;

        Seat(UUID player, String name) {
            this.player = player;
            this.name = name;
        }

        boolean playing() {
            return bet != null && !done;
        }
    }

    private final BoxCasinoPlugin plugin;
    private final XiDachGame game;
    final int number;
    /** Machine in the world this table belongs to (players sit on its chairs), null for a menu table. */
    final String machine;
    final Seat[] seats;
    final List<Card> dealer = new ArrayList<>();
    private List<Card> deck = new ArrayList<>();
    Phase phase = Phase.WAITING;
    int secondsLeft;
    boolean dealerRevealed;
    private final Set<XiDachView> views = new HashSet<>();
    private BukkitTask task;
    private int ticks;
    private long lastAnnounce;

    XiDachTable(BoxCasinoPlugin plugin, XiDachGame game, int number) {
        this(plugin, game, number, null);
    }

    XiDachTable(BoxCasinoPlugin plugin, XiDachGame game, int number, String machine) {
        this.plugin = plugin;
        this.game = game;
        this.number = number;
        this.machine = machine;
        this.seats = new Seat[game.seatSlots.size()];
    }

    int seatOf(UUID player) {
        for (int i = 0; i < seats.length; i++) if (seats[i] != null && seats[i].player.equals(player)) return i;
        return -1;
    }

    boolean hasFreeSeat() {
        for (Seat s : seats) if (s == null) return true;
        return false;
    }

    boolean isEmpty() {
        for (Seat s : seats) if (s != null) return false;
        return views.isEmpty();
    }

    boolean inHand(UUID player) {
        int i = seatOf(player);
        return i >= 0 && seats[i].bet != null && seats[i].bet.isOpen();
    }

    /** Seats the viewer (on {@code preferred} when free, else any free seat) and registers the view. */
    void join(XiDachView view, Player player, int preferred) {
        views.add(view);
        if (seatOf(player.getUniqueId()) >= 0) return;
        if (preferred >= 0 && preferred < seats.length && seats[preferred] == null) {
            seats[preferred] = new Seat(player.getUniqueId(), player.getName());
            refreshAll();
            return;
        }
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] == null) {
                seats[i] = new Seat(player.getUniqueId(), player.getName());
                break;
            }
        }
        refreshAll();
    }

    void leave(XiDachView view, UUID player) {
        views.remove(view);
        int i = seatOf(player);
        if (i >= 0 && seats[i].bet == null && !present(player)) seats[i] = null;
        refreshAll();
        game.removeIfEmpty(this);
    }

    /**
     * The player left the table. A menu table keeps a seat with a bet until the hand ends (the game plays for them).
     * At a machine, standing up gives up: during the betting countdown the bet is refunded, once the cards are dealt the
     * bet is lost.
     */
    void standUp(UUID player) {
        int i = seatOf(player);
        if (i < 0) return;
        Seat s = seats[i];
        if (s.bet != null && s.bet.isOpen()) {
            if (machine == null) return;
            Player p = Bukkit.getPlayer(player);
            if (phase == Phase.BETTING || phase == Phase.WAITING) {
                plugin.bets().refund(s.bet);
                if (p != null) plugin.messages().send(p, "games.xidach.stood-refund");
            } else {
                plugin.bets().settle(s.bet, 0);
                if (p != null) plugin.messages().send(p, "games.xidach.stood-lost",
                        "bet", plugin.wallet().format(s.bet.currency, s.bet.amount()));
            }
        }
        seats[i] = null;
        if (phase == Phase.BETTING && bettors().isEmpty()) {
            phase = Phase.WAITING;
            stopTask();
        } else if (phase == Phase.PLAYING && allDone()) {
            startDealer();
        }
        refreshAll();
        game.removeIfEmpty(this);
    }

    /** Still at the table: on a chair of the machine, or (menu table) looking at it. */
    private boolean present(UUID player) {
        return machine != null ? plugin.seats().seatedAt(player, machine) : viewing(player);
    }

    private boolean viewing(UUID player) {
        for (XiDachView v : views) if (v.player().getUniqueId().equals(player)) return true;
        return false;
    }

    boolean placeBet(Player player, Currency currency, long amount) {
        int i = seatOf(player.getUniqueId());
        if (i < 0 || seats[i].bet != null || (phase != Phase.WAITING && phase != Phase.BETTING)) return false;
        Bet bet = plugin.bets().place(player, game.id(), currency, amount, null);
        if (bet == null) return false;
        seats[i].bet = bet;
        if (phase == Phase.WAITING) {
            phase = Phase.BETTING;
            secondsLeft = game.bettingSeconds;
            startTask();
            long now = System.currentTimeMillis();
            if (now - lastAnnounce >= plugin.access().announceCooldownMillis()) {
                lastAnnounce = now;
                int free = 0;
                for (Seat s : seats) if (s == null) free++;
                plugin.access().announceInCasino(player, "games.xidach.broadcast-start", "player", player.getName(),
                        "table", String.valueOf(number), "seconds", String.valueOf(game.bettingSeconds),
                        "seats", plugin.machines().freeText(free, seats.length));
            }
        }
        refreshAll();
        return true;
    }

    boolean cancelBet(Player player) {
        int i = seatOf(player.getUniqueId());
        if (i < 0 || seats[i].bet == null || phase != Phase.BETTING) return false;
        plugin.bets().refund(seats[i].bet);
        seats[i].bet = null;
        if (bettors().isEmpty()) {
            phase = Phase.WAITING;
            stopTask();
        }
        refreshAll();
        return true;
    }

    private List<Seat> bettors() {
        List<Seat> out = new ArrayList<>();
        for (Seat s : seats) if (s != null && s.bet != null) out.add(s);
        return out;
    }

    /** A button press. Returns false when the action is not allowed now (caller plays a reject sound). */
    boolean act(Player player, String action) {
        int i = seatOf(player.getUniqueId());
        if (phase != Phase.PLAYING || i < 0) return false;
        Seat s = seats[i];
        if (!s.playing() || s.pending != null) return false;
        switch (action) {
            case "hit" -> {
                if (s.cards.size() >= 5) return false;
                s.pending = Action.HIT;
            }
            case "stand" -> {
                if (Hand.total(s.cards) < game.minStand) return false;
                s.pending = Action.STAND;
            }
            case "double" -> {
                if (!s.firstAction || s.cards.size() != 2) return false;
                if (!plugin.bets().raise(player, s.bet, s.bet.amount())) return false;
                s.doubled = true;
                s.pending = Action.DOUBLE;
            }
            case "surrender" -> {
                if (!s.firstAction) return false;
                s.surrendered = true;
                s.done = true;
            }
            default -> {
                return false;
            }
        }
        boolean waiting = false;
        for (Seat o : seats) if (o != null && o.playing() && o.pending == null) waiting = true;
        if (!waiting) resolveRound();
        else refreshAll();
        return true;
    }

    private void startTask() {
        if (task != null) return;
        ticks = 0;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        ticks++;
        if (phase == Phase.DEALER) {
            if (ticks % game.dealerStepTicks == 0) dealerStep();
            return;
        }
        if (ticks % 20 != 0) return;
        secondsLeft--;
        switch (phase) {
            case BETTING -> {
                if (secondsLeft <= 0) {
                    startHand();
                } else {
                    Countdown.start(plugin, members(), secondsLeft, "games.xidach.countdown-deal",
                            "table", String.valueOf(number));
                    refreshTimers();
                }
            }
            case PLAYING -> {
                if (secondsLeft <= 0) {
                    resolveRound();
                } else {
                    List<UUID> undecided = new ArrayList<>();
                    for (Seat s : seats) if (s != null && s.playing() && s.pending == null) undecided.add(s.player);
                    Countdown.turn(plugin, undecided, secondsLeft, "games.xidach.countdown-turn");
                    refreshTimers();
                }
            }
            case RESULT -> {
                if (secondsLeft <= 0) reset();
            }
            default -> stopTask();
        }
    }

    private void startHand() {
        deck = Card.shuffledDeck(plugin.random());
        String outcome = deck.stream().map(Card::toString).collect(Collectors.joining(",", "{\"deck\":\"", "\"}"));
        dealer.clear();
        dealerRevealed = false;
        for (Seat s : bettors()) {
            plugin.bets().setOutcome(s.bet, outcome);
            s.cards.clear();
            s.cards.add(draw());
            s.cards.add(draw());
            s.done = Hand.isXiBan(s.cards) || Hand.isXiDach(s.cards);
        }
        dealer.add(draw());
        dealer.add(draw());
        phase = Phase.PLAYING;
        secondsLeft = game.turnSeconds;
        sound(Sound.ITEM_BOOK_PAGE_TURN);
        if (Hand.isXiBan(dealer) || Hand.isXiDach(dealer)) {
            dealerRevealed = true;
            tell("games.xidach.dealer-special", "hand", plugin.messages().raw(Hand.isXiBan(dealer)
                    ? "games.xidach.rank.xi_ban" : "games.xidach.rank.xi_dach"));
            settleAll();
            return;
        }
        if (allDone()) startDealer();
        else refreshAll();
    }

    private Card draw() {
        if (machine != null) plugin.dealers().act(machine);
        return deck.remove(0);
    }

    private boolean allDone() {
        for (Seat s : seats) if (s != null && s.playing()) return false;
        return true;
    }

    /** Applies every pending action (timeouts and closed GUIs play automatically) and deals the cards. */
    private void resolveRound() {
        for (Seat s : seats) {
            if (s == null || !s.playing()) continue;
            Action a = s.pending != null ? s.pending : Hand.total(s.cards) >= game.minStand ? Action.STAND : Action.HIT;
            if (a == Action.STAND) {
                s.done = true;
            } else {
                s.cards.add(draw());
                if (a == Action.DOUBLE) s.done = true;
                int total = Hand.total(s.cards);
                if (total > 21 || s.cards.size() >= 5) s.done = true;
            }
            s.firstAction = false;
            s.pending = null;
        }
        sound(Sound.ITEM_BOOK_PAGE_TURN);
        secondsLeft = game.turnSeconds;
        if (allDone()) startDealer();
        else refreshAll();
    }

    private void startDealer() {
        phase = Phase.DEALER;
        dealerRevealed = true;
        ticks = 0;
        refreshAll();
    }

    private void dealerStep() {
        if (dealer.size() < 5 && Hand.total(dealer) < game.dealerMinStand) {
            dealer.add(draw());
            sound(Sound.ITEM_BOOK_PAGE_TURN);
            refreshAll();
            return;
        }
        settleAll();
    }

    private void settleAll() {
        for (Seat s : seats) {
            if (s == null || s.bet == null || !s.bet.isOpen()) continue;
            long amount = s.bet.amount();
            if (s.surrendered) {
                s.outcome = Hand.Outcome.LOSE;
                s.payout = Payouts.payout(amount, game.paySurrender);
            } else {
                s.outcome = Hand.compare(s.cards, dealer, game.minStand, game.bothBustDraw);
                s.payout = switch (s.outcome) {
                    case WIN -> Payouts.payout(amount, game.winMultiplier(Hand.rank(s.cards, game.minStand)));
                    case DRAW -> amount;
                    case LOSE -> 0;
                };
            }
            plugin.bets().settle(s.bet, s.payout);
            Player p = Bukkit.getPlayer(s.player);
            if (p != null) {
                String key = s.surrendered ? "games.xidach.surrendered" : switch (s.outcome) {
                    case WIN -> "games.xidach.won";
                    case DRAW -> "games.xidach.draw";
                    case LOSE -> "games.xidach.lost";
                };
                plugin.messages().send(p, key, "you", describe(s.cards, game.minStand), "dealer", describe(dealer, 0),
                        "payout", plugin.wallet().format(s.bet.currency, s.payout),
                        "bet", plugin.wallet().format(s.bet.currency, amount));
            }
        }
        phase = Phase.RESULT;
        secondsLeft = game.resultSeconds;
        ticks = 0;
        dealerRevealed = true;
        sound(Sound.ENTITY_PLAYER_LEVELUP);
        refreshAll();
    }

    /** "19 điểm", "Xì Bàn", "Quắc (24)"... */
    String describe(List<Card> cards, int minStand) {
        Hand.Rank r = Hand.rank(cards, minStand);
        return plugin.messages().raw("games.xidach.rank." + r.name().toLowerCase(java.util.Locale.ROOT),
                "total", String.valueOf(Hand.total(cards)));
    }

    private void reset() {
        stopTask();
        phase = Phase.WAITING;
        dealer.clear();
        dealerRevealed = false;
        for (int i = 0; i < seats.length; i++) {
            Seat s = seats[i];
            if (s == null) continue;
            // players still at the table keep their seat for the next hand
            seats[i] = present(s.player) ? new Seat(s.player, s.name) : null;
        }
        refreshAll();
        game.removeIfEmpty(this);
    }

    /** Plugin disable / reload: hands are not decided, every open bet is refunded. */
    void shutdown() {
        stopTask();
        for (Seat s : seats) {
            if (s != null && s.bet != null && s.bet.isOpen()) {
                plugin.bets().refund(s.bet);
                Player p = Bukkit.getPlayer(s.player);
                if (p != null) plugin.messages().send(p, "games.xidach.cancelled-shutdown");
            }
        }
        views.clear();
    }

    private void sound(Sound sound) {
        for (Seat s : seats) {
            if (s == null) continue;
            Player p = Bukkit.getPlayer(s.player);
            if (p != null && plugin.gameSounds()) p.playSound(p.getLocation(), sound, 0.6f, 1f);
        }
    }

    private void tell(String path, String... ph) {
        for (Seat s : seats) {
            if (s == null || s.bet == null) continue;
            Player p = Bukkit.getPlayer(s.player);
            if (p != null) plugin.messages().send(p, path, ph);
        }
    }

    @Override
    public java.util.Collection<UUID> members() {
        List<UUID> out = new ArrayList<>();
        for (Seat s : seats) if (s != null) out.add(s.player);
        return out;
    }

    @Override
    public void refreshViews() {
        refreshAll();
    }

    @Override
    public void reopen(Player player) {
        new XiDachView(plugin, player, game, this, false).open();
    }

    private void refreshAll() {
        for (XiDachView v : List.copyOf(views)) v.refresh();
    }

    private void refreshTimers() {
        for (XiDachView v : List.copyOf(views)) v.refreshTimer();
    }
}
