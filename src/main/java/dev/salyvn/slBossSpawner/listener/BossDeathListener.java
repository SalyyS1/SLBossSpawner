package dev.salyvn.slBossSpawner.listener;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.boss.BossInstance;
import dev.salyvn.slBossSpawner.boss.BossScheduler;
import dev.salyvn.slBossSpawner.reward.RewardManager;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Listens for MythicMobDeathEvent to detect boss deaths.
 * Builds damage, tank, and support results, then distributes rewards per category.
 */
public class BossDeathListener implements Listener {
    private final SLBossSpawner plugin;
    private final DamageTracker damageTracker;
    private final TankTracker tankTracker;
    private final SupportTracker supportTracker;
    private final RewardManager rewardManager;

    public BossDeathListener(SLBossSpawner plugin, DamageTracker damageTracker,
                              TankTracker tankTracker, SupportTracker supportTracker,
                              RewardManager rewardManager) {
        this.plugin = plugin;
        this.damageTracker = damageTracker;
        this.tankTracker = tankTracker;
        this.supportTracker = supportTracker;
        this.rewardManager = rewardManager;
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        Entity entity = event.getEntity();
        UUID entityUuid = entity.getUniqueId();

        BossScheduler scheduler = plugin.getBossScheduler();
        BossInstance instance = scheduler.findByEntityUuid(entityUuid);

        if (instance == null) return;

        plugin.debug("MythicMobDeathEvent fired for boss: " + instance.getConfig().getId());

        // Build all results before cleanup
        DamageResult damageResult = damageTracker.buildResult(entityUuid);
        TankResult tankResult = tankTracker.buildResult(entityUuid);
        SupportResult supportResult = supportTracker.buildResult(entityUuid);

        // Get killer
        Player killer = (event.getKiller() instanceof Player p) ? p : null;
        String killerName = killer != null ? killer.getName() : null;

        // Broadcast death
        plugin.getBroadcastManager().broadcastDeath(instance.getConfig(), killerName);

        // Distribute rewards per category
        if (instance.getConfig().isRewardsEnabled()) {
            rewardManager.distributeAllRewards(instance.getConfig(),
                damageResult, tankResult, supportResult);
        }

        // Clean up boss instance
        instance.onDeath();

        // Save state (remove dead boss)
        plugin.getBossStateManager().remove(instance.getConfig().getId());
    }
}
