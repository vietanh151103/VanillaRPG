package vn.vietanhrpg.core.service;

import com.magmaguy.elitemobs.economy.EconomyHandler;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Texas Hold'em table rendered in a dedicated poker world. */
public final class PokerTableService implements Listener {
    private static final String WORLD_NAME = "poker_room";
    private static final int MAX_PLAYERS = 6;
    private static final long SMALL_BLIND = 10;
    private static final long BIG_BLIND = 20;
    private static final long TURN_TIME_SECONDS = 60;
    private static final NamespacedKey ACTION_KEY = new NamespacedKey("vanillarpg", "poker_action");
    private final JavaPlugin plugin;
    private final Map<UUID, Seat> seats = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, InventorySnapshot> savedInventories = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingTeleports = new HashMap<>();
    private final Map<UUID, Integer> pendingSeats = new HashMap<>();
    private final Set<UUID> awaitingCustomRaise = new HashSet<>();
    private final List<Entity> cardDisplays = new ArrayList<>();
    private World world;
    private boolean displayCleanupDone;
    private boolean layoutBuilt;
    private final BukkitTask bossBarTask;
    private Phase phase = Phase.WAITING;
    private List<Card> deck = new ArrayList<>();
    private final List<Card> board = new ArrayList<>();
    private final Map<UUID, Long> committed = new HashMap<>();
    private final Set<UUID> acted = new HashSet<>();
    private long pot;
    private long currentBet;
    private UUID turn;
    private long turnStartedAt;
    private int dealerIndex;

    public PokerTableService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.bossBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateBossBars, 1L, 5L);
    }

    public void enter(Player player) {
        World target = ensureWorld();
        if (target == null) return;
        if (seats.containsKey(player.getUniqueId())) {
            player.teleport(spawnFor(seats.get(player.getUniqueId()).seat));
            return;
        }
        if (phase != Phase.WAITING) {
            player.sendMessage(Component.text("A hand is already running. Wait for the next hand.", NamedTextColor.YELLOW));
            player.teleport(target.getSpawnLocation());
            return;
        }
        if (seats.size() + pendingSeats.size() >= MAX_PLAYERS) {
            player.sendMessage(Component.text("The poker table is full (6 players).", NamedTextColor.RED));
            player.teleport(target.getSpawnLocation());
            return;
        }
        int seat = firstFreeSeat();
        startTeleportCountdown(player, seat);
    }

    private void startTeleportCountdown(Player player, int seat) {
        cancelPendingTeleport(player, false);
        pendingSeats.put(player.getUniqueId(), seat);
        File combatTag = new File(plugin.getDataFolder().getParentFile(), "EliteMobs/CombatTag.yml");
        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(combatTag);
        if (!yaml.getBoolean("enableAdventurersGuildTeleportTimer", true)) { joinNow(player, seat); return; }
        final int[] remaining = {5};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) { cancelPendingTeleport(player, false); return; }
            if (remaining[0] <= 0) {
                pendingTeleports.remove(player.getUniqueId());
                pendingSeats.remove(player.getUniqueId());
                task[0].cancel();
                joinNow(player, seat);
                return;
            }
            String message = yaml.getString("teleportTimeLeft", "&7[EM] Teleporting in &a$time &7seconds...")
                    .replace("$time", String.valueOf(remaining[0])).replace('&', '§');
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(message));
            remaining[0]--;
        }, 0L, 20L);
        pendingTeleports.put(player.getUniqueId(), task[0]);
    }

    private void joinNow(Player player, int seat) {
        if (!player.isOnline() || seats.containsKey(player.getUniqueId())) return;
        pendingSeats.remove(player.getUniqueId());
        returnLocations.put(player.getUniqueId(), player.getLocation().clone());
        seats.put(player.getUniqueId(), new Seat(player.getUniqueId(), seat));
        createBossBar(player);
        saveAndPrepareInventory(player);
        player.teleport(spawnFor(seat));
        player.sendMessage(Component.text("You joined seat " + (seat + 1) + ". Use /poker start when everyone is ready.", NamedTextColor.GREEN));
        broadcast(player.getName() + " joined the poker table (" + seats.size() + "/" + MAX_PLAYERS + ").");
    }

    private void cancelPendingTeleport(Player player, boolean notify) {
        BukkitTask task = pendingTeleports.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            pendingSeats.remove(player.getUniqueId());
            if (notify) player.sendActionBar(Component.text("[EM] Teleport interrupted!", NamedTextColor.RED));
        }
    }

    public boolean command(Player player, String[] args) {
        if (args.length == 0) { enter(player); return true; }
        String action = args[0].toLowerCase(java.util.Locale.ROOT);
        if (action.equals("leave")) { leave(player); return true; }
        if (action.equals("start")) { start(player); return true; }
        if (!seats.containsKey(player.getUniqueId())) { enter(player); return true; }
        if (phase == Phase.WAITING) {
            player.sendMessage(Component.text("Waiting for the host to start: /poker start", NamedTextColor.YELLOW));
            return true;
        }
        if (!player.getUniqueId().equals(turn)) {
            player.sendMessage(Component.text("It is not your turn.", NamedTextColor.RED));
            return true;
        }
        switch (action) {
            case "fold" -> fold(player);
            case "check" -> check(player);
            case "call" -> call(player);
            case "raise" -> raise(player, args);
            case "status" -> status(player);
            case "hand" -> showHand(player);
            default -> player.sendMessage(Component.text("Use /poker fold, check, call, raise <amount>, status or leave.", NamedTextColor.YELLOW));
        }
        return true;
    }

    private void start(Player player) {
        if (!seats.containsKey(player.getUniqueId())) { enter(player); return; }
        if (phase != Phase.WAITING) { player.sendMessage(Component.text("A hand is already running.", NamedTextColor.YELLOW)); return; }
        if (seats.size() < 2) { player.sendMessage(Component.text("At least two players are required.", NamedTextColor.RED)); return; }
        resetHand();
        List<Seat> order = activeSeats();
        Seat small = order.get(dealerIndex + 1 >= order.size() ? 0 : dealerIndex + 1);
        Seat big = order.get(dealerIndex + 2 >= order.size() ? (dealerIndex + 2) % order.size() : dealerIndex + 2);
        if (!payBlind(small, SMALL_BLIND) || !payBlind(big, BIG_BLIND)) {
            if (committed.getOrDefault(small.uuid, 0L) >= SMALL_BLIND) EconomyHandler.addCurrency(small.uuid, SMALL_BLIND);
            phase = Phase.WAITING;
            pot = 0;
            player.sendMessage(Component.text("Every player needs enough " + currencyName() + " for the blinds.", NamedTextColor.RED));
            return;
        }
        currentBet = BIG_BLIND;
        dealHoleCards();
        phase = Phase.PREFLOP;
        turn = order.get((dealerIndex + 3) % order.size()).uuid;
        renderBoard();
        broadcast("Texas Hold'em started. Blinds: 10/20 " + currencyName() + ". " + nameOf(turn) + " acts first.");
        notifyTurns();
    }

    private void resetHand() {
        phase = Phase.WAITING; deck = newDeck(); board.clear(); committed.clear(); acted.clear(); pot = 0; currentBet = 0; turn = null; turnStartedAt = 0L;
        for (Seat seat : seats.values()) { seat.hole.clear(); seat.folded = false; }
        clearCards();
    }

    private void dealHoleCards() {
        for (Seat seat : activeSeats()) {
            seat.hole.add(deck.remove(0)); seat.hole.add(deck.remove(0));
            committed.put(seat.uuid, 0L);
            Player p = Bukkit.getPlayer(seat.uuid);
            if (p != null) { showHand(p); refreshPrivateInventory(p); }
        }
        Seat dealer = activeSeats().get(dealerIndex % activeSeats().size());
        dealerIndex = (dealerIndex + 1) % activeSeats().size();
    }

    private boolean payBlind(Seat seat, long amount) {
        if (!EconomyHandler.isReady(seat.uuid) || !EconomyHandler.tryWithdraw(seat.uuid, amount)) return false;
        committed.put(seat.uuid, amount); pot += amount; return true;
    }

    private void fold(Player player) { seats.get(player.getUniqueId()).folded = true; acted.add(player.getUniqueId()); broadcast(player.getName() + " folded."); advanceTurn(); }
    private void check(Player player) {
        long have = committed.getOrDefault(player.getUniqueId(), 0L);
        if (have < currentBet) { player.sendMessage(Component.text("You must call " + (currentBet - have) + " or fold.", NamedTextColor.RED)); return; }
        acted.add(player.getUniqueId()); broadcast(player.getName() + " checked."); advanceTurn();
    }
    private void call(Player player) {
        long amount = currentBet - committed.getOrDefault(player.getUniqueId(), 0L);
        if (!pay(player, amount)) return;
        acted.add(player.getUniqueId()); broadcast(player.getName() + " called."); advanceTurn();
    }
    private void raise(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(Component.text("Usage: /poker raise <total bet>", NamedTextColor.YELLOW)); return; }
        long total; try { total = Long.parseLong(args[1]); } catch (NumberFormatException e) { player.sendMessage(Component.text("Raise must be a number.", NamedTextColor.RED)); return; }
        if (total <= currentBet) { player.sendMessage(Component.text("Raise above the current bet of " + currentBet + ".", NamedTextColor.RED)); return; }
        long amount = total - committed.getOrDefault(player.getUniqueId(), 0L);
        if (!pay(player, amount)) return;
        currentBet = total; acted.clear(); acted.add(player.getUniqueId()); broadcast(player.getName() + " raised to " + total + "."); advanceTurn();
    }
    private boolean pay(Player p, long amount) { if (amount <= 0) return true; if (!EconomyHandler.isReady(p.getUniqueId()) || !EconomyHandler.tryWithdraw(p.getUniqueId(), amount)) { p.sendMessage(Component.text("Not enough " + currencyName() + ".", NamedTextColor.RED)); return false; } committed.merge(p.getUniqueId(), amount, Long::sum); pot += amount; return true; }
    private void advanceTurn() {
        List<Seat> active = activeSeats();
        if (active.isEmpty()) { phase = Phase.WAITING; pot = 0; clearCards(); return; }
        if (active.size() == 1) { finish(active.get(0)); return; }
        if (acted.containsAll(active.stream().map(s -> s.uuid).toList()) && active.stream().allMatch(s -> committed.getOrDefault(s.uuid, 0L) == currentBet)) { nextStreet(); return; }
        int index = indexOf(turn, active);
        for (int i = 1; i <= active.size(); i++) { Seat candidate = active.get((index + i) % active.size()); if (!acted.contains(candidate.uuid) || committed.getOrDefault(candidate.uuid, 0L) < currentBet) { turn = candidate.uuid; notifyTurns(); return; } }
    }
    private void nextStreet() {
        acted.clear(); currentBet = 0; committed.replaceAll((id, amount) -> 0L);
        if (phase == Phase.PREFLOP) { burn(); board.add(deck.remove(0)); board.add(deck.remove(0)); board.add(deck.remove(0)); phase = Phase.FLOP; }
        else if (phase == Phase.FLOP) { burn(); board.add(deck.remove(0)); phase = Phase.TURN; }
        else if (phase == Phase.TURN) { burn(); board.add(deck.remove(0)); phase = Phase.RIVER; }
        else { showdown(); return; }
        renderBoard(); broadcast("" + phase.name() + " dealt. " + pot + " " + currencyName() + " in the pot."); turn = activeSeats().get(0).uuid; notifyTurns();
    }
    private void burn() { if (!deck.isEmpty()) deck.remove(0); }
    private void showdown() {
        renderBoard();
        List<Seat> active = activeSeats();
        if (active.isEmpty()) { phase = Phase.WAITING; return; }
        Seat winner = active.get(0); long share = pot / active.size();
        for (Seat seat : active) { if (handScore(seat) > handScore(winner)) winner = seat; }
        EconomyHandler.addCurrency(winner.uuid, pot); broadcast(nameOf(winner.uuid) + " wins " + pot + " " + currencyName() + ". Board: " + boardLabels());
        phase = Phase.WAITING; pot = 0; clearCards();
    }
    private void finish(Seat winner) { EconomyHandler.addCurrency(winner.uuid, pot); broadcast(nameOf(winner.uuid) + " wins " + pot + " " + currencyName() + " - everyone else folded."); phase = Phase.WAITING; pot = 0; clearCards(); }

    private String currencyName() {
        String configured = plugin.getConfig().getString("economy.currency-name", "$");
        return configured == null || configured.isBlank() ? "$" : configured.trim();
    }

    private long handScore(Seat seat) {
        List<Card> all = new ArrayList<>(seat.hole); all.addAll(board);
        if (all.size() < 5) return 0;
        long best = 0;
        for (int a = 0; a < all.size() - 4; a++) for (int b = a + 1; b < all.size() - 3; b++)
            for (int c = b + 1; c < all.size() - 2; c++) for (int d = c + 1; d < all.size() - 1; d++)
                for (int e = d + 1; e < all.size(); e++) best = Math.max(best, fiveCardScore(List.of(all.get(a), all.get(b), all.get(c), all.get(d), all.get(e))));
        return best;
    }
    private long fiveCardScore(List<Card> cards) {
        int[] counts = new int[15]; boolean flush = cards.stream().allMatch(c -> c.suit == cards.get(0).suit);
        for (Card card : cards) counts[card.rank]++;
        List<Integer> ranks = new ArrayList<>(); for (int r = 14; r >= 2; r--) if (counts[r] > 0) ranks.add(r);
        int straightHigh = 0; if (ranks.size() == 5) { if (ranks.get(0) - ranks.get(4) == 4) straightHigh = ranks.get(0); else if (ranks.equals(List.of(14, 5, 4, 3, 2))) straightHigh = 5; }
        List<Integer> groups = new ArrayList<>(); for (int r = 14; r >= 2; r--) if (counts[r] > 0) groups.add(r);
        groups.sort((x, y) -> counts[y] != counts[x] ? Integer.compare(counts[y], counts[x]) : Integer.compare(y, x));
        int category = straightHigh > 0 && flush ? 8 : counts[groups.get(0)] == 4 ? 7 : counts[groups.get(0)] == 3 && counts[groups.get(1)] == 2 ? 6 : flush ? 5 : straightHigh > 0 ? 4 : counts[groups.get(0)] == 3 ? 3 : counts[groups.get(0)] == 2 && counts[groups.get(1)] == 2 ? 2 : counts[groups.get(0)] == 2 ? 1 : 0;
        List<Integer> tie = new ArrayList<>(); if (category == 8 || category == 4) tie.add(straightHigh); else for (int r : groups) for (int i = 0; i < counts[r]; i++) tie.add(r);
        long score = category; for (int r : tie) score = score * 15 + r; return score;
    }
    private void notifyTurns() {
        turnStartedAt = System.currentTimeMillis();
        if (turn != null && Bukkit.getPlayer(turn) != null) {
            Player player = Bukkit.getPlayer(turn);
            showHand(player);
            player.sendMessage(Component.text("Your turn: /poker check, call, raise <total>, or fold.", NamedTextColor.YELLOW));
        }
    }
    private void showHand(Player player) {
        Seat seat = seats.get(player.getUniqueId());
        if (seat == null || seat.hole.size() < 2) return;
        String boardText = board.isEmpty() ? "waiting for flop" : boardLabels();
        updateBossBar(player, seat, boardText);
    }

    private void saveAndPrepareInventory(Player player) {
        if (savedInventories.containsKey(player.getUniqueId())) return;
        savedInventories.put(player.getUniqueId(), new InventorySnapshot(player.getInventory().getStorageContents(), player.getInventory().getArmorContents(), player.getInventory().getExtraContents()));
        player.getInventory().setStorageContents(new ItemStack[player.getInventory().getStorageContents().length]);
        player.getInventory().setExtraContents(new ItemStack[player.getInventory().getExtraContents().length]);
        player.setItemOnCursor(null);
        player.getInventory().setHeldItemSlot(5);
        refreshPrivateInventory(player);
    }

    private void refreshPrivateInventory(Player player) {
        if (!savedInventories.containsKey(player.getUniqueId())) return;
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        inventory.setItem(0, null); inventory.setItem(1, null);
        inventory.setItem(5, actionItem(Material.LIME_DYE, "CHECK", "Check when matched"));
        inventory.setItem(6, actionItem(Material.GOLD_NUGGET, "CALL", "Match the current bet"));
        inventory.setItem(7, actionItem(Material.EMERALD, "RAISE", "Open raise options"));
        inventory.setItem(8, actionItem(Material.BARRIER, "FOLD", "Fold this hand"));
        Seat seat = seats.get(player.getUniqueId());
        if (seat != null && seat.hole.size() >= 2) {
            inventory.setItem(0, cardStack(seat.hole.get(0)));
            inventory.setItem(1, cardStack(seat.hole.get(1)));
        }
    }

    private static ItemStack actionItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, name.split(":", 2)[0]);
        item.setItemMeta(meta); return item;
    }

    @EventHandler
    public void onPokerActionInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!seats.containsKey(player.getUniqueId())) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null || !event.getAction().isRightClick()) return;
        event.setCancelled(true);
        if (phase == Phase.WAITING) { player.sendMessage(Component.text("The hand has not started. Use /poker start.", NamedTextColor.YELLOW)); return; }
        if (!player.getUniqueId().equals(turn)) { player.sendMessage(Component.text("It is not your turn.", NamedTextColor.RED)); return; }
        switch (action) {
            case "CHECK" -> check(player);
            case "CALL" -> call(player);
            case "RAISE" -> openRaiseMenu(player);
            case "FOLD" -> fold(player);
            default -> { }
        }
    }

    @EventHandler
    public void onPokerHeldItem(PlayerItemHeldEvent event) {
        if (!seats.containsKey(event.getPlayer().getUniqueId())) return;
        if (plugin.getConfig().getBoolean("poker.allow-hold-hole-cards", true)) return;
        if (event.getNewSlot() == 0 || event.getNewSlot() == 1) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Your hole cards stay private; use the action items in slots 6-9.", NamedTextColor.YELLOW));
        }
    }

    @EventHandler
    public void onPokerMove(PlayerMoveEvent event) {
        if (pendingTeleports.containsKey(event.getPlayer().getUniqueId())
                && (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ())) {
            cancelPendingTeleport(event.getPlayer(), true);
        }
    }

    private void openRaiseMenu(Player player) {
        Inventory inventory = plugin.getServer().createInventory(new RaiseHolder(player.getUniqueId()), 9, Component.text("Choose raise", NamedTextColor.GOLD));
        long min = currentBet + BIG_BLIND;
        inventory.setItem(1, actionItem(Material.IRON_INGOT, "Min raise: " + min, "Raise by one big blind"));
        inventory.setItem(3, actionItem(Material.GOLD_INGOT, "Half-pot raise: " + (currentBet + Math.max(BIG_BLIND, pot / 2)), "Raise by half the pot"));
        inventory.setItem(5, actionItem(Material.DIAMOND, "Pot raise: " + (currentBet + Math.max(BIG_BLIND, pot)), "Raise by the pot"));
        inventory.setItem(7, actionItem(Material.PAPER, "Custom amount", "Close menu and type the total bet in chat"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onPokerInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getHolder() instanceof RaiseHolder holder) {
            event.setCancelled(true);
            if (!holder.player.equals(player.getUniqueId())) return;
            int slot = event.getRawSlot();
            if (slot == 1) { player.closeInventory(); raise(player, new String[]{"raise", String.valueOf(currentBet + BIG_BLIND)}); }
            else if (slot == 3) { player.closeInventory(); raise(player, new String[]{"raise", String.valueOf(currentBet + Math.max(BIG_BLIND, pot / 2))}); }
            else if (slot == 5) { player.closeInventory(); raise(player, new String[]{"raise", String.valueOf(currentBet + Math.max(BIG_BLIND, pot))}); }
            else if (slot == 7) { awaitingCustomRaise.add(player.getUniqueId()); player.closeInventory(); player.sendMessage(Component.text("Type the total bet amount in chat, or type cancel.", NamedTextColor.YELLOW)); }
            return;
        }
        if (!seats.containsKey(player.getUniqueId()) || event.getClickedInventory() != player.getInventory()) return;
        event.setCancelled(true);
        if (phase == Phase.WAITING) { player.sendMessage(Component.text("The hand has not started. Use /poker start.", NamedTextColor.YELLOW)); return; }
        if (!player.getUniqueId().equals(turn)) { player.sendMessage(Component.text("It is not your turn.", NamedTextColor.RED)); return; }
        if (event.getSlot() == 5) check(player);
        else if (event.getSlot() == 6) call(player);
        else if (event.getSlot() == 7) { if (player.getUniqueId().equals(turn)) openRaiseMenu(player); else player.sendMessage(Component.text("It is not your turn.", NamedTextColor.RED)); }
        else if (event.getSlot() == 8) fold(player);
    }

    @EventHandler
    public void onPokerInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && savedInventories.containsKey(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onCustomRaiseChat(AsyncPlayerChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!awaitingCustomRaise.remove(id)) return;
        event.setCancelled(true);
        String text = event.getMessage().trim();
        if (text.equalsIgnoreCase("cancel")) { event.getPlayer().sendMessage(Component.text("Custom raise cancelled.", NamedTextColor.YELLOW)); return; }
        Bukkit.getScheduler().runTask(plugin, () -> raise(event.getPlayer(), new String[]{"raise", text}));
    }
    private void status(Player p) { p.sendMessage(Component.text("Phase: " + phase + " | Pot: " + pot + " | Bet: " + currentBet + " | Board: " + boardLabels(), NamedTextColor.AQUA)); }
    private String boardLabels() { return board.stream().map(Card::label).reduce((a, b) -> a + ", " + b).orElse("none"); }

    public void leave(Player player) {
        Seat seat = seats.remove(player.getUniqueId());
        if (seat == null) return;
        awaitingCustomRaise.remove(player.getUniqueId());
        removeBossBar(player.getUniqueId());
        if (phase != Phase.WAITING && !seat.folded) {
            seat.folded = true;
            if (player.getUniqueId().equals(turn)) advanceTurn();
        }
        restoreInventory(player);
        Location returnLocation = returnLocations.remove(player.getUniqueId());
        if (returnLocation != null && returnLocation.getWorld() != null) player.teleport(returnLocation);
        broadcast(player.getName() + " left the poker room.");
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        cancelPendingTeleport(event.getPlayer(), false);
        if (seats.containsKey(event.getPlayer().getUniqueId())) leave(event.getPlayer());
    }

    public void shutdown() {
        if (bossBarTask != null) bossBarTask.cancel();
        pendingTeleports.values().forEach(BukkitTask::cancel);
        pendingTeleports.clear();
        pendingSeats.clear();
        bossBars.values().forEach(BossBar::removeAll);
        bossBars.clear();
    }

    private void createBossBar(Player player) {
        BossBar bar = Bukkit.createBossBar("Poker", BarColor.BLUE, BarStyle.SEGMENTED_10);
        bar.addPlayer(player); bar.setVisible(true); bossBars.put(player.getUniqueId(), bar);
    }
    private void removeBossBar(UUID id) { BossBar bar = bossBars.remove(id); if (bar != null) bar.removeAll(); }
    private void updateBossBars() {
        for (Seat seat : seats.values()) {
            Player player = Bukkit.getPlayer(seat.uuid); if (player == null) continue;
            if (!bossBars.containsKey(seat.uuid)) createBossBar(player);
            updateBossBar(player, seat, board.isEmpty() ? "waiting for flop" : boardLabels());
        }
    }
    private void updateBossBar(Player player, Seat seat, String boardText) {
        BossBar bar = bossBars.get(player.getUniqueId()); if (bar == null) return;
        if (phase == Phase.WAITING) {
            bar.setTitle("Poker | Waiting for players | /poker start"); bar.setColor(BarColor.BLUE); bar.setProgress(1.0); return;
        }
        long call = Math.max(0L, currentBet - committed.getOrDefault(player.getUniqueId(), 0L));
        String actor = turn == null ? "-" : nameOf(turn);
        String hand = seat.hole.size() >= 2 ? seat.hole.get(0).label() + " / " + seat.hole.get(1).label() : "dealing";
        long remaining = turnStartedAt <= 0L ? TURN_TIME_SECONDS : Math.max(0L, TURN_TIME_SECONDS - (System.currentTimeMillis() - turnStartedAt) / 1000L);
        bar.setTitle(phase + " | Pot: " + pot + " | To act: " + actor + " | Time: " + remaining + "s | Call: " + call + " | Hand: " + hand + " | Board: " + boardText);
        bar.setColor(player.getUniqueId().equals(turn) ? BarColor.GREEN : BarColor.BLUE); bar.setProgress(1.0);
    }

    private World ensureWorld() {
        if (world != null) return world;
        world = Bukkit.getWorld(WORLD_NAME);
        if (world == null) world = new WorldCreator(WORLD_NAME).type(org.bukkit.WorldType.FLAT).createWorld();
        configureWorld();
        if (!layoutBuilt) {
            layoutBuilt = true;
            if (!pasteConfiguredSchematic()) buildRoom();
        }
        if (!displayCleanupDone) {
            for (Entity entity : world.getEntities()) if (entity instanceof Display) entity.remove();
            displayCleanupDone = true;
        }
        return world;
    }

    private void configureWorld() {
        if (world == null) return;
        world.setSpawnLocation(roomX(), roomY() + 1, roomZ());
        try { world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false); } catch (Exception ignored) { }
    }

    /**
     * Pastes a WorldEdit schematic only when explicitly enabled. The custom room
     * must be authored around the configured anchor: the board is centered at
     * anchor + (0, 3, 0.5), and seats are x=-3/0/3, z=-6/+6 from the anchor.
     */
    private boolean pasteConfiguredSchematic() {
        if (!plugin.getConfig().getBoolean("poker.room.schematic.enabled", false)) return false;
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
            plugin.getLogger().warning("Poker schematic is enabled but WorldEdit is unavailable; building the fallback room.");
            return false;
        }
        File directory = new File(plugin.getDataFolder(), "schematics");
        String configuredName = plugin.getConfig().getString("poker.room.schematic.file", "poker_room.schem");
        try {
            File root = directory.getCanonicalFile();
            File schematic = new File(root, configuredName).getCanonicalFile();
            if (!schematic.getPath().startsWith(root.getPath() + File.separator) || !schematic.isFile()) {
                plugin.getLogger().warning("Poker schematic not found: " + configuredName + ". Put it in " + root.getPath());
                return false;
            }
            ClipboardFormat format = ClipboardFormats.findByFile(schematic);
            if (format == null) {
                plugin.getLogger().warning("Poker schematic has an unsupported format: " + schematic.getName());
                return false;
            }
            try (ClipboardReader reader = format.getReader(new FileInputStream(schematic))) {
                Clipboard clipboard = reader.read();
                try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                    Operations.complete(new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(BlockVector3.at(roomX(), roomY(), roomZ()))
                            .ignoreAirBlocks(plugin.getConfig().getBoolean("poker.room.schematic.ignore-air-blocks", false))
                            .build());
                }
            }
            plugin.getLogger().info("Pasted poker schematic " + schematic.getName() + " at " + roomX() + ", " + roomY() + ", " + roomZ());
            return true;
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not paste poker schematic: " + exception.getMessage());
            return false;
        }
    }

    private void buildRoom() {
        if (world == null) return;
        for (int x = -12; x <= 12; x++) for (int z = -9; z <= 9; z++) for (int y = -1; y <= 5; y++) world.getBlockAt(roomX() + x, roomY() + y, roomZ() + z).setType(Material.AIR);
        for (int x = -10; x <= 10; x++) for (int z = -7; z <= 7; z++) { world.getBlockAt(roomX() + x, roomY() - 1, roomZ() + z).setType(Material.BLACK_CONCRETE); world.getBlockAt(roomX() + x, roomY(), roomZ() + z).setType(Material.GREEN_WOOL); }
        for (int x = -5; x <= 5; x++) for (int z = -2; z <= 2; z++) world.getBlockAt(roomX() + x, roomY() + 1, roomZ() + z).setType(Material.LIME_WOOL);
        for (int i = 0; i < MAX_PLAYERS; i++) { Location l = spawnFor(i); world.getBlockAt(l.getBlockX(), roomY(), l.getBlockZ()).setType(Material.DARK_OAK_STAIRS); }
    }
    private Location spawnFor(int seat) {
        int row = seat < 3 ? 1 : -1;
        int index = seat % 3;
        int x = -3 + index * 3;
        int z = row * 6;
        return new Location(ensureWorld(), roomX() + x, roomY() + 1, roomZ() + z, row > 0 ? 180.0f : 0.0f, 0.0f);
    }
    private int firstFreeSeat() { Set<Integer> used = new HashSet<>(); seats.values().forEach(s -> used.add(s.seat)); used.addAll(pendingSeats.values()); for (int i = 0; i < MAX_PLAYERS; i++) if (!used.contains(i)) return i; return 0; }
    private List<Seat> activeSeats() { return seats.values().stream().filter(s -> !s.folded).sorted(java.util.Comparator.comparingInt(s -> s.seat)).toList(); }
    private int indexOf(UUID id, List<Seat> list) { for (int i = 0; i < list.size(); i++) if (list.get(i).uuid.equals(id)) return i; return 0; }
    private String nameOf(UUID id) { Player p = Bukkit.getPlayer(id); return p == null ? "Player" : p.getName(); }
    private void broadcast(String text) { seats.keySet().stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull).forEach(p -> { p.sendMessage(Component.text(text, NamedTextColor.GREEN)); if (phase != Phase.WAITING) showHand(p); }); }

    private void renderBoard() {
        clearCards();
        for (int i = 0; i < board.size(); i++) {
            // The table surface is one block above the original room floor.
            Location location = new Location(ensureWorld(), roomX() - 2 + i * 1.0, roomY() + 3.1, roomZ() + 0.5);
            ItemDisplay display = world.spawn(location, ItemDisplay.class);
            display.setItemStack(cardStack(board.get(i)));
            // CENTER keeps the card visible from every seat instead of showing its edge.
            display.setBillboard(Display.Billboard.CENTER);
            // Item models are horizontal by default in an ItemDisplay; stand the card up.
            display.setTransformation(new Transformation(
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new Quaternionf().rotateX((float) Math.toRadians(90.0)),
                    new Vector3f(2.1f, 2.1f, 2.1f),
                    new Quaternionf()));
            display.setViewRange(64.0f);
            cardDisplays.add(display);
        }
    }
    private void clearCards() { for (Entity display : cardDisplays) display.remove(); cardDisplays.clear(); }
    private int roomX() { return plugin.getConfig().getInt("poker.room.anchor.x", 0); }
    private int roomY() { return plugin.getConfig().getInt("poker.room.anchor.y", 64); }
    private int roomZ() { return plugin.getConfig().getInt("poker.room.anchor.z", 0); }
    private static ItemStack cardStack(Card card) { ItemStack item = new ItemStack(Material.PAPER); ItemMeta meta = item.getItemMeta(); meta.displayName(Component.text(card.label(), card.color())); meta.setItemModel(new NamespacedKey("pokerpack", "card_" + card.modelCode())); item.setItemMeta(meta); return item; }
    private static List<Card> newDeck() { List<Card> deck = new ArrayList<>(); for (Suit suit : Suit.values()) for (int rank = 2; rank <= 14; rank++) deck.add(new Card(rank, suit)); Collections.shuffle(deck); return deck; }
    private enum Phase { WAITING, PREFLOP, FLOP, TURN, RIVER }
    private enum Suit { HEARTS("Hearts", "h", NamedTextColor.RED), DIAMONDS("Diamonds", "d", NamedTextColor.RED), CLUBS("Clubs", "c", NamedTextColor.DARK_GRAY), SPADES("Spades", "s", NamedTextColor.DARK_GRAY); final String name, code; final NamedTextColor color; Suit(String n, String c, NamedTextColor col) { name=n; code=c; color=col; } }
    private record Card(int rank, Suit suit) { String label() { return rankName(rank) + " of " + suit.name; } String modelCode() { return suit.code + rankCode(rank); } NamedTextColor color() { return suit.color; } private static String rankCode(int r) { return switch (r) { case 14 -> "a"; case 13 -> "k"; case 12 -> "q"; case 11 -> "j"; default -> String.valueOf(r); }; } private static String rankName(int r) { return switch (r) { case 14 -> "Ace"; case 13 -> "King"; case 12 -> "Queen"; case 11 -> "Jack"; default -> String.valueOf(r); }; } }
    private static final class Seat { final UUID uuid; final int seat; final List<Card> hole = new ArrayList<>(); boolean folded; Seat(UUID id, int s) { uuid=id; seat=s; } }
    private void restoreInventory(Player player) {
        InventorySnapshot snapshot = savedInventories.remove(player.getUniqueId());
        if (snapshot == null) return;
        player.getInventory().setStorageContents(snapshot.contents.clone());
        player.getInventory().setArmorContents(snapshot.armor.clone());
        player.getInventory().setExtraContents(snapshot.extra.clone());
    }
    private record InventorySnapshot(ItemStack[] contents, ItemStack[] armor, ItemStack[] extra) {
        InventorySnapshot {
            contents = cloneItems(contents); armor = cloneItems(armor); extra = cloneItems(extra);
        }
        private static ItemStack[] cloneItems(ItemStack[] source) {
            ItemStack[] copy = source == null ? new ItemStack[0] : source.clone();
            for (int i = 0; i < copy.length; i++) if (copy[i] != null) copy[i] = copy[i].clone();
            return copy;
        }
    }
    private record RaiseHolder(UUID player) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
}
