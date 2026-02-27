package dev.salyvn.slBossSpawner.reward;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.boss.BossConfig;
import dev.salyvn.slBossSpawner.boss.BossConfig.ItemRewardConfig;
import dev.salyvn.slBossSpawner.boss.BossConfig.RewardTierConfig;
import dev.salyvn.slBossSpawner.listener.DamageResult;
import dev.salyvn.slBossSpawner.listener.SupportResult;
import dev.salyvn.slBossSpawner.listener.TankResult;
import dev.salyvn.slBossSpawner.utils.CommandValidator;
import dev.salyvn.slBossSpawner.utils.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Distributes rewards per category (damage, tank, support) when a boss dies.
 *
 * H8  FIX: Three nearly-identical distribute* methods collapsed into one generic
 *          distributeCategory() to eliminate duplication.
 * H9  FIX: Item creation delegated to ItemFactory (was inline in three places).
 * H13 FIX: Command validation uses CommandValidator.isValid() (was inline regex).
 * H14 FIX: Drop tables are queued for offline players instead of silently skipped.
 */
public class RewardManager {
    private final SLBossSpawner plugin;
    private final ItemProtectionListener itemProtection;

    public RewardManager(SLBossSpawner plugin, ItemProtectionListener itemProtection) {
        this.plugin = plugin;
        this.itemProtection = itemProtection;
    }

    public void distributeAllRewards(BossConfig config, DamageResult damageResult,
            TankResult tankResult, SupportResult supportResult) {

        if (config.isDamageRewardsEnabled() && !damageResult.rankings().isEmpty()) {
            distributeCategory(config, config.getDamageRewardTiers(), damageResult.rankings(),
                    damageResult.totalDamage(), "ranking-header", "ranking-footer",
                    "ranking-entry", entry -> {
                        Map<String, String> ph = new HashMap<>();
                        ph.put("rank", String.valueOf(damageResult.rankings().indexOf(entry) + 1));
                        ph.put("player_name", ((DamageResult.PlayerDamageEntry) entry).playerName());
                        ph.put("damage", String.format("%.1f", ((DamageResult.PlayerDamageEntry) entry).damage()));
                        ph.put("percent", String.format("%.1f",
                                damageResult.totalDamage() > 0
                                        ? ((DamageResult.PlayerDamageEntry) entry).damage() / damageResult.totalDamage() * 100
                                        : 0));
                        return ph;
                    },
                    entry -> ((DamageResult.PlayerDamageEntry) entry).playerUuid(),
                    entry -> ((DamageResult.PlayerDamageEntry) entry).playerName(),
                    (tier, entry) -> {
                        // Apply last-hit multiplier for damage category only
                        double mult = tier.multiplier() * config.getGlobalMultiplier();
                        DamageResult.PlayerDamageEntry de = (DamageResult.PlayerDamageEntry) entry;
                        if (de.playerUuid().equals(damageResult.lastHitPlayer())) {
                            mult *= config.getLastHitMultiplier();
                        }
                        return mult;
                    });
        }

        if (config.isTankRewardsEnabled() && !tankResult.rankings().isEmpty()) {
            distributeCategory(config, config.getTankRewardTiers(), tankResult.rankings(),
                    tankResult.totalDamageTaken(), "tank-ranking-header", "tank-ranking-footer",
                    "tank-ranking-entry", entry -> {
                        Map<String, String> ph = new HashMap<>();
                        ph.put("rank", String.valueOf(tankResult.rankings().indexOf(entry) + 1));
                        ph.put("player_name", ((TankResult.PlayerTankEntry) entry).playerName());
                        ph.put("damage_taken", String.format("%.1f", ((TankResult.PlayerTankEntry) entry).damageTaken()));
                        ph.put("percent", String.format("%.1f",
                                tankResult.totalDamageTaken() > 0
                                        ? ((TankResult.PlayerTankEntry) entry).damageTaken() / tankResult.totalDamageTaken() * 100
                                        : 0));
                        return ph;
                    },
                    entry -> ((TankResult.PlayerTankEntry) entry).playerUuid(),
                    entry -> ((TankResult.PlayerTankEntry) entry).playerName(),
                    (tier, entry) -> tier.multiplier() * config.getGlobalMultiplier());
        }

        if (config.isSupportRewardsEnabled() && !supportResult.rankings().isEmpty()) {
            distributeCategory(config, config.getSupportRewardTiers(), supportResult.rankings(),
                    supportResult.totalSupportScore(), "support-ranking-header", "support-ranking-footer",
                    "support-ranking-entry", entry -> {
                        Map<String, String> ph = new HashMap<>();
                        ph.put("rank", String.valueOf(supportResult.rankings().indexOf(entry) + 1));
                        ph.put("player_name", ((SupportResult.PlayerSupportEntry) entry).playerName());
                        ph.put("score", String.format("%.1f", ((SupportResult.PlayerSupportEntry) entry).supportScore()));
                        ph.put("percent", String.format("%.1f",
                                supportResult.totalSupportScore() > 0
                                        ? ((SupportResult.PlayerSupportEntry) entry).supportScore() / supportResult.totalSupportScore() * 100
                                        : 0));
                        return ph;
                    },
                    entry -> ((SupportResult.PlayerSupportEntry) entry).playerUuid(),
                    entry -> ((SupportResult.PlayerSupportEntry) entry).playerName(),
                    (tier, entry) -> tier.multiplier() * config.getGlobalMultiplier());
        }
    }

    /**
     * H8: Generic category distributor — replaces three nearly-identical methods.
     */
    @SuppressWarnings("unchecked")
    private <E> void distributeCategory(
            BossConfig config,
            List<RewardTierConfig> tiers,
            List<E> rankings,
            double totalValue,
            String headerKey, String footerKey, String entryKey,
            java.util.function.Function<E, Map<String, String>> placeholderBuilder,
            java.util.function.Function<E, UUID> uuidExtractor,
            java.util.function.Function<E, String> nameExtractor,
            java.util.function.BiFunction<RewardTierConfig, E, Double> multiplierResolver) {

        if (tiers.isEmpty()) return;

        var mm = plugin.getMessageManager();

        // Build ranking broadcast lines (top 10)
        List<String> rankingLines = new ArrayList<>();
        for (int i = 0; i < rankings.size() && i < 10; i++) {
            rankingLines.add(mm.getRawMessage(entryKey, placeholderBuilder.apply(rankings.get(i))));
        }
        plugin.getBroadcastManager().broadcastCategoryRanking(config, headerKey, footerKey, rankingLines);

        // Distribute per-tier
        for (RewardTierConfig tier : tiers) {
            int rankIndex = tier.rank() - 1;
            if (rankIndex >= rankings.size()) continue;

            E entry = rankings.get(rankIndex);
            double effectiveMult = multiplierResolver.apply(tier, entry);
            distributeRewardsToPlayer(config, tier, uuidExtractor.apply(entry),
                    nameExtractor.apply(entry), effectiveMult);
        }
    }

    /**
     * Distribute a single tier's rewards to one player.
     * Shared across all categories.
     */
    private void distributeRewardsToPlayer(BossConfig config, RewardTierConfig tier,
            UUID playerUuid, String playerName, double effectiveMult) {

        Player player = Bukkit.getPlayer(playerUuid);

        // H13 FIX: validate name with CommandValidator
        String safeName = playerName.replaceAll("[^a-zA-Z0-9_]", "");
        if (safeName.isEmpty()) return;

        // Commands
        for (String cmd : tier.commands()) {
            // H13 FIX: use CommandValidator instead of inline regex
            if (!CommandValidator.isValid(cmd)) {
                plugin.getLogger().warning("Blocked dangerous reward command: " + cmd);
                continue;
            }
            String resolved = cmd.replace("{player}", safeName);
            if (player != null) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
            } else {
                plugin.getPendingRewardManager().queueCommand(playerUuid, resolved);
            }
        }

        // Drop tables
        for (String dropTable : tier.dropTables()) {
            String safeTable = dropTable.replaceAll("[^a-zA-Z0-9_\\-.]", "");
            if (safeTable.isEmpty()) {
                plugin.getLogger().warning("Invalid drop table name after sanitization: " + dropTable);
                continue;
            }
            if (player != null) {
                try {
                    String cmd = "mm items droptable " + safeTable + " 1 " + safeName;
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    plugin.debug("Executed drop table '" + dropTable + "' for " + safeName);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to give drop table '" + dropTable
                            + "': " + e.getMessage());
                }
            } else {
                // H14 FIX: queue drop tables for offline players
                String cmd = "mm items droptable " + safeTable + " 1 {player}";
                plugin.getPendingRewardManager().queueCommand(playerUuid,
                        cmd.replace("{player}", safeName));
                plugin.debug("Queued drop table '" + dropTable + "' for offline player " + safeName);
            }
        }

        // Items — H9 FIX: delegate to ItemFactory
        for (ItemRewardConfig itemConfig : tier.items()) {
            int amount = (int) Math.max(1, itemConfig.amount() * effectiveMult);
            ItemStack stack = createItem(itemConfig, amount);
            if (stack == null) continue;

            if (player != null) {
                giveOrDrop(player, stack, config);
            } else {
                queueItemPending(playerUuid, itemConfig, amount);
            }
        }
    }

    /** H9: Delegate item creation to ItemFactory. */
    private ItemStack createItem(ItemRewardConfig itemConfig, int amount) {
        return switch (itemConfig.type().toLowerCase()) {
            case "vanilla" -> ItemFactory.createVanillaItem(itemConfig, amount, plugin.getLogger());
            case "mmoitems" -> ItemFactory.createMMOItem(itemConfig, amount, plugin.getLogger());
            case "mythicmobs" -> ItemFactory.createMythicMobsItem(itemConfig, amount, plugin.getLogger());
            default -> {
                plugin.getLogger().warning("Unknown item type: " + itemConfig.type());
                yield null;
            }
        };
    }

    private void queueItemPending(UUID playerUuid, ItemRewardConfig itemConfig, int amount) {
        switch (itemConfig.type().toLowerCase()) {
            case "mmoitems" ->
                plugin.getPendingRewardManager().queueItem(
                        playerUuid, itemConfig.material(), null, amount, itemConfig.id());
            case "mythicmobs" ->
                plugin.getPendingRewardManager().queueItem(
                        playerUuid, "mythicmobs:" + itemConfig.material(), null, amount, null);
            default ->
                plugin.getPendingRewardManager().queueItem(
                        playerUuid, itemConfig.material(), itemConfig.displayName(), amount, null);
        }
    }

    private void giveOrDrop(Player player, ItemStack stack, BossConfig config) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack item : leftover.values()) {
            var dropped = player.getWorld().dropItemNaturally(player.getLocation(), item);
            if (config.isItemProtectionEnabled()) {
                itemProtection.tagItem(dropped, player.getUniqueId(),
                        config.getItemProtectionDuration(),
                        config.getItemProtectionExpireAction());
            }
        }
    }
}
