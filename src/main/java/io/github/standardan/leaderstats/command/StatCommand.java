package io.github.standardan.leaderstats.command;

import io.github.standardan.leaderstats.LeaderStatsPlugin;
import io.github.standardan.leaderstats.StatManager;
import io.github.standardan.leaderstats.StatType;
import io.github.standardan.leaderstats.hologram.HologramManager;
import io.github.standardan.leaderstats.storage.LeaderEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /stats, /stats top <stat>, /stats hologram <stat>|remove, /stats reload.
 */
public final class StatCommand implements CommandExecutor, TabCompleter {

    private final LeaderStatsPlugin plugin;
    private final StatManager manager;
    private final HologramManager holograms;

    public StatCommand(LeaderStatsPlugin plugin, StatManager manager, HologramManager holograms) {
        this.plugin = plugin;
        this.manager = manager;
        this.holograms = holograms;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            showOwnStats(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "top" -> showTop(sender, args);
            case "hologram" -> hologram(sender, args);
            case "reload" -> reload(sender);
            default -> sender.sendMessage(Component.text("/stats [top <stat>|hologram <stat>|reload]",
                    NamedTextColor.GRAY));
        }
        return true;
    }

    private void showOwnStats(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players have stats. Try /stats top <stat>.");
            return;
        }
        player.sendMessage(Component.text("Your stats:", NamedTextColor.AQUA));
        for (StatType type : StatType.values()) {
            player.sendMessage(Component.text("  " + type.display + ": ", NamedTextColor.GRAY)
                    .append(Component.text(manager.format(type, manager.get(player.getUniqueId(), type)),
                            NamedTextColor.WHITE)));
        }
    }

    private void showTop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /stats top <stat>", NamedTextColor.RED));
            return;
        }
        StatType type = StatType.fromKey(args[1]);
        if (type == null) {
            sender.sendMessage(Component.text("Unknown stat. Try: " + statKeys(), NamedTextColor.RED));
            return;
        }
        List<LeaderEntry> board = manager.leaderboard(type);
        sender.sendMessage(Component.text(type.display + " - Top " + board.size(), NamedTextColor.GOLD));
        int rank = 1;
        for (LeaderEntry entry : board) {
            sender.sendMessage(Component.text("  " + rank++ + ". ", NamedTextColor.YELLOW)
                    .append(Component.text(entry.name() + " ", NamedTextColor.WHITE))
                    .append(Component.text("- " + manager.format(type, entry.value()), NamedTextColor.GREEN)));
        }
        if (board.isEmpty()) {
            sender.sendMessage(Component.text("  (no data yet)", NamedTextColor.GRAY));
        }
    }

    private void hologram(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Run this in-game.");
            return;
        }
        if (!player.hasPermission("leaderstats.admin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("remove")) {
            int removed = holograms.removeNear(player.getLocation(), 5);
            player.sendMessage(Component.text("Removed " + removed + " nearby hologram(s).", NamedTextColor.GREEN));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /stats hologram <stat> | /stats hologram remove",
                    NamedTextColor.RED));
            return;
        }
        StatType type = StatType.fromKey(args[1]);
        if (type == null) {
            player.sendMessage(Component.text("Unknown stat. Try: " + statKeys(), NamedTextColor.RED));
            return;
        }
        holograms.create(player.getLocation(), type);
        player.sendMessage(Component.text("Created a " + type.display + " leaderboard hologram here.",
                NamedTextColor.GREEN));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("leaderstats.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        plugin.reloadConfig();
        holograms.spawnAll();
        sender.sendMessage(Component.text("LeaderStats reloaded.", NamedTextColor.GREEN));
    }

    private String statKeys() {
        List<String> keys = new ArrayList<>();
        for (StatType t : StatType.values()) keys.add(t.key);
        return String.join(", ", keys);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("top", "hologram", "reload"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("top") || args[0].equalsIgnoreCase("hologram"))) {
            List<String> opts = new ArrayList<>();
            for (StatType t : StatType.values()) opts.add(t.key);
            if (args[0].equalsIgnoreCase("hologram")) opts.add("remove");
            return filter(opts, args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream().filter(s -> s.startsWith(prefix.toLowerCase(Locale.ROOT))).toList();
    }
}
