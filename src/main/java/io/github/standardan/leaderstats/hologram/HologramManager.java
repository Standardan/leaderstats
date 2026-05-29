package io.github.standardan.leaderstats.hologram;

import io.github.standardan.leaderstats.StatManager;
import io.github.standardan.leaderstats.StatType;
import io.github.standardan.leaderstats.storage.LeaderEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Floating leaderboard holograms backed by TextDisplay entities. Definitions
 * (location + stat) live in config.yml; the entities themselves are transient
 * and re-spawned from config on enable, so there are never orphans.
 */
public final class HologramManager {

    private record Holo(TextDisplay display, StatType stat) {}

    private final JavaPlugin plugin;
    private final StatManager manager;
    private final List<Holo> active = new ArrayList<>();

    public HologramManager(JavaPlugin plugin, StatManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /** Spawn a hologram for every definition in config. */
    public void spawnAll() {
        removeAll();
        for (Map<String, Object> entry : readEntries()) {
            StatType stat = StatType.fromKey(String.valueOf(entry.get("stat")));
            World world = Bukkit.getWorld(String.valueOf(entry.get("world")));
            if (stat == null || world == null) {
                continue;
            }
            Location loc = new Location(world, toDouble(entry.get("x")),
                    toDouble(entry.get("y")), toDouble(entry.get("z")));
            spawn(loc, stat);
        }
    }

    public void refresh() {
        for (Holo holo : active) {
            if (holo.display().isValid()) {
                holo.display().setText(board(holo.stat()));
            }
        }
    }

    /** Create a new hologram at the player's location and persist it. */
    public void create(Location loc, StatType stat) {
        List<Map<String, Object>> entries = readEntries();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("world", loc.getWorld().getName());
        entry.put("x", loc.getX());
        entry.put("y", loc.getY());
        entry.put("z", loc.getZ());
        entry.put("stat", stat.key);
        entries.add(entry);
        writeEntries(entries);
        spawn(loc, stat);
    }

    /** Remove every hologram within the given radius of a point; returns how many. */
    public int removeNear(Location near, double radius) {
        List<Map<String, Object>> kept = new ArrayList<>();
        int removed = 0;
        for (Map<String, Object> entry : readEntries()) {
            World world = Bukkit.getWorld(String.valueOf(entry.get("world")));
            Location loc = world == null ? null : new Location(world,
                    toDouble(entry.get("x")), toDouble(entry.get("y")), toDouble(entry.get("z")));
            if (loc != null && near.getWorld().equals(world) && loc.distanceSquared(near) <= radius * radius) {
                removed++;
            } else {
                kept.add(entry);
            }
        }
        writeEntries(kept);
        spawnAll(); // re-sync entities to the trimmed config
        return removed;
    }

    public void removeAll() {
        active.forEach(h -> {
            if (h.display().isValid()) h.display().remove();
        });
        active.clear();
    }

    private void spawn(Location loc, StatType stat) {
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setText(board(stat));
        });
        active.add(new Holo(display, stat));
    }

    private String board(StatType stat) {
        StringBuilder sb = new StringBuilder("§6§l").append(stat.display).append(" §7Leaderboard");
        int rank = 1;
        for (LeaderEntry entry : manager.leaderboard(stat)) {
            sb.append("\n§e").append(rank++).append(". §f").append(entry.name())
                    .append(" §8- §a").append(manager.format(stat, entry.value()));
        }
        if (rank == 1) {
            sb.append("\n§7No data yet");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readEntries() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<?, ?> raw : plugin.getConfig().getMapList("holograms")) {
            Map<String, Object> copy = new LinkedHashMap<>();
            raw.forEach((k, v) -> copy.put(String.valueOf(k), v));
            out.add(copy);
        }
        return out;
    }

    private void writeEntries(List<Map<String, Object>> entries) {
        plugin.getConfig().set("holograms", entries);
        plugin.saveConfig();
    }

    private double toDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }
}
