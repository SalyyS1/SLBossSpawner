package dev.salyvn.slBossSpawner.reward;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.persist.PendingRewardManager;
import dev.salyvn.slBossSpawner.persist.PendingRewardManager.PendingReward;
import dev.salyvn.slBossSpawner.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/**
 * Delivers pending rewards to players when they join.
 */
public class PendingRewardListener implements Listener {
    private final SLBossSpawner plugin;

    public PendingRewardListener(SLBossSpawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PendingRewardManager manager = plugin.getPendingRewardManager();

        if (!manager.hasPending(player.getUniqueId())) return;

        // Delay delivery by 2 seconds to ensure player is fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<PendingReward> rewards = manager.claimRewards(player.getUniqueId());
            if (rewards.isEmpty()) return;

            for (PendingReward reward : rewards) {
                if ("command".equals(reward.type()) && reward.command() != null) {
                    String cmd = reward.command();
                    if (cmd.matches(".*[;&|`$].*")) {
                        plugin.getLogger().warning("Blocked suspicious pending command: " + cmd);
                        continue;
                    }
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                } else if ("item".equals(reward.type())) {
                    deliverItem(player, reward);
                }
            }

            String msg = plugin.getMessageManager().getRawMessage("pending-rewards-delivered",
                Map.of("count", String.valueOf(rewards.size())));
            player.sendMessage(msg);

            plugin.debug("Delivered " + rewards.size() + " pending rewards to " + player.getName());
        }, 40L); // 2 seconds delay
    }

    private void deliverItem(Player player, PendingReward reward) {
        // Try MMOItems first
        if (reward.mmoitemsId() != null && Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            try {
                var mmoItems = net.Indyuce.mmoitems.MMOItems.plugin;
                var type = mmoItems.getTypes().get(reward.material());
                if (type != null) {
                    ItemStack stack = mmoItems.getItem(type, reward.mmoitemsId());
                    if (stack != null) {
                        stack.setAmount(reward.amount());
                        giveOrDrop(player, stack);
                        return;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to deliver MMOItems pending reward: " + e.getMessage());
            }
        }

        // MythicMobs items (stored as "mythicmobs:ItemName")
        if (reward.material() != null && reward.material().startsWith("mythicmobs:")) {
            String mythicId = reward.material().substring("mythicmobs:".length());
            if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
                try {
                    ItemStack stack = io.lumine.mythic.bukkit.MythicBukkit.inst()
                            .getItemManager().getItemStack(mythicId);
                    if (stack != null) {
                        stack.setAmount(reward.amount());
                        giveOrDrop(player, stack);
                        return;
                    }
                    plugin.getLogger().warning("MythicMobs item '" + mythicId + "' not found for pending reward");
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to deliver MythicMobs pending reward: " + e.getMessage());
                }
            }
            return; // Don't fall through to vanilla for mythicmobs: prefix
        }

        // Vanilla item
        if (reward.material() != null) {
            try {
                Material mat = Material.valueOf(reward.material().toUpperCase());
                ItemStack stack = new ItemStack(mat, reward.amount());

                if (reward.displayName() != null && !reward.displayName().isEmpty()) {
                    ItemMeta meta = stack.getItemMeta();
                    if (meta != null) {
                        meta.displayName(ColorUtils.toComponent(reward.displayName()));
                        stack.setItemMeta(meta);
                    }
                }

                giveOrDrop(player, stack);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in pending reward: " + reward.material());
            }
        }
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        var leftover = player.getInventory().addItem(stack);
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }
}
