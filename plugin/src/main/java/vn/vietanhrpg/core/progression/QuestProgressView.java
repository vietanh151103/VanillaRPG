package vn.vietanhrpg.core.progression;

public record QuestProgressView(
        QuestDefinition definition,
        long progress,
        boolean rewarded,
        String periodKey
) {
    public boolean complete() {
        return progress >= definition.target();
    }
}
