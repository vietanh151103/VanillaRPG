package vn.vietanhrpg.core.dungeon;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class DungeonRun {
    private final UUID playerId;
    private final String dungeonId;
    private final Location returnLocation;
    private final long startedAt;
    private final long deadlineAt;
    private final int targetKills;
    private final Set<UUID> spawnedMobs = new HashSet<>();
    private int kills;

    public DungeonRun(UUID playerId, String dungeonId, Location returnLocation,
                      long startedAt, long deadlineAt, int targetKills) {
        this.playerId = playerId;
        this.dungeonId = dungeonId;
        this.returnLocation = returnLocation;
        this.startedAt = startedAt;
        this.deadlineAt = deadlineAt;
        this.targetKills = targetKills;
    }

    public UUID playerId() { return playerId; }
    public String dungeonId() { return dungeonId; }
    public Location returnLocation() { return returnLocation; }
    public long startedAt() { return startedAt; }
    public long deadlineAt() { return deadlineAt; }
    public int targetKills() { return targetKills; }
    public int kills() { return kills; }
    public int addKill() { return ++kills; }
    public Set<UUID> spawnedMobs() { return spawnedMobs; }
}
