package dev.salyvn.slBossSpawner.commands;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.config.MessageManager;
import dev.salyvn.slBossSpawner.database.DatabaseManager;
import dev.salyvn.slBossSpawner.database.LeaderboardRow;
import dev.salyvn.slBossSpawner.statistics.LeaderboardCache;
import dev.salyvn.slBossSpawner.statistics.PeriodType;
import dev.salyvn.slBossSpawner.statistics.SeasonResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles /slboss top <metric> [period] [boss] — shows leaderboard from DB.
 */
public class TopCommandHandler {
    private final SLBossSpawner plugin;
    private final MessageManager messageManager;

    private static final Map<String, PeriodType> PERIOD_ALIASES = new LinkedHashMap<>();
    static {
        PERIOD_ALIASES.put("alltime", PeriodType.ALL_TIME);
        PERIOD_ALIASES.put("daily", PeriodType.DAILY);
        PERIOD_ALIASES.put("weekly", PeriodType.WEEKLY);
        PERIOD_ALIASES.put("monthly", PeriodType.MONTHLY);
        PERIOD_ALIASES.put("quarterly", PeriodType.QUARTERLY);
        PERIOD_ALIASES.put("seasonal", PeriodType.SEASONAL);
        PERIOD_ALIASES.put("yearly", PeriodType.YEARLY);
    }

    public TopCommandHandler(SLBossSpawner plugin, MessageManager messageManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
    }

    public void handle(CommandSender sender, String[] args) {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) {
            sender.sendMessage(messageManager.getMessage("stats-disabled"));
            return;
        }

        // /slboss top <metric> [period] [boss]
        if (args.length < 2) {
            sender.sendMessage(messageManager.getMessage("top-usage"));
            return;
        }

        String metricArg = args[1].toLowerCase();
        String dbColumn = LeaderboardCache.metricToColumn(metricArg);
        if (dbColumn == null) {
            sender.sendMessage(messageManager.getMessage("top-invalid-metric",
                    Map.of("metric", args[1])));
            return;
        }

        // Resolve period (default: ALL_TIME)
        PeriodType periodType = PeriodType.ALL_TIME;
        String periodAlias = "alltime";
        if (args.length >= 3) {
            periodAlias = args[2].toLowerCase();
            PeriodType resolved = PERIOD_ALIASES.get(periodAlias);
            if (resolved == null) {
                sender.sendMessage(messageManager.getMessage("top-invalid-period",
                        Map.of("period", args[2])));
                return;
            }
            periodType = resolved;
        }

        // Resolve boss (default: __global__)
        String bossId = args.length >= 4 ? args[3] : "__global__";

        // Compute period key
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        SeasonResolver resolver = plugin.getStatisticsManager() != null
                ? plugin.getStatisticsManager().getSeasonResolver()
                : date -> date.getYear() + "-S" + ((date.getMonthValue() - 1) / 3 + 1);
        String periodKey = periodType.computeKey(today, resolver);

        String displayPeriod = periodAlias;
        String displayMetric = metricArg;
        PeriodType finalPeriodType = periodType;

        // Async DB query
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<LeaderboardRow> rows = db.getTopPlayers(
                        finalPeriodType.name(), periodKey, bossId, dbColumn, 10);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (rows == null || rows.isEmpty()) {
                        sender.sendMessage(messageManager.getMessage("top-no-data"));
                        return;
                    }
                    sendTopDisplay(sender, displayMetric, displayPeriod, rows);
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to query leaderboard: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(messageManager.getMessage("top-no-data")));
            }
        });
    }

    private void sendTopDisplay(CommandSender sender, String metric, String period, List<LeaderboardRow> rows) {
        sender.sendMessage(messageManager.getMessage("top-header",
                Map.of("metric", metric, "period", period)));

        for (LeaderboardRow row : rows) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("rank", String.valueOf(row.rank()));
            placeholders.put("player_name", row.playerName());
            placeholders.put("value", formatDouble(row.value()));
            sender.sendMessage(messageManager.getMessage("top-entry", placeholders));
        }

        sender.sendMessage(messageManager.getMessage("top-footer"));
    }

    /**
     * Tab completion for /slboss top args.
     */
    public List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            String input = args[1].toLowerCase();
            return Arrays.stream(LeaderboardCache.METRICS)
                    .filter(m -> m.startsWith(input))
                    .collect(Collectors.toList());
        }
        if (args.length == 3) {
            String input = args[2].toLowerCase();
            return PERIOD_ALIASES.keySet().stream()
                    .filter(p -> p.startsWith(input))
                    .collect(Collectors.toList());
        }
        if (args.length == 4) {
            String input = args[3].toLowerCase();
            List<String> bossIds = new ArrayList<>(plugin.getBossScheduler().getAllBossInstances().keySet());
            return bossIds.stream()
                    .filter(id -> id.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private String formatDouble(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format("%.1f", value);
    }
}
