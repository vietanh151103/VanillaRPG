package vn.vietanhrpg.core.item;

import java.util.Arrays;
import java.util.Optional;

public enum Affix {
    DAMAGE_BONUS("damage_bonus", "Attack Damage", "+%.1f%%"),
    CRIT_CHANCE("crit_chance", "Critical Chance", "+%.1f%%"),
    CRIT_DAMAGE("crit_damage", "Critical Damage", "+%.1f%%"),
    LIFESTEAL("lifesteal", "Lifesteal", "+%.1f%%");

    private final String id;
    private final String display;
    private final String format;

    Affix(String id, String display, String format) {
        this.id = id;
        this.display = display;
        this.format = format;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public String formatted(double value) {
        return String.format(format, value);
    }

    public static Optional<Affix> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(value -> value.id.equalsIgnoreCase(id))
                .findFirst();
    }
}
