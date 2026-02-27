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
            List<PendingReward> rewards = manager.claimRewards(player.getUniqueId());
            if (rewards.isEmpty()) return;

            for (PendingReward reward : rewards) {
                if ("command".equals(reward.type()) && reward.command() != null) {
                    // H13 FIX: use CommandValidator instead of inline regex
                    if (!CommandValidator.isValid(reward.command())) {
                        plugin.getLogger().warning("Blocked suspicious pending command: "
                                + reward.command());
                        continue;
                    }
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), reward.command());

                } else if ("item".equals(reward.type())) {
                    // H9 FIX: delegate to ItemFactory.createFromPending()
                    ItemStack stack = ItemFactory.createFromPending(
                            reward.material(), reward.displayName(),
                            reward.amount(), reward.mmoitemsId(),
                            plugin.getLogger());
                    if (stack != null) {
                        giveOrDrop(player, stack);
                    }
                }
            }

            String msg = plugin.getMessageManager().getRawMessage("pending-rewards-delivered",
                    Map.of("count", String.valueOf(rewards.size())));
            player.sendMessage(msg);
            plugin.debug("Delivered " + rewards.size() + " pending rewards to " + player.getName());
        }, 40L);
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        var leftover = player.getInventory().addItem(stack);
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }
}
