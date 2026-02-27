package dev.salyvn.slBossSpawner.utils;

import dev.salyvn.slBossSpawner.boss.BossConfig.ItemRewardConfig;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.logging.Logger;

/**
 * Static factory for creating reward item stacks from various sources:
 * vanilla Bukkit, MMOItems, and MythicMobs.
 */
public final class ItemFactory {
    private ItemFactory() {}

    public static ItemStack createVanillaItem(ItemRewardConfig config, int amount, Logger logger) {
        Material mat;
        try {
            mat = Material.valueOf(config.material().toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid material '" + config.material() + "' in reward config");
            return null;
        }
        ItemStack stack = new ItemStack(mat, amount);
        if (config.displayName() != null && !config.displayName().isEmpty()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtils.toComponent(config.displayName()));
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    public static ItemStack createMMOItem(ItemRewardConfig config, int amount, Logger logger) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) return null;
        try {
            var mmoItems = net.Indyuce.mmoitems.MMOItems.plugin;
            var type = mmoItems.getTypes().get(config.material());
            if (type == null) {
                logger.warning("MMOItems type '" + config.material() + "' not found");
                return null;
            }
            ItemStack stack = mmoItems.getItem(type, config.id());
            if (stack == null) {
                logger.warning("MMOItems item '" + config.id() + "' not found");
                return null;
            }
            stack.setAmount(amount);
            return stack;
        } catch (Exception e) {
            logger.warning("Failed to create MMOItems reward: " + e.getMessage());
            return null;
        }
    }

    public static ItemStack createMythicMobsItem(ItemRewardConfig config, int amount, Logger logger) {
        try {
            var itemManager = MythicBukkit.inst().getItemManager();
            var mythicItem = itemManager.getItem(config.material());
            if (mythicItem.isEmpty()) {
                logger.warning("MythicMobs item '" + config.material() + "' not found");
                return null;
            }
            ItemStack stack = MythicBukkit.inst().getItemManager().getItemStack(config.material());
            if (stack == null) {
                logger.warning("MythicMobs item '" + config.material() + "' produced null ItemStack");
                return null;
            }
            stack.setAmount(amount);
            return stack;
        } catch (Exception e) {
            logger.warning("Failed to create MythicMobs reward: " + e.getMessage());
            return null;
        }
    }

    /**
     * Re-creates an item from stored pending-reward fields.
     * Tries MMOItems → MythicMobs (mythicmobs: prefix) → vanilla in order.
     */
    public static ItemStack createFromPending(String material, String displayName,
            int amount, String mmoitemsId, Logger logger) {
        // MMOItems
        if (mmoitemsId != null && Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            try {
                var mmoItems = net.Indyuce.mmoitems.MMOItems.plugin;
                var type = mmoItems.getTypes().get(material);
                if (type != null) {
                    ItemStack stack = mmoItems.getItem(type, mmoitemsId);
                    if (stack != null) {
                        stack.setAmount(amount);
                        return stack;
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to create MMOItems pending reward: " + e.getMessage());
            }
        }

        // MythicMobs (stored with "mythicmobs:" prefix)
        if (material != null && material.startsWith("mythicmobs:")) {
            String mythicId = material.substring("mythicmobs:".length());
            if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
                try {
                    ItemStack stack = MythicBukkit.inst().getItemManager().getItemStack(mythicId);
                    if (stack != null) {
                        stack.setAmount(amount);
                        return stack;
                    }
                    logger.warning("MythicMobs item '" + mythicId + "' not found for pending reward");
                } catch (Exception e) {
                    logger.warning("Failed to create MythicMobs pending reward: " + e.getMessage());
                }
            }
            return null; // Don't fall through to vanilla
        }

        // Vanilla
        if (material != null) {
            try {
                Material mat = Material.valueOf(material.toUpperCase());
                ItemStack stack = new ItemStack(mat, amount);
                if (displayName != null && !displayName.isEmpty()) {
                    ItemMeta meta = stack.getItemMeta();
                    if (meta != null) {
                        meta.displayName(ColorUtils.toComponent(displayName));
                        stack.setItemMeta(meta);
                    }
                }
                return stack;
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid material in pending reward: " + material);
            }
        }
        return null;
    }
}
