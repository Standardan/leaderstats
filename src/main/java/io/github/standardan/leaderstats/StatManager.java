package io.github.standardan.leaderstats;

import io.github.standardan.leaderstats.storage.LeaderEntry;
import io.github.standardan.leaderstats.storage.StatStore;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps every online player's stats in memory (so increments and placeholder
 * lookups are instant) and periodically flushes to the database and rebuilds
 * the leaderboard snapshot that holograms and placeholders read from.
 */
public final class StatManager {

    private final LeaderStatsPlugin plugin;
    private final StatStore store;

    // Live stats for online players (touched only on the main thread).
    private final Map<UUID, Map<StatType, Long>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    // Latest leaderboard snapshot per stat (rebuilt async, read anywhere).
    private final Map<StatType, List<LeaderEntry>> leaderboards = new ConcurrentHashMap<>();

    public StatManager(LeaderStatsPlugin plugin, StatStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void handleJoin(Player player) {
        UUID id = player.getUniqueId();
        names.put(id, player.getName());
        store.load(id).thenAccept(record -> plugin.sync(() -> {
            Map<StatType, Long> values = new EnumMap<>(StatType.class);
            for (StatType type : StatType.values()) {
                values.put(type, record.values().getOrDefault(type, 0L));
            }
            cache.put(id, values);
        }));
    }

    public void handleQuit(UUID id) {
        flush(id);
        cache.remove(id);
        names.remove(id);
    }

    public void increment(UUID id, StatType type, long amount) {
        Map<StatType, Long> values = cache.get(id);
        if (values != null) {
            values.merge(type, amount, Long::sum);
        }
    }

    public long get(UUID id, StatType type) {
        Map<StatType, Long> values = cache.get(id);
        return values == null ? 0L : values.getOrDefault(type, 0L);
    }

    public List<LeaderEntry> leaderboard(StatType type) {
        return leaderboards.getOrDefault(type, List.of());
    }

    /** Credit everyone currently online with this many seconds of playtime. */
    public void addPlaytime(long seconds) {
        cache.values().forEach(values -> values.merge(StatType.PLAYTIME, seconds, Long::sum));
    }

    /** Flush all online players, then rebuild leaderboard snapshots. */
    public void flushAndRefresh() {
        saveAll();
        for (StatType type : StatType.values()) {
            store.topN(type, 10).thenAccept(list -> leaderboards.put(type, list));
        }
    }

    /** Queue a save of every online player's stats (used on shutdown too). */
    public void saveAll() {
        cache.keySet().forEach(this::flush);
    }

    private void flush(UUID id) {
        Map<StatType, Long> values = cache.get(id);
        String name = names.get(id);
        if (values != null && name != null) {
            store.save(id, name, new EnumMap<>(values));
        }
    }

    /** Human-friendly value: playtime as Xh Ym, everything else as a number. */
    public String format(StatType type, long value) {
        if (type != StatType.PLAYTIME) {
            return Long.toString(value);
        }
        long hours = value / 3600;
        long minutes = (value % 3600) / 60;
        return hours + "h " + minutes + "m";
    }
}
