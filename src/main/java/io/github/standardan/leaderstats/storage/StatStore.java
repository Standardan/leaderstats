package io.github.standardan.leaderstats.storage;

import io.github.standardan.leaderstats.StatType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SQLite store for player stats. All queries run on a single background thread.
 *
 * Note on the topN query: the column name is interpolated into the SQL string,
 * but it always comes from our own StatType enum (never user input), so there's
 * no injection surface. Values are still bound as parameters.
 */
public final class StatStore {

    private final Plugin plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LeaderStats-DB");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;

    public StatStore(Plugin plugin) {
        this.plugin = plugin;
    }

    public void connect() throws SQLException, ClassNotFoundException {
        plugin.getDataFolder().mkdirs();
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:"
                + new File(plugin.getDataFolder(), "stats.db").getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS stats (
                        uuid         TEXT PRIMARY KEY,
                        name         TEXT,
                        kills        INTEGER NOT NULL DEFAULT 0,
                        deaths       INTEGER NOT NULL DEFAULT 0,
                        mob_kills    INTEGER NOT NULL DEFAULT 0,
                        blocks_mined INTEGER NOT NULL DEFAULT 0,
                        playtime     INTEGER NOT NULL DEFAULT 0
                    )
                    """);
        }
    }

    public CompletableFuture<StatRecord> load(UUID uuid) {
        return supply(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT name, kills, deaths, mob_kills, blocks_mined, playtime FROM stats WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    Map<StatType, Long> values = new EnumMap<>(StatType.class);
                    if (!rs.next()) {
                        return new StatRecord(null, values);
                    }
                    for (StatType type : StatType.values()) {
                        values.put(type, rs.getLong(type.column));
                    }
                    return new StatRecord(rs.getString("name"), values);
                }
            }
        });
    }

    public CompletableFuture<Void> save(UUID uuid, String name, Map<StatType, Long> values) {
        return supply(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO stats(uuid, name, kills, deaths, mob_kills, blocks_mined, playtime) "
                            + "VALUES(?, ?, ?, ?, ?, ?, ?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, "
                            + "kills = excluded.kills, deaths = excluded.deaths, "
                            + "mob_kills = excluded.mob_kills, blocks_mined = excluded.blocks_mined, "
                            + "playtime = excluded.playtime")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setLong(3, values.getOrDefault(StatType.KILLS, 0L));
                ps.setLong(4, values.getOrDefault(StatType.DEATHS, 0L));
                ps.setLong(5, values.getOrDefault(StatType.MOB_KILLS, 0L));
                ps.setLong(6, values.getOrDefault(StatType.BLOCKS_MINED, 0L));
                ps.setLong(7, values.getOrDefault(StatType.PLAYTIME, 0L));
                ps.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<List<LeaderEntry>> topN(StatType type, int limit) {
        return supply(() -> {
            List<LeaderEntry> out = new ArrayList<>();
            String sql = "SELECT name, " + type.column + " AS v FROM stats "
                    + "WHERE name IS NOT NULL ORDER BY v DESC LIMIT ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new LeaderEntry(rs.getString("name"), rs.getLong("v")));
                    }
                }
            }
            return out;
        });
    }

    private <T> CompletableFuture<T> supply(SqlSupplier<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                future.complete(work.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Error closing stats DB: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws Exception;
    }
}
