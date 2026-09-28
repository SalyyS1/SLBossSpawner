package dev.salyvn.slBossSpawner.boss;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shares one plugin chunk ticket between bosses in the same chunk. Unlike
 * Chunk#setForceLoaded, a plugin ticket cannot clear another plugin's force
 * load setting, and reference counts keep one boss from releasing another's
 * chunk.
 */
final class BossChunkTickets {
    private static final Map<ChunkKey, Integer> REFERENCES = new HashMap<>();

    private BossChunkTickets() {
    }

    static void acquire(Plugin plugin, World world, int chunkX, int chunkZ) {
        ChunkKey key = new ChunkKey(plugin, world.getUID(), chunkX, chunkZ);
        int count = REFERENCES.getOrDefault(key, 0);
        if (count == 0) {
            world.addPluginChunkTicket(chunkX, chunkZ, plugin);
        }
        REFERENCES.put(key, count + 1);
    }

    static void release(Plugin plugin, World world, int chunkX, int chunkZ) {
        ChunkKey key = new ChunkKey(plugin, world.getUID(), chunkX, chunkZ);
        int count = REFERENCES.getOrDefault(key, 0);
        if (count <= 0) return;
        if (count == 1) {
            REFERENCES.remove(key);
            world.removePluginChunkTicket(chunkX, chunkZ, plugin);
        } else {
            REFERENCES.put(key, count - 1);
        }
    }

    private record ChunkKey(Plugin plugin, UUID worldId, int x, int z) {
    }
}
