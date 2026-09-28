package dev.salyvn.slBossSpawner.reward;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.persist.PendingRewardManager;
import dev.salyvn.slBossSpawner.persist.PendingRewardManager.PendingReward;
import dev.salyvn.slBossSpawner.utils.CommandValidator;
import dev.salyvn.slBossSpawner.utils.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Delivers pending rewards to players when they join.
 *
 * H13 FIX: Command validation uses CommandValidator.isValid() (was inline regex).
 * H9  FIX: Item creation delegated to ItemFactory.createFromPending() (was inline).
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
            if (!player.isOnline()) return;

            String playerName = RewardPlayerName.resolve(player);
            if (playerName == null) {
                plugin.getLogger().warning("Cannot deliver pending rewards: unsafe player name for UUID "
                        + player.getUniqueId());
                return;
            }

            List<PendingReward> rewards = manager.claimRewards(player.getUniqueId());
            if (rewards.isEmpty()) return;

            List<PendingReward> failed = new ArrayList<>();
            int delivered = 0;
            for (PendingReward reward : rewards) {
                if ("command".equals(reward.type()) && reward.command() != null) {
                    // H13 FIX: use CommandValidator instead of inline regex
                    if (!CommandValidator.isValid(reward.command())) {
                        plugin.getLogger().warning("Blocked suspicious pending command: "
                                + reward.command());
                        continue;
                    }
                    String command = RewardPlayerName.resolvePendingCommand(
                            reward.command(), playerName);
                    if (!command.equals(reward.command())
                            && !reward.command().contains("{player}")) {
                        plugin.debug("Migrated legacy pending command target for " + playerName);
                    }
                    try {
                        if (Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                            delivered++;
                        } else {
                            failed.add(reward);
                            plugin.getLogger().warning("Pending reward command was not accepted: " + command);
                        }
                    } catch (Exception e) {
                        failed.add(reward);
                        plugin.getLogger().warning("Pending reward command failed: " + e.getMessage());
                    }

                } else if ("item".equals(reward.type())) {
                    try {
                        // H9 FIX: delegate to ItemFactory.createFromPending()
                        ItemStack stack = ItemFactory.createFromPending(
                                reward.material(), reward.displayName(),
                                reward.amount(), reward.mmoitemsId(),
                                plugin.getLogger());
                        if (stack == null) {
                            failed.add(reward);
                            continue;
                        }
                        giveOrDrop(player, stack);
                        delivered++;
                    } catch (Exception e) {
                        failed.add(reward);
                        plugin.getLogger().warning("Pending item reward failed: " + e.getMessage());
                    }
                }
            }

            manager.requeueRewards(player.getUniqueId(), failed);

            if (delivered > 0) {
                String msg = plugin.getMessageManager().getRawMessage("pending-rewards-delivered",
                        Map.of("count", String.valueOf(delivered)));
                player.sendMessage(msg);
            }
            plugin.debug("Delivered " + delivered + " pending rewards to " + playerName
                    + "; " + failed.size() + " remain queued");
        }, 40L);
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        var leftover = player.getInventory().addItem(stack);
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }
}
