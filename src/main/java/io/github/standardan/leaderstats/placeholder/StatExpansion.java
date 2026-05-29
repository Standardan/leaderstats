package io.github.standardan.leaderstats.placeholder;

import io.github.standardan.leaderstats.StatManager;
import io.github.standardan.leaderstats.StatType;
import io.github.standardan.leaderstats.storage.LeaderEntry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * PlaceholderAPI expansion. The ONLY class that imports PlaceholderAPI, so it's
 * loaded only when we register it (which we only do if PlaceholderAPI is
 * installed) - the same soft-dependency isolation used for Vault elsewhere.
 *
 * Placeholders:
 *   %leaderstats_kills%                    - the player's own value
 *   %leaderstats_top_kills_1_name%         - #1 on the kills board (name)
 *   %leaderstats_top_kills_1_value%        - #1 on the kills board (value)
 * (stat keys: kills, deaths, mobkills, blocksmined, playtime)
 */
public final class StatExpansion extends PlaceholderExpansion {

    private final StatManager manager;

    public StatExpansion(StatManager manager) {
        this.manager = manager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "leaderstats";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Dan";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; // stay registered across PlaceholderAPI reloads
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.startsWith("top_")) {
            return topPlaceholder(params.substring(4));
        }
        StatType type = StatType.fromKey(params);
        if (type == null || player == null) {
            return null;
        }
        return manager.format(type, manager.get(player.getUniqueId(), type));
    }

    /** Handles top_<stat>_<rank>_<name|value>. */
    private String topPlaceholder(String rest) {
        String[] parts = rest.split("_");
        if (parts.length != 3) {
            return null;
        }
        StatType type = StatType.fromKey(parts[0]);
        if (type == null) {
            return null;
        }
        int rank;
        try {
            rank = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        List<LeaderEntry> board = manager.leaderboard(type);
        boolean wantName = parts[2].equalsIgnoreCase("name");
        if (rank < 1 || rank > board.size()) {
            return wantName ? "-" : "0";
        }
        LeaderEntry entry = board.get(rank - 1);
        return wantName ? entry.name() : manager.format(type, entry.value());
    }
}
