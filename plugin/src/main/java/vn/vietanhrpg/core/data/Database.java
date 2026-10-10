package vn.vietanhrpg.core.data;

import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.item.Rarity;
import vn.vietanhrpg.core.market.MarketCancelResult;
import vn.vietanhrpg.core.market.MarketCancelStatus;
import vn.vietanhrpg.core.market.MarketDelivery;
import vn.vietanhrpg.core.market.MarketListing;
import vn.vietanhrpg.core.market.MarketPurchaseResult;
import vn.vietanhrpg.core.market.MarketPurchaseStatus;
import vn.vietanhrpg.core.market.MarketTransaction;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Database {
    private final JavaPlugin plugin;
    private Connection connection;

    public Database(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void open() throws Exception {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder");
        }

        Class.forName("org.sqlite.JDBC");
        File file = new File(plugin.getDataFolder(), "rpg.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        coin INTEGER NOT NULL DEFAULT 0,
                        token INTEGER NOT NULL DEFAULT 0,
                        dungeon_clears INTEGER NOT NULL DEFAULT 0,
                        boss_kills INTEGER NOT NULL DEFAULT 0,
                        rare_crafted INTEGER NOT NULL DEFAULT 0,
                        epic_crafted INTEGER NOT NULL DEFAULT 0,
                        legendary_crafted INTEGER NOT NULL DEFAULT 0,
                        mythic_crafted INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_blueprints (
                        uuid TEXT NOT NULL,
                        blueprint_id TEXT NOT NULL,
                        learned_at INTEGER NOT NULL,
                        PRIMARY KEY(uuid, blueprint_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_settings (
                        uuid TEXT PRIMARY KEY,
                        legendary_aura INTEGER NOT NULL DEFAULT 1,
                        mythic_aura INTEGER NOT NULL DEFAULT 1,
                        combat_particles INTEGER NOT NULL DEFAULT 1,
                        craft_effects INTEGER NOT NULL DEFAULT 1,
                        material_fx INTEGER NOT NULL DEFAULT 1,
                        show_other_particles INTEGER NOT NULL DEFAULT 1
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_quest_assignments (
                        uuid TEXT NOT NULL,
                        period_type TEXT NOT NULL,
                        period_key TEXT NOT NULL,
                        slot INTEGER NOT NULL,
                        quest_id TEXT NOT NULL,
                        PRIMARY KEY(uuid, period_type, period_key, slot)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_quest_progress (
                        uuid TEXT NOT NULL,
                        quest_id TEXT NOT NULL,
                        period_key TEXT NOT NULL,
                        progress INTEGER NOT NULL DEFAULT 0,
                        rewarded INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(uuid, quest_id, period_key)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_counters (
                        uuid TEXT NOT NULL,
                        counter_id TEXT NOT NULL,
                        value INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(uuid, counter_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_achievements (
                        uuid TEXT NOT NULL,
                        achievement_id TEXT NOT NULL,
                        unlocked_at INTEGER NOT NULL,
                        PRIMARY KEY(uuid, achievement_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_titles (
                        uuid TEXT NOT NULL,
                        title_id TEXT NOT NULL,
                        unlocked_at INTEGER NOT NULL,
                        PRIMARY KEY(uuid, title_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_dungeon_cooldowns (
                        uuid TEXT NOT NULL,
                        dungeon_id TEXT NOT NULL,
                        last_clear_at INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(uuid, dungeon_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS market_listings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        seller_uuid TEXT NOT NULL,
                        seller_name TEXT NOT NULL,
                        item_data BLOB NOT NULL,
                        item_type TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        price INTEGER NOT NULL,
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        buyer_uuid TEXT,
                        buyer_name TEXT,
                        created_at INTEGER NOT NULL,
                        completed_at INTEGER
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS market_deliveries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        recipient_uuid TEXT NOT NULL,
                        listing_id INTEGER NOT NULL,
                        item_data BLOB NOT NULL,
                        reason TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        claimed_at INTEGER,
                        FOREIGN KEY(listing_id) REFERENCES market_listings(id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS market_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        listing_id INTEGER NOT NULL,
                        buyer_uuid TEXT NOT NULL,
                        buyer_name TEXT NOT NULL,
                        seller_uuid TEXT NOT NULL,
                        seller_name TEXT NOT NULL,
                        item_type TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        price INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY(listing_id) REFERENCES market_listings(id)
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS market_listings_status_created ON market_listings(status, created_at DESC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS market_listings_seller_status ON market_listings(seller_uuid, status)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS market_deliveries_recipient_claimed ON market_deliveries(recipient_uuid, claimed_at)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS market_transactions_buyer ON market_transactions(buyer_uuid, created_at DESC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS market_transactions_seller ON market_transactions(seller_uuid, created_at DESC)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS shop_sales_daily (
                        uuid TEXT NOT NULL,
                        day_key TEXT NOT NULL,
                        coin_earned INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(uuid, day_key)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS shop_sales_items_daily (
                        uuid TEXT NOT NULL,
                        day_key TEXT NOT NULL,
                        material TEXT NOT NULL,
                        amount INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(uuid, day_key, material)
                    )
                    """);
        }

        // Migration from older VanillaRPG databases.
        ensureColumn("players", "mythic_crafted", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("players", "selected_title", "TEXT");
        ensureColumn("player_settings", "material_fx", "INTEGER NOT NULL DEFAULT 1");
    }

    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not close SQLite connection: " + exception.getMessage());
        }
        connection = null;
    }

    public synchronized void ensurePlayer(UUID uuid, String name, long startingCoin, long startingToken) {
        requireConnection();
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO players(uuid, name, coin, token, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, updated_at = excluded.updated_at
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setLong(3, Math.max(0, startingCoin));
            statement.setLong(4, Math.max(0, startingToken));
            statement.setLong(5, now);
            statement.setLong(6, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not ensure player record", exception);
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_settings(uuid) VALUES(?)
                ON CONFLICT(uuid) DO NOTHING
                """)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not ensure player settings", exception);
        }
    }

    public synchronized long getCoin(UUID uuid) {
        return getLong(uuid, "coin");
    }

    public synchronized long getToken(UUID uuid) {
        return getLong(uuid, "token");
    }

    public synchronized long getStat(UUID uuid, String column) {
        if (!switch (column) {
            case "dungeon_clears", "boss_kills", "rare_crafted", "epic_crafted", "legendary_crafted", "mythic_crafted" -> true;
            default -> false;
        }) {
            throw new IllegalArgumentException("Unsupported stat column: " + column);
        }
        return getLong(uuid, column);
    }

    private long getLong(UUID uuid, String column) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + column + " FROM players WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read " + column, exception);
        }
    }

    public synchronized void setCoin(UUID uuid, long value) {
        setValue(uuid, "coin", Math.max(0, value));
    }

    public synchronized void setToken(UUID uuid, long value) {
        setValue(uuid, "token", Math.max(0, value));
    }

    private void setValue(UUID uuid, String column, long value) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE players SET " + column + " = ?, updated_at = ? WHERE uuid = ?")) {
            statement.setLong(1, value);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not update " + column, exception);
        }
    }

    public synchronized boolean transferCoin(UUID from, UUID to, long amount) {
        requireConnection();
        if (amount <= 0 || from.equals(to)) {
            return false;
        }

        try {
            connection.setAutoCommit(false);
            long balance;
            try (PreparedStatement read = connection.prepareStatement("SELECT coin FROM players WHERE uuid = ?")) {
                read.setString(1, from.toString());
                try (ResultSet result = read.executeQuery()) {
                    balance = result.next() ? result.getLong(1) : 0L;
                }
            }
            if (balance < amount) {
                connection.rollback();
                return false;
            }

            try (PreparedStatement debit = connection.prepareStatement("UPDATE players SET coin = coin - ?, updated_at = ? WHERE uuid = ?");
                 PreparedStatement credit = connection.prepareStatement("UPDATE players SET coin = coin + ?, updated_at = ? WHERE uuid = ?")) {
                long now = System.currentTimeMillis();
                debit.setLong(1, amount);
                debit.setLong(2, now);
                debit.setString(3, from.toString());
                debit.executeUpdate();

                credit.setLong(1, amount);
                credit.setLong(2, now);
                credit.setString(3, to.toString());
                credit.executeUpdate();
            }

            connection.commit();
            return true;
        } catch (SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
            throw new IllegalStateException("Could not transfer Coin", exception);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    public synchronized long shopSaleCoinToday(UUID uuid, String dayKey) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT coin_earned FROM shop_sales_daily WHERE uuid = ? AND day_key = ?
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, dayKey);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read daily shop sales", exception);
        }
    }

    public synchronized int shopSaleItemAmountToday(UUID uuid, String dayKey, String material) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT amount FROM shop_sales_items_daily WHERE uuid = ? AND day_key = ? AND material = ?
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, dayKey);
            statement.setString(3, material);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read daily shop item sales", exception);
        }
    }

    /** Atomically credits a shop sale only when it fits its Coin and material daily limits. */
    public synchronized boolean creditShopSale(UUID uuid, String dayKey, String material, int itemAmount,
                                               long coinAmount, long dailyCoinLimit, int dailyItemLimit) {
        requireConnection();
        if (itemAmount <= 0 || coinAmount <= 0 || dailyCoinLimit < coinAmount || dailyItemLimit < itemAmount) return false;
        try {
            connection.setAutoCommit(false);
            long earned = 0;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT coin_earned FROM shop_sales_daily WHERE uuid = ? AND day_key = ?
                    """)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, dayKey);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) earned = rows.getLong(1);
                }
            }
            if (earned > dailyCoinLimit - coinAmount) {
                connection.rollback();
                return false;
            }

            int itemsSold = 0;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT amount FROM shop_sales_items_daily WHERE uuid = ? AND day_key = ? AND material = ?
                    """)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, dayKey);
                statement.setString(3, material);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) itemsSold = rows.getInt(1);
                }
            }
            if (itemsSold > dailyItemLimit - itemAmount) {
                connection.rollback();
                return false;
            }

            try (PreparedStatement sale = connection.prepareStatement("""
                    INSERT INTO shop_sales_daily(uuid, day_key, coin_earned) VALUES(?, ?, ?)
                    ON CONFLICT(uuid, day_key) DO UPDATE SET coin_earned = coin_earned + excluded.coin_earned
                    """);
                 PreparedStatement itemSale = connection.prepareStatement("""
                    INSERT INTO shop_sales_items_daily(uuid, day_key, material, amount) VALUES(?, ?, ?, ?)
                    ON CONFLICT(uuid, day_key, material) DO UPDATE SET amount = amount + excluded.amount
                    """);
                 PreparedStatement credit = connection.prepareStatement("""
                    UPDATE players SET coin = coin + ?, updated_at = ? WHERE uuid = ?
                    """)) {
                sale.setString(1, uuid.toString());
                sale.setString(2, dayKey);
                sale.setLong(3, coinAmount);
                sale.executeUpdate();

                itemSale.setString(1, uuid.toString());
                itemSale.setString(2, dayKey);
                itemSale.setString(3, material);
                itemSale.setInt(4, itemAmount);
                itemSale.executeUpdate();

                credit.setLong(1, coinAmount);
                credit.setLong(2, System.currentTimeMillis());
                credit.setString(3, uuid.toString());
                if (credit.executeUpdate() != 1) throw new SQLException("Player row is missing");
            }
            connection.commit();
            return true;
        } catch (SQLException exception) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            throw new IllegalStateException("Could not credit shop sale", exception);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public synchronized void incrementCraft(UUID uuid, Rarity rarity) {
        String column = switch (rarity) {
            case RARE -> "rare_crafted";
            case EPIC -> "epic_crafted";
            case LEGENDARY -> "legendary_crafted";
            case MYTHIC -> "mythic_crafted";
            default -> null;
        };
        if (column == null) {
            return;
        }
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE players SET " + column + " = " + column + " + 1, updated_at = ? WHERE uuid = ?")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not increment craft stat", exception);
        }
    }

    public synchronized void incrementDungeonClear(UUID uuid) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE players SET dungeon_clears = dungeon_clears + 1, updated_at = ? WHERE uuid = ?")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not increment dungeon clear stat", exception);
        }
    }

    public synchronized long dungeonLastClear(UUID uuid, String dungeonId) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT last_clear_at FROM player_dungeon_cooldowns WHERE uuid = ? AND dungeon_id = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, dungeonId.toLowerCase());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read dungeon cooldown", exception);
        }
    }

    public synchronized void setDungeonLastClear(UUID uuid, String dungeonId, long timestamp) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_dungeon_cooldowns(uuid, dungeon_id, last_clear_at) VALUES(?, ?, ?)
                ON CONFLICT(uuid, dungeon_id) DO UPDATE SET last_clear_at = excluded.last_clear_at
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, dungeonId.toLowerCase());
            statement.setLong(3, Math.max(0, timestamp));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not update dungeon cooldown", exception);
        }
    }

    public synchronized boolean hasBlueprint(UUID uuid, String blueprintId) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM player_blueprints WHERE uuid = ? AND blueprint_id = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, blueprintId.toLowerCase());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read blueprint", exception);
        }
    }

    public synchronized boolean learnBlueprint(UUID uuid, String blueprintId) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_blueprints(uuid, blueprint_id, learned_at)
                VALUES(?, ?, ?)
                ON CONFLICT(uuid, blueprint_id) DO NOTHING
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, blueprintId.toLowerCase());
            statement.setLong(3, System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not learn blueprint", exception);
        }
    }

    public synchronized Set<String> blueprints(UUID uuid) {
        requireConnection();
        Set<String> result = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT blueprint_id FROM player_blueprints WHERE uuid = ? ORDER BY learned_at")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(rows.getString(1));
                }
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not list blueprints", exception);
        }
    }

    public synchronized int blueprintCount(UUID uuid) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM player_blueprints WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not count blueprints", exception);
        }
    }

    public synchronized boolean getSetting(UUID uuid, String setting) {
        String column = settingColumn(setting);
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + column + " FROM player_settings WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return !rows.next() || rows.getInt(1) != 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read particle setting", exception);
        }
    }

    public synchronized void setSetting(UUID uuid, String setting, boolean value) {
        String column = settingColumn(setting);
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE player_settings SET " + column + " = ? WHERE uuid = ?")) {
            statement.setInt(1, value ? 1 : 0);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not update particle setting", exception);
        }
    }

    public synchronized List<String> questAssignments(UUID uuid, String periodType, String periodKey) {
        requireConnection();
        List<String> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT quest_id FROM player_quest_assignments WHERE uuid = ? AND period_type = ? AND period_key = ? ORDER BY slot")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, periodType.toUpperCase());
            statement.setString(3, periodKey);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(rows.getString(1));
                }
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not list quest assignments", exception);
        }
    }

    public synchronized void saveQuestAssignments(UUID uuid, String periodType, String periodKey, List<String> questIds) {
        requireConnection();
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM player_quest_assignments WHERE uuid = ? AND period_type = ? AND period_key = ?")) {
                delete.setString(1, uuid.toString());
                delete.setString(2, periodType.toUpperCase());
                delete.setString(3, periodKey);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO player_quest_assignments(uuid, period_type, period_key, slot, quest_id) VALUES(?, ?, ?, ?, ?)")) {
                for (int i = 0; i < questIds.size(); i++) {
                    insert.setString(1, uuid.toString());
                    insert.setString(2, periodType.toUpperCase());
                    insert.setString(3, periodKey);
                    insert.setInt(4, i);
                    insert.setString(5, questIds.get(i));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
        } catch (SQLException exception) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            throw new IllegalStateException("Could not save quest assignments", exception);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public synchronized long questProgress(UUID uuid, String questId, String periodKey) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT progress FROM player_quest_progress WHERE uuid = ? AND quest_id = ? AND period_key = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, questId);
            statement.setString(3, periodKey);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read quest progress", exception);
        }
    }

    public synchronized long addQuestProgress(UUID uuid, String questId, String periodKey, long delta) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_quest_progress(uuid, quest_id, period_key, progress, rewarded)
                VALUES(?, ?, ?, ?, 0)
                ON CONFLICT(uuid, quest_id, period_key)
                DO UPDATE SET progress = progress + excluded.progress
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, questId);
            statement.setString(3, periodKey);
            statement.setLong(4, Math.max(0, delta));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not update quest progress", exception);
        }
        return questProgress(uuid, questId, periodKey);
    }

    public synchronized boolean questRewarded(UUID uuid, String questId, String periodKey) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT rewarded FROM player_quest_progress WHERE uuid = ? AND quest_id = ? AND period_key = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, questId);
            statement.setString(3, periodKey);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getInt(1) != 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read quest reward state", exception);
        }
    }

    public synchronized void markQuestRewarded(UUID uuid, String questId, String periodKey) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_quest_progress(uuid, quest_id, period_key, progress, rewarded)
                VALUES(?, ?, ?, 0, 1)
                ON CONFLICT(uuid, quest_id, period_key) DO UPDATE SET rewarded = 1
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, questId);
            statement.setString(3, periodKey);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not mark quest rewarded", exception);
        }
    }

    public synchronized long counter(UUID uuid, String counterId) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM player_counters WHERE uuid = ? AND counter_id = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, counterId.toLowerCase());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read player counter", exception);
        }
    }

    public synchronized long addCounter(UUID uuid, String counterId, long delta) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_counters(uuid, counter_id, value) VALUES(?, ?, ?)
                ON CONFLICT(uuid, counter_id) DO UPDATE SET value = value + excluded.value
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, counterId.toLowerCase());
            statement.setLong(3, Math.max(0, delta));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not update player counter", exception);
        }
        return counter(uuid, counterId);
    }

    public synchronized boolean hasAchievement(UUID uuid, String achievementId) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM player_achievements WHERE uuid = ? AND achievement_id = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, achievementId.toLowerCase());
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read achievement", exception);
        }
    }

    public synchronized boolean unlockAchievement(UUID uuid, String achievementId) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_achievements(uuid, achievement_id, unlocked_at) VALUES(?, ?, ?)
                ON CONFLICT(uuid, achievement_id) DO NOTHING
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, achievementId.toLowerCase());
            statement.setLong(3, System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not unlock achievement", exception);
        }
    }

    public synchronized Set<String> achievements(UUID uuid) {
        requireConnection();
        Set<String> result = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT achievement_id FROM player_achievements WHERE uuid = ? ORDER BY unlocked_at")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(rows.getString(1));
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not list achievements", exception);
        }
    }

    public synchronized boolean hasTitle(UUID uuid, String titleId) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM player_titles WHERE uuid = ? AND title_id = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, titleId.toLowerCase());
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read title", exception);
        }
    }

    public synchronized boolean unlockTitle(UUID uuid, String titleId) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_titles(uuid, title_id, unlocked_at) VALUES(?, ?, ?)
                ON CONFLICT(uuid, title_id) DO NOTHING
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, titleId.toLowerCase());
            statement.setLong(3, System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not unlock title", exception);
        }
    }

    public synchronized Set<String> titles(UUID uuid) {
        requireConnection();
        Set<String> result = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT title_id FROM player_titles WHERE uuid = ? ORDER BY unlocked_at")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(rows.getString(1));
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not list titles", exception);
        }
    }

    public synchronized String selectedTitle(UUID uuid) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement("SELECT selected_title FROM players WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read selected title", exception);
        }
    }

    public synchronized void setSelectedTitle(UUID uuid, String titleId) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE players SET selected_title = ?, updated_at = ? WHERE uuid = ?")) {
            if (titleId == null || titleId.isBlank()) statement.setNull(1, java.sql.Types.VARCHAR);
            else statement.setString(1, titleId.toLowerCase());
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not set selected title", exception);
        }
    }

    public synchronized long createMarketListing(UUID sellerUuid, String sellerName, byte[] itemData,
                                                 String itemType, int amount, long price) {
        requireConnection();
        if (itemData == null || itemData.length == 0 || amount <= 0 || price <= 0) {
            throw new IllegalArgumentException("Invalid market listing data");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO market_listings(
                    seller_uuid, seller_name, item_data, item_type, amount, price, status, created_at
                ) VALUES(?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, sellerUuid.toString());
            statement.setString(2, sellerName);
            statement.setBytes(3, itemData);
            statement.setString(4, itemType);
            statement.setInt(5, amount);
            statement.setLong(6, price);
            statement.setLong(7, System.currentTimeMillis());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            throw new SQLException("No generated listing ID returned");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create market listing", exception);
        }
    }

    public synchronized int activeMarketListingCount() {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM market_listings WHERE status = 'ACTIVE'");
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getInt(1) : 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not count market listings", exception);
        }
    }

    public synchronized int activeMarketListingCount(UUID sellerUuid) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM market_listings WHERE seller_uuid = ? AND status = 'ACTIVE'")) {
            statement.setString(1, sellerUuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not count player market listings", exception);
        }
    }

    public synchronized List<MarketListing> activeMarketListings(int limit, int offset) {
        requireConnection();
        List<MarketListing> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, seller_uuid, seller_name, item_data, item_type, amount, price, created_at
                FROM market_listings
                WHERE status = 'ACTIVE'
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """)) {
            statement.setInt(1, Math.max(1, Math.min(100, limit)));
            statement.setInt(2, Math.max(0, offset));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(readMarketListing(rows));
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not list market listings", exception);
        }
    }

    public synchronized List<MarketListing> activeMarketListings(UUID sellerUuid, int limit) {
        requireConnection();
        List<MarketListing> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, seller_uuid, seller_name, item_data, item_type, amount, price, created_at
                FROM market_listings
                WHERE seller_uuid = ? AND status = 'ACTIVE'
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """)) {
            statement.setString(1, sellerUuid.toString());
            statement.setInt(2, Math.max(1, Math.min(100, limit)));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(readMarketListing(rows));
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not list player market listings", exception);
        }
    }

    public synchronized MarketPurchaseResult purchaseMarketListing(long listingId, UUID buyerUuid, String buyerName) {
        requireConnection();
        try {
            connection.setAutoCommit(false);
            MarketListing listing;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, seller_uuid, seller_name, item_data, item_type, amount, price, created_at
                    FROM market_listings WHERE id = ? AND status = 'ACTIVE'
                    """)) {
                statement.setLong(1, listingId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        connection.rollback();
                        return MarketPurchaseResult.failure(MarketPurchaseStatus.NOT_FOUND, listingId);
                    }
                    listing = readMarketListing(rows);
                }
            }

            if (listing.sellerUuid().equals(buyerUuid)) {
                connection.rollback();
                return MarketPurchaseResult.failure(MarketPurchaseStatus.OWN_LISTING, listingId);
            }

            long now = System.currentTimeMillis();
            try (PreparedStatement debit = connection.prepareStatement("""
                    UPDATE players SET coin = coin - ?, updated_at = ?
                    WHERE uuid = ? AND coin >= ?
                    """)) {
                debit.setLong(1, listing.price());
                debit.setLong(2, now);
                debit.setString(3, buyerUuid.toString());
                debit.setLong(4, listing.price());
                if (debit.executeUpdate() != 1) {
                    connection.rollback();
                    return MarketPurchaseResult.failure(MarketPurchaseStatus.INSUFFICIENT_COIN, listingId);
                }
            }

            try (PreparedStatement credit = connection.prepareStatement(
                    "UPDATE players SET coin = coin + ?, updated_at = ? WHERE uuid = ?")) {
                credit.setLong(1, listing.price());
                credit.setLong(2, now);
                credit.setString(3, listing.sellerUuid().toString());
                if (credit.executeUpdate() != 1) throw new SQLException("Seller player row is missing");
            }

            try (PreparedStatement close = connection.prepareStatement("""
                    UPDATE market_listings
                    SET status = 'SOLD', buyer_uuid = ?, buyer_name = ?, completed_at = ?
                    WHERE id = ? AND status = 'ACTIVE'
                    """)) {
                close.setString(1, buyerUuid.toString());
                close.setString(2, buyerName);
                close.setLong(3, now);
                close.setLong(4, listing.id());
                if (close.executeUpdate() != 1) throw new SQLException("Listing changed during purchase");
            }

            long deliveryId;
            try (PreparedStatement delivery = connection.prepareStatement("""
                    INSERT INTO market_deliveries(recipient_uuid, listing_id, item_data, reason, created_at)
                    VALUES(?, ?, ?, 'PURCHASE', ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                delivery.setString(1, buyerUuid.toString());
                delivery.setLong(2, listing.id());
                delivery.setBytes(3, listing.itemData());
                delivery.setLong(4, now);
                delivery.executeUpdate();
                try (ResultSet keys = delivery.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("No generated delivery ID returned");
                    deliveryId = keys.getLong(1);
                }
            }

            try (PreparedStatement transaction = connection.prepareStatement("""
                    INSERT INTO market_transactions(
                        listing_id, buyer_uuid, buyer_name, seller_uuid, seller_name,
                        item_type, amount, price, created_at
                    ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                transaction.setLong(1, listing.id());
                transaction.setString(2, buyerUuid.toString());
                transaction.setString(3, buyerName);
                transaction.setString(4, listing.sellerUuid().toString());
                transaction.setString(5, listing.sellerName());
                transaction.setString(6, listing.itemType());
                transaction.setInt(7, listing.amount());
                transaction.setLong(8, listing.price());
                transaction.setLong(9, now);
                transaction.executeUpdate();
            }

            connection.commit();
            return new MarketPurchaseResult(MarketPurchaseStatus.SUCCESS, listing.id(), deliveryId,
                    listing.price(), listing.sellerName());
        } catch (SQLException exception) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            throw new IllegalStateException("Could not purchase market listing", exception);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public synchronized MarketCancelResult cancelMarketListing(long listingId, UUID sellerUuid) {
        requireConnection();
        try {
            connection.setAutoCommit(false);
            UUID owner;
            byte[] itemData;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT seller_uuid, item_data FROM market_listings WHERE id = ? AND status = 'ACTIVE'")) {
                statement.setLong(1, listingId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        connection.rollback();
                        return MarketCancelResult.failure(MarketCancelStatus.NOT_FOUND, listingId);
                    }
                    owner = UUID.fromString(rows.getString("seller_uuid"));
                    itemData = rows.getBytes("item_data");
                }
            }
            if (!owner.equals(sellerUuid)) {
                connection.rollback();
                return MarketCancelResult.failure(MarketCancelStatus.NOT_OWNER, listingId);
            }

            long now = System.currentTimeMillis();
            try (PreparedStatement close = connection.prepareStatement("""
                    UPDATE market_listings SET status = 'CANCELLED', completed_at = ?
                    WHERE id = ? AND status = 'ACTIVE'
                    """)) {
                close.setLong(1, now);
                close.setLong(2, listingId);
                if (close.executeUpdate() != 1) throw new SQLException("Listing changed during cancellation");
            }

            long deliveryId;
            try (PreparedStatement delivery = connection.prepareStatement("""
                    INSERT INTO market_deliveries(recipient_uuid, listing_id, item_data, reason, created_at)
                    VALUES(?, ?, ?, 'CANCEL', ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                delivery.setString(1, sellerUuid.toString());
                delivery.setLong(2, listingId);
                delivery.setBytes(3, itemData);
                delivery.setLong(4, now);
                delivery.executeUpdate();
                try (ResultSet keys = delivery.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("No generated delivery ID returned");
                    deliveryId = keys.getLong(1);
                }
            }
            connection.commit();
            return new MarketCancelResult(MarketCancelStatus.SUCCESS, listingId, deliveryId);
        } catch (SQLException exception) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            throw new IllegalStateException("Could not cancel market listing", exception);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public synchronized List<MarketDelivery> pendingMarketDeliveries(UUID recipientUuid, int limit) {
        requireConnection();
        List<MarketDelivery> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, listing_id, item_data, reason, created_at
                FROM market_deliveries
                WHERE recipient_uuid = ? AND claimed_at IS NULL
                ORDER BY created_at, id
                LIMIT ?
                """)) {
            statement.setString(1, recipientUuid.toString());
            statement.setInt(2, Math.max(1, Math.min(100, limit)));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new MarketDelivery(rows.getLong("id"), rows.getLong("listing_id"),
                            rows.getBytes("item_data"), rows.getString("reason"), rows.getLong("created_at")));
                }
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not list market deliveries", exception);
        }
    }

    public synchronized int pendingMarketDeliveryCount(UUID recipientUuid) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM market_deliveries
                WHERE recipient_uuid = ? AND claimed_at IS NULL
                """)) {
            statement.setString(1, recipientUuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not count market deliveries", exception);
        }
    }

    public synchronized boolean claimMarketDelivery(long deliveryId, UUID recipientUuid) {
        requireConnection();
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE market_deliveries SET claimed_at = ?
                WHERE id = ? AND recipient_uuid = ? AND claimed_at IS NULL
                """)) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setLong(2, deliveryId);
            statement.setString(3, recipientUuid.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not claim market delivery", exception);
        }
    }

    public synchronized List<MarketTransaction> marketHistory(UUID uuid, int limit) {
        requireConnection();
        List<MarketTransaction> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, listing_id, buyer_uuid, buyer_name, seller_uuid, seller_name,
                       item_type, amount, price, created_at
                FROM market_transactions
                WHERE buyer_uuid = ? OR seller_uuid = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, uuid.toString());
            statement.setInt(3, Math.max(1, Math.min(100, limit)));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new MarketTransaction(
                            rows.getLong("id"), rows.getLong("listing_id"),
                            UUID.fromString(rows.getString("buyer_uuid")), rows.getString("buyer_name"),
                            UUID.fromString(rows.getString("seller_uuid")), rows.getString("seller_name"),
                            rows.getString("item_type"), rows.getInt("amount"), rows.getLong("price"),
                            rows.getLong("created_at")));
                }
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read market history", exception);
        }
    }

    private MarketListing readMarketListing(ResultSet rows) throws SQLException {
        return new MarketListing(
                rows.getLong("id"), UUID.fromString(rows.getString("seller_uuid")),
                rows.getString("seller_name"), rows.getBytes("item_data"), rows.getString("item_type"),
                rows.getInt("amount"), rows.getLong("price"), rows.getLong("created_at"));
    }

    private String settingColumn(String setting) {
        return switch (setting.toLowerCase()) {
            case "legendary", "legendary_aura" -> "legendary_aura";
            case "mythic", "mythic_aura" -> "mythic_aura";
            case "combat", "combat_particles" -> "combat_particles";
            case "craft", "craft_effects" -> "craft_effects";
            case "materials", "material", "material_fx" -> "material_fx";
            case "others", "show_other_particles" -> "show_other_particles";
            default -> throw new IllegalArgumentException("Unsupported setting: " + setting);
        };
    }

    private void ensureColumn(String table, String column, String definition) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(null, null, table, column)) {
            if (columns.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private void requireConnection() {
        if (connection == null) {
            throw new IllegalStateException("Database is not open");
        }
    }
}
