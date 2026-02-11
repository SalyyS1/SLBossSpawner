package dev.salyvn.slBossSpawner;

import dev.salyvn.slBossSpawner.boss.BossScheduler;
import dev.salyvn.slBossSpawner.broadcast.BroadcastManager;
import dev.salyvn.slBossSpawner.commands.BossCommand;
import dev.salyvn.slBossSpawner.config.ConfigManager;
import dev.salyvn.slBossSpawner.config.MessageManager;
import dev.salyvn.slBossSpawner.config.ScheduleManager;
import dev.salyvn.slBossSpawner.listener.BossDeathListener;
import dev.salyvn.slBossSpawner.listener.DamageTracker;
import dev.salyvn.slBossSpawner.listener.SupportTracker;
import dev.salyvn.slBossSpawner.listener.TankTracker;
import dev.salyvn.slBossSpawner.persist.BossStateManager;
import dev.salyvn.slBossSpawner.persist.PendingRewardManager;
import dev.salyvn.slBossSpawner.placeholder.BossPlaceholder;
import dev.salyvn.slBossSpawner.reward.ItemProtectionListener;
import dev.salyvn.slBossSpawner.reward.PendingRewardListener;
import dev.salyvn.slBossSpawner.reward.RewardManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SLBossSpawner extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private ScheduleManager scheduleManager;
    private volatile BossScheduler bossScheduler;
    private BroadcastManager broadcastManager;
    private DamageTracker damageTracker;
    private TankTracker tankTracker;
    private SupportTracker supportTracker;
    private BossStateManager bossStateManager;
    private PendingRewardManager pendingRewardManager;
    private RewardManager rewardManager;
    private ItemProtectionListener itemProtectionListener;
    private BossPlaceholder placeholder;

    @Override
    public void onEnable() {
        if (!checkDependencies()) {
            getLogger().severe("Missing required dependencies! Disabling plugin...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Config & messages
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        scheduleManager = new ScheduleManager(this);

        // Broadcast
        broadcastManager = new BroadcastManager(this);

        // Damage tracking
        damageTracker = new DamageTracker(this);
        Bukkit.getPluginManager().registerEvents(damageTracker, this);

        // Tank tracking
        tankTracker = new TankTracker(this);
        Bukkit.getPluginManager().registerEvents(tankTracker, this);

        // Support tracking
        supportTracker = new SupportTracker(this);
        Bukkit.getPluginManager().registerEvents(supportTracker, this);

        // Item protection
        itemProtectionListener = new ItemProtectionListener(this);
        Bukkit.getPluginManager().registerEvents(itemProtectionListener, this);

        // Rewards
        pendingRewardManager = new PendingRewardManager(this);
        rewardManager = new RewardManager(this, itemProtectionListener);

        // Pending reward delivery on join
        Bukkit.getPluginManager().registerEvents(new PendingRewardListener(this), this);

        // Boss scheduler
        bossScheduler = new BossScheduler(this, scheduleManager);

        // Death listener (needs scheduler, all trackers, reward manager)
        Bukkit.getPluginManager().registerEvents(
            new BossDeathListener(this, damageTracker, tankTracker, supportTracker, rewardManager), this);

        // Persistence
        bossStateManager = new BossStateManager(this);

        // Commands
        registerCommands();

        // PlaceholderAPI
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholder = new BossPlaceholder(this);
            placeholder.register();
            getLogger().info("PlaceholderAPI integration enabled!");
        }

        // MMOItems softdepend check
        if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            getLogger().info("MMOItems integration enabled!");
        }

        // Start scheduler & restore state
        bossScheduler.start();
        bossStateManager.restoreFromState(bossScheduler);

        getLogger().info("SLBossSpawner enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Save state before despawning
        if (bossStateManager != null && bossScheduler != null) {
            bossStateManager.saveAll();
        }

        if (bossScheduler != null) {
            bossScheduler.stop();
            bossScheduler.despawnAll();
        }

        // Save pending rewards synchronously to avoid data loss on shutdown
        if (pendingRewardManager != null) {
            pendingRewardManager.saveSync();
        }

        if (damageTracker != null) {
            damageTracker.clearAll();
        }

        if (tankTracker != null) {
            tankTracker.clearAll();
        }

        if (supportTracker != null) {
            supportTracker.clearAll();
        }

        if (itemProtectionListener != null) {
            itemProtectionListener.cleanup();
        }

        if (placeholder != null) {
            placeholder.unregister();
        }

        getLogger().info("SLBossSpawner disabled!");
    }

    private boolean checkDependencies() {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            getLogger().severe("MythicMobs not found! This plugin requires MythicMobs to work.");
            return false;
        }
        return true;
    }

    private void registerCommands() {
        PluginCommand command = getCommand("slboss");
        if (command != null) {
            BossCommand bossCommand = new BossCommand(this, messageManager);
            command.setExecutor(bossCommand);
            command.setTabCompleter(bossCommand);
        }
    }

    public void reload() {
        configManager.reload();
        messageManager.reload();
        scheduleManager.reload();

        // Save state, despawn, recreate scheduler
        bossStateManager.saveAll();
        bossScheduler.stop();
        bossScheduler.despawnAll();
        damageTracker.clearAll();
        tankTracker.clearAll();
        supportTracker.clearAll();

        bossScheduler = new BossScheduler(this, scheduleManager);
        bossScheduler.start();
        bossStateManager.restoreFromState(bossScheduler);
    }

    public void debug(String message) {
        if (configManager.isDebug()) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    public ConfigManager getConfigManager() { return configManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public ScheduleManager getScheduleManager() { return scheduleManager; }
    public BossScheduler getBossScheduler() { return bossScheduler; }
    public BroadcastManager getBroadcastManager() { return broadcastManager; }
    public DamageTracker getDamageTracker() { return damageTracker; }
    public TankTracker getTankTracker() { return tankTracker; }
    public SupportTracker getSupportTracker() { return supportTracker; }
    public BossStateManager getBossStateManager() { return bossStateManager; }
    public PendingRewardManager getPendingRewardManager() { return pendingRewardManager; }
    public RewardManager getRewardManager() { return rewardManager; }
}
