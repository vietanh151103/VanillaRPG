package vn.vietanhrpg.core.progression;

import java.util.Map;

public record RewardDefinition(
        long coin,
        long token,
        Map<String, Integer> customMaterials,
        String titleId
) {
}
