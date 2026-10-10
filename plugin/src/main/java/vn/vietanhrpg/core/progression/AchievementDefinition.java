package vn.vietanhrpg.core.progression;

public record AchievementDefinition(
        String id,
        String name,
        String description,
        String counterId,
        long target,
        RewardDefinition reward
) {
}
