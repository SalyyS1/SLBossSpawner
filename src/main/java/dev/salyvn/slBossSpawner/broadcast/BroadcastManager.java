package dev.salyvn.slBossSpawner.broadcast;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.boss.BossConfig;
import dev.salyvn.slBossSpawner.utils.ColorUtils;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;

/**
 * Sends chat, title, and bossbar broadcasts to all online players.
 */
public class BroadcastManager {
    private final SLBossSpawner plugin;

    public BroadcastManager(SLBossSpawner plugin) {
        this.plugin = plugin;
    }

    /**
     * Broadcast boss death to all players.
     */
    public void broadcastDeath(BossConfig config, String killerName) {
        var mm = plugin.getMessageManager();
        Map<String, String> placeholders = Map.of(
            "boss_name", config.getMythicMobId(),
            "killer_name", killerName != null ? killerName : ""
        );

        if (config.isDeathChat()) {
            String key = killerName != null ? "boss-death-chat" : "boss-death-no-killer";
            String msg = mm.getRawMessage(key, new java.util.HashMap<>(placeholders));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(msg);
            }
        }

        if (config.isDeathTitle()) {
            String titleKey = "boss-death-title";
            String subtitleKey = killerName != null ? "boss-death-subtitle" : "boss-death-no-killer-subtitle";
            Component titleComp = ColorUtils.toComponent(
                mm.getRawMessage(titleKey, new java.util.HashMap<>(placeholders)));
            Component subtitleComp = ColorUtils.toComponent(
                mm.getRawMessage(subtitleKey, new java.util.HashMap<>(placeholders)));

            Title title = Title.title(titleComp, subtitleComp,
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500)));

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(title);
            }
        }
    }

    /**
     * Broadcast spawn warning via chat and/or title.
     */
    public void broadcastSpawnWarning(BossConfig config, int minutesRemaining) {
        var mm = plugin.getMessageManager();
        Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("boss_name", config.getMythicMobId());
        placeholders.put("time_remaining", minutesRemaining + mm.getRawMessage("time-unit-minutes"));

        if (config.isSpawnWarningChat()) {
            String msg = mm.getRawMessage("spawn-warning-chat", placeholders);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(msg);
            }
        }

        if (config.isSpawnWarningTitle()) {
            Component titleComp = ColorUtils.toComponent(
                mm.getRawMessage("spawn-warning-title", placeholders));
            Component subtitleComp = ColorUtils.toComponent(
                mm.getRawMessage("spawn-warning-subtitle", placeholders));

            Title title = Title.title(titleComp, subtitleComp,
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(300)));

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(title);
            }
        }
    }

    /**
     * Create a BossBar for spawn countdown (last 60 seconds).
     */
    public BossBar createSpawnBossBar(BossConfig config) {
        var mm = plugin.getMessageManager();
        Map<String, String> placeholders = Map.of("boss_name", config.getMythicMobId());
        String text = mm.getRawMessage("spawn-warning-bossbar", new java.util.HashMap<>(placeholders));

        return BossBar.bossBar(
            ColorUtils.toComponent(text),
            1.0f,
            BossBar.Color.RED,
            BossBar.Overlay.PROGRESS
        );
    }

    /**
     * Broadcast ranking leaderboard after boss death (legacy, uses damage ranking keys).
     */
    public void broadcastRanking(BossConfig config, java.util.List<String> rankingLines) {
        broadcastCategoryRanking(config, "ranking-header", "ranking-footer", rankingLines);
    }

    /**
     * Broadcast a category-specific ranking leaderboard.
     * @param headerKey message key for the ranking header
     * @param footerKey message key for the ranking footer
     * @param rankingLines pre-formatted ranking entry lines
     */
    public void broadcastCategoryRanking(BossConfig config, String headerKey, String footerKey,
                                          java.util.List<String> rankingLines) {
        if (rankingLines.isEmpty()) return;

        var mm = plugin.getMessageManager();
        Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("boss_name", config.getMythicMobId());

        String header = mm.getRawMessage(headerKey, placeholders);
        String footer = mm.getRawMessage(footerKey, placeholders);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(header);
            for (String line : rankingLines) {
                p.sendMessage(line);
            }
            p.sendMessage(footer);
        }
    }
}
