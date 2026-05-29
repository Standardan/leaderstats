package io.github.standardan.leaderstats.storage;

import io.github.standardan.leaderstats.StatType;

import java.util.Map;

/**
 * A player's loaded stats. {@code name} is null if the player has no row yet.
 */
public record StatRecord(String name, Map<StatType, Long> values) {

    public boolean isNew() {
        return name == null;
    }
}
