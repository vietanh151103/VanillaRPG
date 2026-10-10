package vn.vietanhrpg.core.dungeon;

import java.util.List;

public record DungeonRewardDefinition(
        long coinMin,
        long coinMax,
        long tokenMin,
        long tokenMax,
        List<DungeonCustomReward> customRewards
) {}
