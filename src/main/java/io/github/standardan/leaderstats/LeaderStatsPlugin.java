package io.github.standardan.leaderstats;

import io.github.standardan.leaderstats.command.StatCommand;
import io.github.standardan.leaderstats.hologram.HologramManager;
import io.github.standardan.leaderstats.listener.StatListener;
import io.github.standardan.leaderstats.placeholder.StatExpansion;
import io.github.standardan.leaderstats.storage.StatStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class LeaderStatsPlugin extends JavaPlugin {

    private StatStore store;
    private StatManager manager;
    private HologramManager holograms;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        store = new StatStore(this);
        try {
            store.connect();
        } catch (Exception e) {
            getLogger().severe("Failed to open stats DB, disabling: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        manager = new StatManager(this, store);
        holograms = new HologramManager(this, manager);

        getServer().getPluginManager().registerEvents(new StatListener(manager), this);

        PluginCommand command = Objects.requireNonNull(getCommand("stats"), "stats missing from plugin.yml");
        StatCommand handler = new StatCommand(this, manager, holograms);
        command.setExecutor(handler);
        command.setTabCompleter(handler);

        // Hook PlaceholderAPI only if present (this is the first line that loads
        // the StatExpansion class, and therefore PlaceholderAPI's classes).
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new StatExpansion(manager).register();
            getLogger().info("Hooked into PlaceholderAPI.");
        }

        // Load anyone already online (handles /reload).
        getServer().getOnlinePlayers().forEach(manager::handleJoin);
        holograms.spawnAll();

        int refresh = Math.max(5, getConfig().getInt("refresh-seconds", 60));
        long ticks = refresh * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> {
            manager.addPlaytime(refresh);
            manager.flushAndRefresh();
            holograms.refresh();
        }, 40L, ticks);

        getLogger().info("LeaderStats enabled.");
    }

    @Override
    public void onDisable() {
        if (holograms != null) {
            holograms.removeAll();
        }
        if (manager != null) {
            manager.saveAll(); // queued saves finish during store.close()
        }
        if (store != null) {
            store.close();
        }
    }

    /** Run a task on the main thread (used by async DB callbacks). */
    public void sync(Runnable task) {
        if (isEnabled()) {
            getServer().getScheduler().runTask(this, task);
        }
    }
}
