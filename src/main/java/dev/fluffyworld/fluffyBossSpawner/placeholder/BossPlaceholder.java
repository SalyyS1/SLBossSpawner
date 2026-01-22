package dev.fluffyworld.fluffyBossSpawner.placeholder;

import dev.fluffyworld.fluffyBossSpawner.FluffyBossSpawner;
import dev.fluffyworld.fluffyBossSpawner.boss.BossInstance;
import dev.fluffyworld.fluffyBossSpawner.boss.BossScheduler;
import dev.fluffyworld.fluffyBossSpawner.utils.TimeUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class BossPlaceholder extends PlaceholderExpansion {
    private final FluffyBossSpawner plugin;
    
    public BossPlaceholder(FluffyBossSpawner plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public @NotNull String getIdentifier() {
        return "fbs";
    }
    
    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }
    
    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true;
    }
    
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        BossScheduler scheduler = plugin.getBossScheduler();
        
        String[] parts = params.split("_", 2);
        if (parts.length < 2) return null;
        
        String bossId = parts[0];
        String placeholder = parts[1];
        
        BossInstance instance = scheduler.getBossInstance(bossId);
        if (instance == null) {
            if (params.equalsIgnoreCase("count")) {
                return String.valueOf(scheduler.getAllBossInstances().size());
            }
            return null;
        }
        
        switch (placeholder.toLowerCase()) {
            case "current_boss", "current" -> {
                return instance.isAlive() ? instance.getConfig().getMythicMobId() : "None";
            }
            case "next" -> {
                long seconds = instance.getSecondsUntilNext();
                if (seconds < 0) return "N/A";
                return TimeUtils.formatDuration(seconds);
            }
            case "next_formatted", "nextformatted" -> {
                return instance.getNextSpawnFormatted();
            }
            case "expired", "expire" -> {
                long seconds = instance.getSecondsUntilExpire();
                if (seconds < 0) return "N/A";
                return TimeUtils.formatDuration(seconds);
            }
            case "status" -> {
                return instance.isAlive() ? "Alive" : "Dead";
            }
            case "mythicmob", "mob" -> {
                return instance.getConfig().getMythicMobId();
            }
        }
        
        return null;
    }
}
