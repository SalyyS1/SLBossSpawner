package dev.salyvn.slBossSpawner.commands;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.boss.BossInstance;
import dev.salyvn.slBossSpawner.config.MessageManager;
import dev.salyvn.slBossSpawner.utils.TimeUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class BossCommand implements CommandExecutor, TabCompleter {
    private final SLBossSpawner plugin;
    private final MessageManager messageManager;

    public BossCommand(SLBossSpawner plugin, MessageManager messageManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> sendHelp(sender);
            case "reload" -> {
                if (!sender.hasPermission("slboss.reload")) {
                    sender.sendMessage(messageManager.getMessage("no-permission"));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(messageManager.getMessage("reload-success"));
            }
            case "spawn" -> {
                if (!sender.hasPermission("slboss.spawn")) {
                    sender.sendMessage(messageManager.getMessage("no-permission"));
                    return true;
                }

                if (args.length < 2) {
                    plugin.getBossScheduler().forceSpawnAll();
                    sender.sendMessage(messageManager.getMessage("boss-spawned-all"));
                } else {
                    String bossId = args[1];
                    plugin.getBossScheduler().forceSpawn(bossId);

                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("boss_id", bossId);
                    sender.sendMessage(messageManager.getMessage("boss-spawned-single", placeholders));
                }
            }
            case "despawn" -> {
                if (!sender.hasPermission("slboss.despawn")) {
                    sender.sendMessage(messageManager.getMessage("no-permission"));
                    return true;
                }

                if (args.length < 2) {
                    plugin.getBossScheduler().despawnAll();
                    sender.sendMessage(messageManager.getMessage("boss-despawned-all"));
                } else {
                    String bossId = args[1];
                    plugin.getBossScheduler().despawn(bossId);

                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("boss_id", bossId);
                    sender.sendMessage(messageManager.getMessage("boss-despawned-single", placeholders));
                }
            }
            case "list" -> {
                if (!sender.hasPermission("slboss.info")) {
                    sender.sendMessage(messageManager.getMessage("no-permission"));
                    return true;
                }
                sendList(sender);
            }
            case "info" -> {
                if (!sender.hasPermission("slboss.info")) {
                    sender.sendMessage(messageManager.getMessage("no-permission"));
                    return true;
                }
                sendInfo(sender);
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        String version = plugin.getDescription().getVersion();
        sender.sendMessage(messageManager.getMessage("help-header", Map.of("version", version)));
        sender.sendMessage(messageManager.getMessage("help-usage"));
        sender.sendMessage("");
        if (sender.hasPermission("slboss.reload"))
            sender.sendMessage(messageManager.getMessage("help-reload"));
        if (sender.hasPermission("slboss.spawn"))
            sender.sendMessage(messageManager.getMessage("help-spawn"));
        if (sender.hasPermission("slboss.despawn"))
            sender.sendMessage(messageManager.getMessage("help-despawn"));
        if (sender.hasPermission("slboss.info")) {
            sender.sendMessage(messageManager.getMessage("help-info"));
            sender.sendMessage(messageManager.getMessage("help-list"));
        }
        sender.sendMessage(messageManager.getMessage("help-help"));
        sender.sendMessage(messageManager.getMessage("help-footer"));
    }

    private void sendList(CommandSender sender) {
        Map<String, BossInstance> instances = plugin.getBossScheduler().getAllBossInstances();

        sender.sendMessage(messageManager.getMessage("list-header"));

        for (Map.Entry<String, BossInstance> entry : instances.entrySet()) {
            String bossId = entry.getKey();
            BossInstance instance = entry.getValue();

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("boss_id", bossId);
            placeholders.put("mythicmob", instance.getConfig().getMythicMobId());
            String status = instance.isAlive()
                ? messageManager.getRawMessage("status-alive")
                : messageManager.getRawMessage("status-dead");
            placeholders.put("status", status);

            sender.sendMessage(messageManager.getMessage("list-entry", placeholders));
        }

        sender.sendMessage(messageManager.getMessage("list-footer"));
    }

    private void sendInfo(CommandSender sender) {
        Map<String, BossInstance> instances = plugin.getBossScheduler().getAllBossInstances();

        sender.sendMessage(messageManager.getMessage("info-header"));

        for (Map.Entry<String, BossInstance> entry : instances.entrySet()) {
            String bossId = entry.getKey();
            BossInstance instance = entry.getValue();

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("boss_id", bossId);

            String currentBoss = instance.isAlive() ? instance.getConfig().getMythicMobId() : "None";
            placeholders.put("current_boss", currentBoss);

            long secondsUntilNext = instance.getSecondsUntilNext();
            String nextTime = secondsUntilNext >= 0 ? TimeUtils.formatDuration(secondsUntilNext) : "N/A";
            placeholders.put("next_time", nextTime);

            String nextFormatted = instance.getNextSpawnFormatted();
            placeholders.put("next_formatted", nextFormatted);

            long secondsUntilExpire = instance.getSecondsUntilExpire();
            String expireTimeStr = secondsUntilExpire >= 0 ? TimeUtils.formatDuration(secondsUntilExpire) : "N/A";
            placeholders.put("expire_time", expireTimeStr);

            sender.sendMessage(messageManager.getMessage("info-boss-header", placeholders));
            sender.sendMessage(messageManager.getMessage("info-current", placeholders));
            sender.sendMessage(messageManager.getMessage("info-next", placeholders));
            sender.sendMessage(messageManager.getMessage("info-expire", placeholders));
        }

        sender.sendMessage(messageManager.getMessage("info-footer"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();

            List<String> subcommands = Arrays.asList("help", "reload", "spawn", "despawn", "info", "list");
            for (String sub : subcommands) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }

            return completions;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("despawn"))) {
            String input = args[1].toLowerCase();
            return plugin.getBossScheduler().getAllBossInstances().keySet().stream()
                .filter(id -> id.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
