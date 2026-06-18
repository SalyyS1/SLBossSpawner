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
 * Listens for MythicMobDeathEvent, builds fight results, and distributes rewards.
 *
 * H3 FIX: Returns immediately if plugin.isReloading() — prevents spurious reward
 * distribution for bosses despawned by the reload sequence.
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
        // H3 FIX: ignore deaths triggered by reload despawn
        if (plugin.isReloading()) return;

        Entity entity = event.getEntity();
        UUID entityUuid = entity.getUniqueId();

        BossScheduler scheduler = plugin.getBossScheduler();
        BossInstance instance = scheduler.findByEntityUuid(entityUuid);
        if (instance == null) return;

        plugin.debug("MythicMobDeathEvent fired for boss: " + instance.getConfig().getId());

        // Capture results before cleanup clears tracker state
        DamageResult damageResult = damageTracker.buildResult(entityUuid);
        TankResult tankResult = tankTracker.buildResult(entityUuid);
        SupportResult supportResult = supportTracker.buildResult(entityUuid);

        Player killer = (event.getKiller() instanceof Player p) ? p : null;
        String killerName = killer != null ? killer.getName() : null;

        // Pass null → BroadcastManager uses the configured display name
        // (falls back to mob ID when no name is set).
        plugin.getBroadcastManager().broadcastDeath(instance.getConfig(), killerName, null);

        if (instance.getConfig().isRewardsEnabled()) {
            rewardManager.distributeAllRewards(instance.getConfig(),
                    damageResult, tankResult, supportResult);
        }

        // Record statistics
        if (plugin.getStatisticsManager() != null && plugin.getConfigManager().isStatisticsEnabled()) {
            plugin.getStatisticsManager().recordBossDeath(
                    instance.getConfig().getId(), damageResult, tankResult, supportResult,
                    killer != null ? killer.getUniqueId() : null);
        }

        // cleanup() handles tracker stop, entity index deregister, and state removal
        instance.onDeath();
    }
}
