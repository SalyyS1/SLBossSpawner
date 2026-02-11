package dev.salyvn.slBossSpawner.reward;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Protects dropped reward items so only the owner can pick them up.
 * Uses PersistentDataContainer on the Item entity.
 */
public class ItemProtectionListener implements Listener {
    private final SLBossSpawner plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey expireKey;
    private final NamespacedKey actionKey;

    // Track expiry tasks by item entity UUID
    private final Map<UUID, BukkitTask> expiryTasks = new HashMap<>();

    public ItemProtectionListener(SLBossSpawner plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "reward_owner");
        this.expireKey = new NamespacedKey(plugin, "reward_expire");
        this.actionKey = new NamespacedKey(plugin, "reward_expire_action");
    }

    /**
     * Tag a dropped item with owner protection.
     */
    public void tagItem(Item item, UUID ownerUuid, int durationSeconds, String expireAction) {
        PersistentDataContainer pdc = item.getPersistentDataContainer();
        pdc.set(ownerKey, PersistentDataType.STRING, ownerUuid.toString());
        long expireEpoch = System.currentTimeMillis() / 1000 + durationSeconds;
        pdc.set(expireKey, PersistentDataType.LONG, expireEpoch);
        pdc.set(actionKey, PersistentDataType.STRING, expireAction);

        // Schedule expiry
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!item.isValid() || item.isDead()) return;

            if ("despawn".equalsIgnoreCase(expireAction)) {
                item.remove();
            } else {
                // Make public: remove protection tags
                PersistentDataContainer container = item.getPersistentDataContainer();
                container.remove(ownerKey);
                container.remove(expireKey);
                container.remove(actionKey);
            }
            expiryTasks.remove(item.getUniqueId());
        }, durationSeconds * 20L);

        expiryTasks.put(item.getUniqueId(), task);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Item item = event.getItem();
        PersistentDataContainer pdc = item.getPersistentDataContainer();

        if (!pdc.has(ownerKey, PersistentDataType.STRING)) return;

        String ownerStr = pdc.get(ownerKey, PersistentDataType.STRING);
        if (ownerStr == null) return;

        UUID ownerUuid;
        try {
            ownerUuid = UUID.fromString(ownerStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        // Check if protection expired
        Long expireEpoch = pdc.get(expireKey, PersistentDataType.LONG);
        if (expireEpoch != null && System.currentTimeMillis() / 1000 >= expireEpoch) {
            // Protection expired, allow pickup
            pdc.remove(ownerKey);
            pdc.remove(expireKey);
            pdc.remove(actionKey);
            return;
        }

        // Only owner can pick up
        if (!player.getUniqueId().equals(ownerUuid)) {
            event.setCancelled(true);

            // ActionBar message
            String msg = plugin.getMessageManager().getRawMessage("pickup-denied");
            Component component = ColorUtils.toComponent(msg);
            player.sendActionBar(component);
        }
    }

    public void cleanup() {
        for (BukkitTask task : expiryTasks.values()) {
            task.cancel();
        }
        expiryTasks.clear();
    }
}
