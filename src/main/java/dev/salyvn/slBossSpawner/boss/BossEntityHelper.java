package dev.salyvn.slBossSpawner.boss;

import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.entity.Entity;

import javax.annotation.Nullable;

/**
 * Safely access Bukkit entity from MythicMobs ActiveMob.
 * Prevents NPE when entity is unloaded, despawned, or executor is null.
 */
public final class BossEntityHelper {
    private BossEntityHelper() {}

    @Nullable
    public static Entity getBukkitEntity(@Nullable ActiveMob mob) {
        if (mob == null) return null;
        var executor = mob.getEntity();
        if (executor == null) return null;
        Entity entity = executor.getBukkitEntity();
        if (entity == null || !entity.isValid() || entity.isDead()) return null;
        return entity;
    }
}
