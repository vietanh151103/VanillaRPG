package vn.vietanhrpg.core.data;

import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.item.Rarity;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
        }
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
    }

    public synchronized long getCoin(UUID uuid) {
        return getLong(uuid, "coin");
    }

    public synchronized long getToken(UUID uuid) {
        return getLong(uuid, "token");
    }

    public synchronized long getStat(UUID uuid, String column) {
        if (!switch (column) {
            case "dungeon_clears", "boss_kills", "rare_crafted", "epic_crafted", "legendary_crafted" -> true;
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

    public synchronized void incrementCraft(UUID uuid, Rarity rarity) {
        String column = switch (rarity) {
            case RARE -> "rare_crafted";
            case EPIC -> "epic_crafted";
            case LEGENDARY -> "legendary_crafted";
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

    private void requireConnection() {
        if (connection == null) {
            throw new IllegalStateException("Database is not open");
        }
    }
}
