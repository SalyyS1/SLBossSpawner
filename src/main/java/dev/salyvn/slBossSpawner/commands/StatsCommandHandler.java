package dev.salyvn.slBossSpawner.commands;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.config.MessageManager;
import dev.salyvn.slBossSpawner.statistics.PlayerStatsRow;
import dev.salyvn.slBossSpawner.statistics.StatisticsStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Handles /slboss stats [player] — shows a player's ALL_TIME/__global__ statistics.
 */
public class StatsCommandHandler {
    private final SLBossSpawner plugin;
    private final MessageManager messageManager;

    public StatsCommandHandler(SLBossSpawner plugin, MessageManager messageManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
    }

    public void handle(CommandSender sender, String[] args) {
        StatisticsStore store = plugin.getStatisticsStore();
        if (store == null) {
            sender.sendMessage(messageManager.getMessage("stats-disabled"));
            return;
        }

        // Resolve target player
        UUID targetUuid;
        String targetName;

        if (args.length >= 2) {
            // Explicit player name provided
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(messageManager.getMessage("stats-player-not-found",
                        Map.of("player_name", args[1])));
                return;
            }
            targetUuid = target.getUniqueId();
            targetName = target.getName() != null ? target.getName() : args[1];
        } else if (sender instanceof Player player) {
            targetUuid = player.getUniqueId();
            targetName = player.getName();
        } else {
            sender.sendMessage(messageManager.getMessage("stats-console-requires-player"));
            return;
        }

        // The store is in-memory; keep the read off the command thread so a
        // large YAML snapshot can never delay a chat response.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerStatsRow row = store.getPlayerStats(
                        targetUuid.toString(), "ALL_TIME", "all", "__global__");

                // Send result on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (row == null) {
                        sender.sendMessage(messageManager.getMessage("stats-no-data"));
                        return;
                    }
                    sendStatsDisplay(sender, targetName, row);
                });
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Failed to read player stats: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(messageManager.getMessage("stats-no-data")));
            }
        });
    }

    private void sendStatsDisplay(CommandSender sender, String playerName, PlayerStatsRow row) {
        sender.sendMessage(messageManager.getMessage("stats-header", Map.of("player_name", playerName)));
        sender.sendMessage(messageManager.getMessage("stats-damage", Map.of("value", formatDouble(row.totalDamage()))));
        sender.sendMessage(messageManager.getMessage("stats-kills", Map.of("value", String.valueOf(row.kills()))));
        sender.sendMessage(messageManager.getMessage("stats-participations", Map.of("value", String.valueOf(row.participations()))));
        sender.sendMessage(messageManager.getMessage("stats-tank", Map.of("value", formatDouble(row.totalDamageTaken()))));
        sender.sendMessage(messageManager.getMessage("stats-support", Map.of("value", formatDouble(row.totalSupportScore()))));
        sender.sendMessage(messageManager.getMessage("stats-top1", Map.of("value", String.valueOf(row.top1Count()))));
        sender.sendMessage(messageManager.getMessage("stats-top2", Map.of("value", String.valueOf(row.top2Count()))));
        sender.sendMessage(messageManager.getMessage("stats-top3", Map.of("value", String.valueOf(row.top3Count()))));
        sender.sendMessage(messageManager.getMessage("stats-top1tank", Map.of("value", String.valueOf(row.top1TankCount()))));
        sender.sendMessage(messageManager.getMessage("stats-top1support", Map.of("value", String.valueOf(row.top1SupportCount()))));
        sender.sendMessage(messageManager.getMessage("stats-bestdamage", Map.of("value", formatDouble(row.highestDamage()))));
        sender.sendMessage(messageManager.getMessage("stats-footer"));
    }

    private String formatDouble(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format("%.1f", value);
    }
}
