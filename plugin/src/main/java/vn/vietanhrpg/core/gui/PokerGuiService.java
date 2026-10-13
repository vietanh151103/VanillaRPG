package vn.vietanhrpg.core.gui;

import com.magmaguy.elitemobs.economy.EconomyHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A small one-player five-card draw table backed by EliteMobs Elite Coins. */
public final class PokerGuiService implements Listener {
    private static final int[] CARD_SLOTS = {10, 11, 12, 13, 14};
    private static final int[] BETS = {10, 50, 100, 500};
    private final JavaPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public PokerGuiService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Session old = sessions.remove(player.getUniqueId());
        if (old != null && old.dealt) {
            refund(player, old.bet);
        }
        Session session = new Session(player.getUniqueId());
        sessions.put(player.getUniqueId(), session);
        player.openInventory(render(session));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof PokerHolder holder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        Session session = sessions.get(player.getUniqueId());
        if (session == null || session != holder.session) return;
        int slot = event.getRawSlot();
        if (slot == 26) {
            player.closeInventory();
            return;
        }
        if (slot == 16) {
            if (!session.dealt) deal(player, session);
            else draw(player, session);
            return;
        }
        if (!session.dealt) {
            for (int i = 0; i < BETS.length; i++) {
                if (slot == 21 + i) {
                    session.bet = BETS[i];
                    player.openInventory(render(session));
                    return;
                }
            }
        } else {
            for (int i = 0; i < CARD_SLOTS.length; i++) {
                if (slot == CARD_SLOTS[i]) {
                    session.held[i] = !session.held[i];
                    player.openInventory(render(session));
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof PokerHolder)) return;
        Session session = sessions.remove(player.getUniqueId());
        if (session != null && session.dealt) {
            refund(player, session.bet);
            player.sendMessage(Component.text("Poker closed; your bet was returned.", NamedTextColor.YELLOW));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null && session.dealt) refund(event.getPlayer(), session.bet);
    }

    private void deal(Player player, Session session) {
        if (!EconomyHandler.isReady(player.getUniqueId())) {
            player.sendMessage(Component.text("EliteMobs currency data is still loading.", NamedTextColor.RED));
            return;
        }
        if (!EconomyHandler.tryWithdraw(player.getUniqueId(), session.bet)) {
            player.sendMessage(Component.text("You do not have enough Elite Coins.", NamedTextColor.RED));
            return;
        }
        session.deck = newDeck();
        session.hand.clear();
        for (int i = 0; i < 5; i++) session.hand.add(session.deck.remove(0));
        session.dealt = true;
        player.openInventory(render(session));
    }

    private void draw(Player player, Session session) {
        for (int i = 0; i < 5; i++) {
            if (!session.held[i]) session.hand.set(i, session.deck.remove(0));
        }
        HandResult result = evaluate(session.hand);
        long payout = Math.round(session.bet * result.multiplier);
        if (payout > 0) EconomyHandler.addCurrency(player.getUniqueId(), payout);
        player.sendMessage(Component.text(result.name + " - payout: " + payout + " Elite Coins", result.color));
        sessions.remove(player.getUniqueId());
        player.closeInventory();
    }

    private Inventory render(Session session) {
        Inventory inventory = plugin.getServer().createInventory(new PokerHolder(session), 27,
                Component.text("Poker - Elite Coins", NamedTextColor.DARK_GREEN));
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
        for (int i = 0; i < session.hand.size(); i++) {
            Card card = session.hand.get(i);
            String prefix = session.held[i] ? "[HELD] " : "";
            inventory.setItem(CARD_SLOTS[i], cardItem(card, prefix, session.held[i]));
        }
        inventory.setItem(16, item(session.dealt ? Material.LIME_DYE : Material.EMERALD,
                session.dealt ? "Draw cards" : "Deal (" + session.bet + " Elite Coins)", NamedTextColor.GREEN));
        if (!session.dealt) {
            for (int i = 0; i < BETS.length; i++) {
                inventory.setItem(21 + i, item(Material.GOLD_NUGGET, "Bet: " + BETS[i] + " Elite Coins",
                        session.bet == BETS[i] ? NamedTextColor.YELLOW : NamedTextColor.GOLD));
            }
        } else {
            inventory.setItem(22, item(Material.YELLOW_DYE, "Click cards to hold them", NamedTextColor.YELLOW));
        }
        inventory.setItem(26, item(Material.BARRIER, "Close", NamedTextColor.RED));
        return inventory;
    }

    private static List<Card> newDeck() {
        List<Card> deck = new ArrayList<>();
        for (Suit suit : Suit.values()) for (int rank = 2; rank <= 14; rank++) deck.add(new Card(rank, suit));
        Collections.shuffle(deck);
        return deck;
    }

    private static HandResult evaluate(List<Card> hand) {
        int[] counts = new int[15];
        boolean flush = hand.stream().allMatch(card -> card.suit == hand.get(0).suit);
        for (Card card : hand) counts[card.rank]++;
        boolean straight = true;
        int distinct = 0;
        int high = 0;
        for (int rank = 2; rank <= 14; rank++) {
            if (counts[rank] > 0) { distinct++; high = rank; }
        }
        if (distinct != 5) straight = false;
        else {
            int min = 0;
            for (int rank = 2; rank <= 14; rank++) if (counts[rank] > 0) { min = rank; break; }
            straight = high - min == 4 || (counts[14] == 1 && counts[2] == 1 && counts[3] == 1 && counts[4] == 1 && counts[5] == 1);
        }
        int pairs = 0, trips = 0, quads = 0;
        for (int count : counts) {
            if (count == 2) pairs++;
            if (count == 3) trips++;
            if (count == 4) quads++;
        }
        if (straight && flush && high == 14) return new HandResult("Royal flush", 100, NamedTextColor.LIGHT_PURPLE);
        if (straight && flush) return new HandResult("Straight flush", 50, NamedTextColor.LIGHT_PURPLE);
        if (quads == 1) return new HandResult("Four of a kind", 20, NamedTextColor.DARK_PURPLE);
        if (trips == 1 && pairs == 1) return new HandResult("Full house", 9, NamedTextColor.GOLD);
        if (flush) return new HandResult("Flush", 7, NamedTextColor.AQUA);
        if (straight) return new HandResult("Straight", 5, NamedTextColor.GREEN);
        if (trips == 1) return new HandResult("Three of a kind", 3, NamedTextColor.GREEN);
        if (pairs == 2) return new HandResult("Two pair", 2, NamedTextColor.YELLOW);
        if (pairs == 1) return new HandResult("Pair", 1, NamedTextColor.YELLOW);
        return new HandResult("No winning hand", 0, NamedTextColor.GRAY);
    }

    private void refund(Player player, long amount) {
        if (amount > 0 && EconomyHandler.isReady(player.getUniqueId())) EconomyHandler.addCurrency(player.getUniqueId(), amount);
    }

    private static ItemStack item(Material material, String name, NamedTextColor color) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color).decorate(TextDecoration.BOLD));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack cardItem(Card card, String prefix, boolean held) {
        Material icon = switch (card.suit) {
            case HEARTS -> Material.RED_DYE;
            case DIAMONDS -> Material.REDSTONE;
            case CLUBS -> Material.COAL;
            case SPADES -> Material.LAPIS_LAZULI;
        };
        ItemStack stack = new ItemStack(icon);
        ItemMeta meta = stack.getItemMeta();
        NamedTextColor color = held ? NamedTextColor.YELLOW : card.color();
        meta.displayName(Component.text(prefix + card.label(), color).decorate(TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text("Rank: " + Card.rankName(card.rank), NamedTextColor.GRAY),
                Component.text("Suit: " + card.suit.name, card.color()),
                Component.text(held ? "Held - click to release" : "Click to hold this card", NamedTextColor.YELLOW)
        ));
        // PokerPack_v1.3 supplies these models. Keep it opt-in until the pack
        // has been merged into the server resource pack sent to clients.
        if (plugin.getConfig().getBoolean("poker.item-models-enabled", false)) {
            meta.setItemModel(new NamespacedKey("pokerpack", "card_" + card.modelCode()));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private enum Suit {
        HEARTS("Hearts", "h", NamedTextColor.RED), DIAMONDS("Diamonds", "d", NamedTextColor.RED),
        CLUBS("Clubs", "c", NamedTextColor.BLACK), SPADES("Spades", "s", NamedTextColor.BLACK);
        final String name; final String code; final NamedTextColor color;
        Suit(String name, String code, NamedTextColor color) { this.name = name; this.code = code; this.color = color; }
    }

    private record Card(int rank, Suit suit) {
        String label() { return rankName(rank) + " of " + suit.name; }
        String modelCode() { return suit.code + rankCode(rank); }
        NamedTextColor color() { return suit.color == NamedTextColor.BLACK ? NamedTextColor.DARK_GRAY : suit.color; }
        private static String rankCode(int rank) {
            return switch (rank) { case 14 -> "a"; case 13 -> "k"; case 12 -> "q"; case 11 -> "j"; default -> String.valueOf(rank); };
        }
        private static String rankName(int rank) {
            return switch (rank) { case 14 -> "Ace"; case 13 -> "King"; case 12 -> "Queen"; case 11 -> "Jack"; default -> String.valueOf(rank); };
        }
    }

    private static final class Session {
        final UUID player; long bet = 10; boolean dealt; boolean[] held = new boolean[5];
        List<Card> deck = new ArrayList<>(); List<Card> hand = new ArrayList<>();
        Session(UUID player) { this.player = player; }
    }

    private record HandResult(String name, double multiplier, NamedTextColor color) { }

    private record PokerHolder(Session session) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
