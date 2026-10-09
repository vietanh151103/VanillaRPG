package vn.vietanhrpg.core.item;

import net.kyori.adventure.text.format.NamedTextColor;

public enum Rarity {
    COMMON(NamedTextColor.WHITE, 0),
    UNCOMMON(NamedTextColor.GREEN, 1),
    RARE(NamedTextColor.BLUE, 2),
    EPIC(NamedTextColor.DARK_PURPLE, 3),
    LEGENDARY(NamedTextColor.GOLD, 4);

    private final NamedTextColor color;
    private final int affixCount;

    Rarity(NamedTextColor color, int affixCount) {
        this.color = color;
        this.affixCount = affixCount;
    }

    public NamedTextColor color() {
        return color;
    }

    public int affixCount() {
        return affixCount;
    }

    public Rarity next() {
        int next = ordinal() + 1;
        return next >= values().length ? null : values()[next];
    }
}
