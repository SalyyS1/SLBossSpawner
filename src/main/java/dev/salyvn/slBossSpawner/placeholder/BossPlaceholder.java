package dev.salyvn.slBossSpawner.placeholder;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.boss.BossInstance;
import dev.salyvn.slBossSpawner.boss.BossScheduler;
import dev.salyvn.slBossSpawner.utils.TimeUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class BossPlaceholder extends PlaceholderExpansion {
    private final SLBossSpawner plugin;

    public BossPlaceholder(SLBossSpawner plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "slbs";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SalyVn";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        BossScheduler scheduler = plugin.getBossScheduler();

        // Use dot separator to support boss IDs with underscores
        String[] parts = params.split("\\.", 2);
        if (parts.length < 2) {
            if (params.equalsIgnoreCase("count")) {
                return String.valueOf(scheduler.getAllBossInstances().size());
            }
            return null;
        }

        String bossId = parts[0];
        String placeholder = parts[1];

        BossInstance instance = scheduler.getBossInstance(bossId);
        if (instance == null)
            return null;

        return switch (placeholder.toLowerCase()) {
            case "current_boss", "current" ->
                instance.isAlive() ? instance.getConfig().getMythicMobId() : "None";
            case "next" -> {
                long seconds = instance.getSecondsUntilNext();
                yield seconds < 0 ? "N/A" : TimeUtils.formatDuration(seconds);
            }
            case "next_formatted", "nextformatted" ->
                instance.getNextSpawnFormatted();
            case "expired", "expire" -> {
                long seconds = instance.getSecondsUntilExpire();
                yield seconds < 0 ? "N/A" : TimeUtils.formatDuration(seconds);
            }
            case "status" ->
                instance.isAlive() ? "Alive" : "Dead";
            case "mythicmob", "mob" ->
                instance.getConfig().getMythicMobId();
            default -> null;
        };
    }
}
