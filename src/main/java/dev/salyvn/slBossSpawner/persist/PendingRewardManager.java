package dev.salyvn.slBossSpawner.persist;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import org.bukkit.configuration.file.YamlConfiguration;

import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages pending rewards for offline players.
 * Rewards are queued and delivered when the player joins.
 */
public class PendingRewardManager {
    private final SLBossSpawner plugin;
    private final File pendingFile;

    // playerUuid -> list of pending reward entries
    private final Map<UUID, List<PendingReward>> pendingRewards = new HashMap<>();
    private boolean savePending = false;

    public PendingRewardManager(SLBossSpawner plugin) {
        this.plugin = plugin;
        this.pendingFile = new File(plugin.getDataFolder(), "pending-rewards.yml");
        load();
    }

    /**
     * Queue a command reward for an offline player.
     */
    public void queueCommand(UUID playerUuid, String command) {
        pendingRewards.computeIfAbsent(playerUuid, k -> new ArrayList<>())
                .add(new PendingReward("command", command, null, null, 0, null));
        save();
    }

    /**
     * Queue an item reward for an offline player.
     */
    public void queueItem(UUID playerUuid, String material, String displayName, int amount, String mmoitemsId) {
        pendingRewards.computeIfAbsent(playerUuid, k -> new ArrayList<>())
                .add(new PendingReward("item", null, material, displayName, amount, mmoitemsId));
        save();
    }

    /**
     * Get and clear pending rewards for a player.
     */
    public List<PendingReward> claimRewards(UUID playerUuid) {
        List<PendingReward> rewards = pendingRewards.remove(playerUuid);
        if (rewards != null) {
            save();
        }
        return rewards != null ? rewards : List.of();
    }

    public boolean hasPending(UUID playerUuid) {
        List<PendingReward> list = pendingRewards.get(playerUuid);
        return list != null && !list.isEmpty();
    }

    private void load() {
        if (!pendingFile.exists())
            return;

        YamlConfiguration yaml;
        try {
            yaml = YamlConfiguration.loadConfiguration(pendingFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Corrupted pending-rewards.yml: " + e.getMessage());
            return;
        }

        for (String uuidStr : yaml.getKeys(false)) {
            UUID playerUuid;
            try {
                playerUuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue;
            }

            List<Map<?, ?>> entries = yaml.getMapList(uuidStr);
            List<PendingReward> rewards = new ArrayList<>();

            for (Map<?, ?> entry : entries) {
                String type = entry.containsKey("type") ? String.valueOf(entry.get("type")) : "command";
                String command = entry.containsKey("command") ? String.valueOf(entry.get("command")) : null;
                String material = entry.containsKey("material") ? String.valueOf(entry.get("material")) : null;
                String displayName = entry.containsKey("display-name") ? String.valueOf(entry.get("display-name"))
                        : null;
                int amount = 1;
                if (entry.containsKey("amount")) {
                    Object raw = entry.get("amount");
                    if (raw instanceof Number n) {
                        amount = n.intValue();
                    } else {
                        try { amount = Integer.parseInt(String.valueOf(raw)); } catch (NumberFormatException ignored) {}
                    }
                }
                String mmoitemsId = entry.containsKey("mmoitems-id") ? String.valueOf(entry.get("mmoitems-id")) : null;

                rewards.add(new PendingReward(type, command, material, displayName, amount, mmoitemsId));
            }

            if (!rewards.isEmpty()) {
                pendingRewards.put(playerUuid, rewards);
            }
        }

        plugin.debug("Loaded pending rewards for " + pendingRewards.size() + " players");
    }

    private void save() {
        if (savePending) return;
        savePending = true;

        // Debounce: schedule a single save 1 tick later, coalescing rapid calls
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            savePending = false;
            saveNow();
        }, 1L);
    }

    /**
     * Immediate synchronous save. Called on server shutdown.
     */
    public void saveSync() {
        savePending = false;
        YamlConfiguration yaml = buildYaml();
        try {
            yaml.save(pendingFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save pending rewards: " + e.getMessage());
        }
    }

    private void saveNow() {
        YamlConfiguration yaml = buildYaml();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                yaml.save(pendingFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save pending rewards: " + e.getMessage());
            }
        });
    }

    private YamlConfiguration buildYaml() {
        // Build YAML snapshot on main thread
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<UUID, List<PendingReward>> entry : pendingRewards.entrySet()) {
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (PendingReward reward : entry.getValue()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("type", reward.type());
                if (reward.command() != null)
                    map.put("command", reward.command());
                if (reward.material() != null)
                    map.put("material", reward.material());
                if (reward.displayName() != null)
                    map.put("display-name", reward.displayName());
                if (reward.amount() > 0)
                    map.put("amount", reward.amount());
                if (reward.mmoitemsId() != null)
                    map.put("mmoitems-id", reward.mmoitemsId());
                serialized.add(map);
            }
            yaml.set(entry.getKey().toString(), serialized);
        }
        return yaml;
    }

    /**
     * Pending reward entry.
     */
    public record PendingReward(
            String type, // "command" or "item"
            String command, // for command type
            String material, // for item type (Material name or MMOItems type)
            String displayName, // for vanilla items
            int amount,
            String mmoitemsId // for MMOItems
    ) {
    }
}
