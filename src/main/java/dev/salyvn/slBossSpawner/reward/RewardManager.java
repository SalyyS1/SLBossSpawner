package dev.salyvn.slBossSpawner.reward;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.boss.BossConfig;
import dev.salyvn.slBossSpawner.boss.BossConfig.ItemRewardConfig;
import dev.salyvn.slBossSpawner.boss.BossConfig.RewardTierConfig;
import dev.salyvn.slBossSpawner.listener.DamageResult;
import dev.salyvn.slBossSpawner.listener.DamageResult.PlayerDamageEntry;
import dev.salyvn.slBossSpawner.listener.SupportResult;
import dev.salyvn.slBossSpawner.listener.TankResult;
import dev.salyvn.slBossSpawner.utils.ColorUtils;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Distributes rewards per category (damage, tank, support) when a boss dies.
 * Handles commands, drop tables, vanilla items, MMOItems, MythicMobs items, and
 * offline queuing.
 */
public class RewardManager {
    private final SLBossSpawner plugin;
    private final ItemProtectionListener itemProtection;

    public RewardManager(SLBossSpawner plugin, ItemProtectionListener itemProtection) {
        this.plugin = plugin;
        this.itemProtection = itemProtection;
    }

    /**
     * Main entry: distribute all category rewards.
     */
    public void distributeAllRewards(BossConfig config, DamageResult damageResult,
            TankResult tankResult, SupportResult supportResult) {
        // Damage category
        if (config.isDamageRewardsEnabled() && !damageResult.rankings().isEmpty()) {
            distributeDamageRewards(config, damageResult);
        }

        // Tank category
        if (config.isTankRewardsEnabled() && !tankResult.rankings().isEmpty()) {
            distributeTankRewards(config, tankResult);
        }

        // Support category
        if (config.isSupportRewardsEnabled() && !supportResult.rankings().isEmpty()) {
            distributeSupportRewards(config, supportResult);
        }
    }

    private void distributeDamageRewards(BossConfig config, DamageResult result) {
        List<RewardTierConfig> tiers = config.getDamageRewardTiers();
        if (tiers.isEmpty())
            return;

        double globalMult = config.getGlobalMultiplier();
        List<PlayerDamageEntry> rankings = result.rankings();
        var mm = plugin.getMessageManager();

        // Build ranking broadcast lines
        List<String> rankingLines = new ArrayList<>();
        for (int i = 0; i < rankings.size() && i < 10; i++) {
            PlayerDamageEntry entry = rankings.get(i);
            double percent = result.totalDamage() > 0
                    ? (entry.damage() / result.totalDamage()) * 100
                    : 0;

            Map<String, String> ph = new HashMap<>();
            ph.put("rank", String.valueOf(i + 1));
            ph.put("player_name", entry.playerName());
            ph.put("damage", String.format("%.1f", entry.damage()));
            ph.put("percent", String.format("%.1f", percent));
            rankingLines.add(mm.getRawMessage("ranking-entry", ph));
        }

        plugin.getBroadcastManager().broadcastCategoryRanking(config,
                "ranking-header", "ranking-footer", rankingLines);

        // Distribute per-tier rewards
        for (RewardTierConfig tier : tiers) {
            int rankIndex = tier.rank() - 1;
            if (rankIndex >= rankings.size())
                continue;

            PlayerDamageEntry entry = rankings.get(rankIndex);
            double tierMult = tier.multiplier() * globalMult;

            // Apply last-hit bonus for damage category
            double effectiveMult = tierMult;
            if (entry.playerUuid().equals(result.lastHitPlayer())) {
                effectiveMult *= config.getLastHitMultiplier();
            }

            distributeRewardsToPlayer(config, tier, entry.playerUuid(), entry.playerName(), effectiveMult);
        }
    }

    private void distributeTankRewards(BossConfig config, TankResult result) {
        List<RewardTierConfig> tiers = config.getTankRewardTiers();
        if (tiers.isEmpty())
            return;

        double globalMult = config.getGlobalMultiplier();
        List<TankResult.PlayerTankEntry> rankings = result.rankings();
        var mm = plugin.getMessageManager();

        // Build ranking broadcast lines
        List<String> rankingLines = new ArrayList<>();
        for (int i = 0; i < rankings.size() && i < 10; i++) {
            TankResult.PlayerTankEntry entry = rankings.get(i);
            double percent = result.totalDamageTaken() > 0
                    ? (entry.damageTaken() / result.totalDamageTaken()) * 100
                    : 0;

            Map<String, String> ph = new HashMap<>();
            ph.put("rank", String.valueOf(i + 1));
            ph.put("player_name", entry.playerName());
            ph.put("damage_taken", String.format("%.1f", entry.damageTaken()));
            ph.put("percent", String.format("%.1f", percent));
            rankingLines.add(mm.getRawMessage("tank-ranking-entry", ph));
        }

        plugin.getBroadcastManager().broadcastCategoryRanking(config,
                "tank-ranking-header", "tank-ranking-footer", rankingLines);

        // Distribute per-tier rewards
        for (RewardTierConfig tier : tiers) {
            int rankIndex = tier.rank() - 1;
            if (rankIndex >= rankings.size())
                continue;

            TankResult.PlayerTankEntry entry = rankings.get(rankIndex);
            double effectiveMult = tier.multiplier() * globalMult;

            distributeRewardsToPlayer(config, tier, entry.playerUuid(), entry.playerName(), effectiveMult);
        }
    }

    private void distributeSupportRewards(BossConfig config, SupportResult result) {
        List<RewardTierConfig> tiers = config.getSupportRewardTiers();
        if (tiers.isEmpty())
            return;

        double globalMult = config.getGlobalMultiplier();
        List<SupportResult.PlayerSupportEntry> rankings = result.rankings();
        var mm = plugin.getMessageManager();

        // Build ranking broadcast lines
        List<String> rankingLines = new ArrayList<>();
        for (int i = 0; i < rankings.size() && i < 10; i++) {
            SupportResult.PlayerSupportEntry entry = rankings.get(i);
            double percent = result.totalSupportScore() > 0
                    ? (entry.supportScore() / result.totalSupportScore()) * 100
                    : 0;

            Map<String, String> ph = new HashMap<>();
            ph.put("rank", String.valueOf(i + 1));
            ph.put("player_name", entry.playerName());
            ph.put("score", String.format("%.1f", entry.supportScore()));
            ph.put("percent", String.format("%.1f", percent));
            rankingLines.add(mm.getRawMessage("support-ranking-entry", ph));
        }

        plugin.getBroadcastManager().broadcastCategoryRanking(config,
                "support-ranking-header", "support-ranking-footer", rankingLines);

        // Distribute per-tier rewards
        for (RewardTierConfig tier : tiers) {
            int rankIndex = tier.rank() - 1;
            if (rankIndex >= rankings.size())
                continue;

            SupportResult.PlayerSupportEntry entry = rankings.get(rankIndex);
            double effectiveMult = tier.multiplier() * globalMult;

            distributeRewardsToPlayer(config, tier, entry.playerUuid(), entry.playerName(), effectiveMult);
        }
    }

    /**
     * Distribute a single tier's rewards to a specific player (shared across all
     * categories).
     */
    private void distributeRewardsToPlayer(BossConfig config, RewardTierConfig tier,
            UUID playerUuid, String playerName, double effectiveMult) {
        Player player = Bukkit.getPlayer(playerUuid);

        // Commands - validate player name to prevent command injection
        String safeName = playerName.replaceAll("[^a-zA-Z0-9_]", "");
        if (safeName.isEmpty())
            return;

        for (String cmd : tier.commands()) {
            String resolved = cmd.replace("{player}", safeName);
            if (player != null) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
            } else {
                plugin.getPendingRewardManager().queueCommand(playerUuid, resolved);
            }
        }

        // Drop tables — execute via MythicMobs droptable command
        for (String dropTable : tier.dropTables()) {
            if (player != null) {
                try {
                    String safeTable = dropTable.replaceAll("[^a-zA-Z0-9_\\-.]", "");
                    if (safeTable.isEmpty()) {
                        plugin.getLogger().warning("Invalid drop table name after sanitization: " + dropTable);
                        continue;
                    }
                    String cmd = "mm items droptable " + safeTable + " 1 " + safeName;
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    plugin.debug("Executed drop table '" + dropTable + "' for " + safeName);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to give drop table '" + dropTable + "': " + e.getMessage());
                }
            }
        }

        // Items
        for (ItemRewardConfig itemConfig : tier.items()) {
            int amount = (int) Math.max(1, itemConfig.amount() * effectiveMult);

            switch (itemConfig.type().toLowerCase()) {
                case "vanilla" -> giveVanillaItem(player, playerUuid, itemConfig, amount, config);
                case "mmoitems" -> giveMMOItem(player, playerUuid, itemConfig, amount, config);
                case "mythicmobs" -> giveMythicMobsItem(player, playerUuid, itemConfig, amount, config);
                default -> plugin.getLogger().warning("Unknown item type: " + itemConfig.type());
            }
        }
    }

    private void giveVanillaItem(Player player, UUID playerUuid,
            ItemRewardConfig itemConfig, int amount, BossConfig config) {
        Material mat;
        try {
            mat = Material.valueOf(itemConfig.material().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material '" + itemConfig.material() + "' in reward config");
            return;
        }

        ItemStack stack = new ItemStack(mat, amount);
        if (itemConfig.displayName() != null && !itemConfig.displayName().isEmpty()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtils.toComponent(itemConfig.displayName()));
                stack.setItemMeta(meta);
            }
        }

        if (player != null) {
            giveOrDrop(player, stack, config);
        } else {
            plugin.getPendingRewardManager().queueItem(
                    playerUuid, itemConfig.material(), itemConfig.displayName(), amount, null);
        }
    }

    private void giveMMOItem(Player player, UUID playerUuid,
            ItemRewardConfig itemConfig, int amount, BossConfig config) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            plugin.debug("MMOItems not installed, skipping MMOItems reward");
            return;
        }

        try {
            var mmoItems = net.Indyuce.mmoitems.MMOItems.plugin;
            var type = mmoItems.getTypes().get(itemConfig.material());
            if (type == null) {
                plugin.getLogger().warning("MMOItems type '" + itemConfig.material() + "' not found");
                return;
            }

            ItemStack stack = mmoItems.getItem(type, itemConfig.id());
            if (stack == null) {
                plugin.getLogger().warning("MMOItems item '" + itemConfig.id() + "' not found");
                return;
            }

            stack.setAmount(amount);

            if (player != null) {
                giveOrDrop(player, stack, config);
            } else {
                plugin.getPendingRewardManager().queueItem(
                        playerUuid, itemConfig.material(), null, amount, itemConfig.id());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create MMOItems reward: " + e.getMessage());
        }
    }

    private void giveMythicMobsItem(Player player, UUID playerUuid,
            ItemRewardConfig itemConfig, int amount, BossConfig config) {
        try {
            var itemManager = MythicBukkit.inst().getItemManager();
            var mythicItem = itemManager.getItem(itemConfig.material());
            if (mythicItem.isEmpty()) {
                plugin.getLogger().warning("MythicMobs item '" + itemConfig.material() + "' not found");
                return;
            }

            ItemStack stack = MythicBukkit.inst().getItemManager()
                    .getItemStack(itemConfig.material());
            if (stack == null) {
                plugin.getLogger().warning("MythicMobs item '" + itemConfig.material() + "' produced null ItemStack");
                return;
            }

            stack.setAmount(amount);

            if (player != null) {
                giveOrDrop(player, stack, config);
            } else {
                plugin.getPendingRewardManager().queueItem(
                        playerUuid, "mythicmobs:" + itemConfig.material(), null, amount, null);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create MythicMobs reward: " + e.getMessage());
        }
    }

    /**
     * Give item to player inventory or drop at location with pickup protection.
     */
    private void giveOrDrop(Player player, ItemStack stack, BossConfig config) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            for (ItemStack item : leftover.values()) {
                var dropped = player.getWorld().dropItemNaturally(player.getLocation(), item);
                if (config.isItemProtectionEnabled()) {
                    itemProtection.tagItem(dropped, player.getUniqueId(), config.getItemProtectionDuration(),
                            config.getItemProtectionExpireAction());
                }
            }
        }
    }
}
