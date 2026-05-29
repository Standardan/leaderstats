package io.github.standardan.leaderstats;

/**
 * The stats we track. Each has a display name, a database column, and a
 * placeholder key (no underscores, so PlaceholderAPI parsing stays simple).
 */
public enum StatType {
    KILLS("Kills", "kills", "kills"),
    DEATHS("Deaths", "deaths", "deaths"),
    MOB_KILLS("Mob Kills", "mob_kills", "mobkills"),
    BLOCKS_MINED("Blocks Mined", "blocks_mined", "blocksmined"),
    PLAYTIME("Playtime", "playtime", "playtime");

    public final String display;
    public final String column;
    public final String key;

    StatType(String display, String column, String key) {
        this.display = display;
        this.column = column;
        this.key = key;
    }

    public static StatType fromKey(String input) {
        if (input == null) return null;
        for (StatType t : values()) {
            if (t.key.equalsIgnoreCase(input) || t.column.equalsIgnoreCase(input)
                    || t.name().equalsIgnoreCase(input)) {
                return t;
            }
        }
        return null;
    }
}
