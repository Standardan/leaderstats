package io.github.standardan.leaderstats.listener;

import io.github.standardan.leaderstats.StatManager;
import io.github.standardan.leaderstats.StatType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Feeds the stat manager: kills/deaths, mob kills, blocks mined, and join/quit
 * to load and save stats.
 */
public final class StatListener implements Listener {

    private final StatManager manager;

    public StatListener(StatManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        manager.increment(victim.getUniqueId(), StatType.DEATHS, 1);
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            manager.increment(killer.getUniqueId(), StatType.KILLS, 1);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return; // player deaths handled above
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            manager.increment(killer.getUniqueId(), StatType.MOB_KILLS, 1);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        manager.increment(event.getPlayer().getUniqueId(), StatType.BLOCKS_MINED, 1);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handleQuit(event.getPlayer().getUniqueId());
    }
}
