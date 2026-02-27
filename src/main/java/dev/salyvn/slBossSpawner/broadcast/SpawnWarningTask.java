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
 *
 * H12 FIX: Replaced the per-second tick loop with targeted delayed tasks at
 * each configured interval, eliminating 3600 redundant ticks per warning period.
 * M13 FIX: BossBar progress now starts at 1.0f and reaches 0.0f exactly at spawn
 * (was off-by-one: progress(secondsRemaining/60) started < 1.0f on first tick).
 */
public class SpawnWarningTask {
    private final SLBossSpawner plugin;
    private final BroadcastManager broadcastManager;
    private final BossConfig config;

    private BukkitTask bossBarTask;
    private BossBar bossBar;
    private boolean running = false;

    public SpawnWarningTask(SLBossSpawner plugin, BroadcastManager broadcastManager,
                            BossConfig config) {
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
        running = true;

        // H12: schedule one delayed task per configured interval instead of ticking every second
        List<Integer> intervals = config.getSpawnWarningIntervals();
        for (int minutes : intervals) {
            long triggerAtSeconds = (long) minutes * 60;
            long delayTicks = (totalSeconds - triggerAtSeconds) * 20L;
            if (delayTicks < 0) {
                // interval already passed — fire immediately
                broadcastManager.broadcastSpawnWarning(config, minutes);
                continue;
            }
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> broadcastManager.broadcastSpawnWarning(config, minutes),
                    delayTicks);
        }

        // BossBar in last 60 seconds — one task to show it, then a per-second updater
        if (config.isSpawnWarningBossbar() && totalSeconds > 0) {
            long bossBarStartDelay = Math.max(0L, (totalSeconds - 60L)) * 20L;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!running) return;
                bossBar = broadcastManager.createSpawnBossBar(config);
                // M13 FIX: initial progress = 1.0f (full bar), decrements to 0
                bossBar.progress(1.0f);
                for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bossBar);

                long displaySeconds = Math.min(totalSeconds, 60L);
                // Update progress every second for the display window
                bossBarTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                    long remaining = displaySeconds;

                    @Override
                    public void run() {
                        remaining--;
                        if (remaining <= 0 || !running) {
                            if (bossBar != null) {
                                bossBar.progress(0f);
                                for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bossBar);
                                bossBar = null;
                            }
                            if (bossBarTask != null) { bossBarTask.cancel(); bossBarTask = null; }
                            return;
                        }
                        // M13: remaining/displaySeconds gives correct 1→0 progression
                        bossBar.progress(Math.max(0f, (float) remaining / (float) displaySeconds));
                    }
                }, 20L, 20L);
            }, bossBarStartDelay);
        }
    }

    public void stop() {
        running = false;

        if (bossBarTask != null) {
            bossBarTask.cancel();
            bossBarTask = null;
        }

        if (bossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bossBar);
            bossBar = null;
        }
    }

    public boolean isRunning() {
        return running;
    }
}
