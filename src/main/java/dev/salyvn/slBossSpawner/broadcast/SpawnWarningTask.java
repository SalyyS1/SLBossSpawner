package dev.salyvn.slBossSpawner.broadcast;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.boss.BossConfig;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * Schedules countdown warnings before boss spawn.
 * Fires at configured minute intervals + BossBar in last 60 seconds.
 */
public class SpawnWarningTask {
    private final SLBossSpawner plugin;
    private final BroadcastManager broadcastManager;
    private final BossConfig config;

    private BukkitTask countdownTask;
    private BossBar bossBar;
    private long secondsRemaining;

    public SpawnWarningTask(SLBossSpawner plugin, BroadcastManager broadcastManager, BossConfig config) {
        this.plugin = plugin;
        this.broadcastManager = broadcastManager;
        this.config = config;
    }

    /**
     * Start the warning countdown.
     * @param totalSeconds seconds until boss spawns
     */
    public void start(long totalSeconds) {
        stop();
        this.secondsRemaining = totalSeconds;

        // Run every second to track countdown
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            secondsRemaining--;

            if (secondsRemaining <= 0) {
                stop();
                return;
            }

            // Check if current secondsRemaining matches a minute interval
            if (secondsRemaining % 60 == 0) {
                int minutesLeft = (int) (secondsRemaining / 60);
                List<Integer> intervals = config.getSpawnWarningIntervals();
                if (intervals.contains(minutesLeft)) {
                    broadcastManager.broadcastSpawnWarning(config, minutesLeft);
                }
            }

            // BossBar in last 60 seconds
            if (config.isSpawnWarningBossbar()) {
                if (secondsRemaining == 60) {
                    bossBar = broadcastManager.createSpawnBossBar(config);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showBossBar(bossBar);
                    }
                }

                if (bossBar != null && secondsRemaining <= 60 && secondsRemaining > 0) {
                    bossBar.progress(Math.max(0f, (float) secondsRemaining / 60f));
                }
            }
        }, 20L, 20L); // every second (20 ticks)
    }

    public void stop() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        if (bossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bossBar);
            }
            bossBar = null;
        }
    }

    public boolean isRunning() {
        return countdownTask != null;
    }
}
