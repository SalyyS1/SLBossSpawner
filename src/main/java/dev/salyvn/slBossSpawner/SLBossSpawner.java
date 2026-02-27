package dev.salyvn.slBossSpawner;

import dev.salyvn.slBossSpawner.boss.BossScheduler;
import dev.salyvn.slBossSpawner.broadcast.BroadcastManager;
import dev.salyvn.slBossSpawner.commands.BossCommand;
import dev.salyvn.slBossSpawner.config.ConfigManager;
import dev.salyvn.slBossSpawner.config.MessageManager;
import dev.salyvn.slBossSpawner.config.ScheduleManager;
import dev.salyvn.slBossSpawner.database.DatabaseManager;
import dev.salyvn.slBossSpawner.listener.BossDeathListener;
import dev.salyvn.slBossSpawner.statistics.CalendarQuarterSeasonResolver;
import dev.salyvn.slBossSpawner.statistics.LeaderboardCache;
import dev.salyvn.slBossSpawner.statistics.StatisticsManager;
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
    private DatabaseManager databaseManager;
    private StatisticsManager statisticsManager;
    private LeaderboardCache leaderboardCache;

    // H3 FIX: flag to suppress death-event processing during reload despawn
    private volatile boolean reloading = false;

    @Override
    public void onEnable() {
        if (!checkDependencies()) {
            getLogger().severe("Missing required dependencies! Disabling plugin...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        scheduleManager = new ScheduleManager(this);

        if (configManager.isStatisticsEnabled()) {
            databaseManager = DatabaseManager.create(this, configManager.getDatabaseType());
            try {
                databaseManager.initialize();
                getLogger().info("Statistics database initialized (" + configManager.getDatabaseType() + ")");
            } catch (Exception e) {
                getLogger().severe("Failed to initialize statistics database: " + e.getMessage());
                databaseManager = null;
            }
        }

        if (databaseManager != null) {
            statisticsManager = new StatisticsManager(this, databaseManager, new CalendarQuarterSeasonResolver());
            statisticsManager.startArchivalScheduler();
            leaderboardCache = new LeaderboardCache(this, databaseManager, statisticsManager.getSeasonResolver());
            leaderboardCache.start();
            getLogger().info("Statistics system enabled!");
        }

        broadcastManager = new BroadcastManager(this);

        damageTracker = new DamageTracker(this);
        Bukkit.getPluginManager().registerEvents(damageTracker, this);

        tankTracker = new TankTracker(this);
        Bukkit.getPluginManager().registerEvents(tankTracker, this);

        supportTracker = new SupportTracker(this);
        Bukkit.getPluginManager().registerEvents(supportTracker, this);

        itemProtectionListener = new ItemProtectionListener(this);
        Bukkit.getPluginManager().registerEvents(itemProtectionListener, this);

        pendingRewardManager = new PendingRewardManager(this);
        rewardManager = new RewardManager(this, itemProtectionListener);

        Bukkit.getPluginManager().registerEvents(new PendingRewardListener(this), this);

        bossScheduler = new BossScheduler(this, scheduleManager);

        Bukkit.getPluginManager().registerEvents(
                new BossDeathListener(this, damageTracker, tankTracker, supportTracker, rewardManager), this);

        bossStateManager = new BossStateManager(this);

        registerCommands();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholder = new BossPlaceholder(this, leaderboardCache);
            placeholder.register();
            getLogger().info("PlaceholderAPI integration enabled!");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            getLogger().info("MMOItems integration enabled!");
        }

        bossScheduler.start();

        // H4 FIX: delay state restore by 2 ticks to ensure worlds are fully loaded
        Bukkit.getScheduler().runTaskLater(this,
                () -> bossStateManager.restoreFromState(bossScheduler), 2L);

        getLogger().info("SLBossSpawner enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (leaderboardCache != null) {
            leaderboardCache.stop();
        }

        if (statisticsManager != null) {
            statisticsManager.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        if (bossStateManager != null && bossScheduler != null) {
            bossStateManager.saveAll();
        }

        if (bossScheduler != null) {
            bossScheduler.stop();
            bossScheduler.despawnAll();
        }

        if (pendingRewardManager != null) {
            pendingRewardManager.saveSync();
        }

        if (damageTracker != null) damageTracker.clearAll();
        if (tankTracker != null) tankTracker.clearAll();
        if (supportTracker != null) supportTracker.clearAll();

        if (itemProtectionListener != null) itemProtectionListener.cleanup();
        if (placeholder != null) placeholder.unregister();

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
        // H3 FIX: set flag before despawn so death events fired during reload are ignored
        reloading = true;

        configManager.reload();
        messageManager.reload();
        scheduleManager.reload();

        // Recreate DB connection with potentially updated config
        if (leaderboardCache != null) {
            leaderboardCache.stop();
            leaderboardCache = null;
        }
        if (statisticsManager != null) {
            statisticsManager.shutdown();
            statisticsManager = null;
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
            databaseManager = null;
        }
        if (configManager.isStatisticsEnabled()) {
            databaseManager = DatabaseManager.create(this, configManager.getDatabaseType());
            try {
                databaseManager.initialize();
                getLogger().info("Statistics database reinitialized (" + configManager.getDatabaseType() + ")");
            } catch (Exception e) {
                getLogger().severe("Failed to reinitialize statistics database: " + e.getMessage());
                databaseManager = null;
            }
        }
        if (databaseManager != null) {
            statisticsManager = new StatisticsManager(this, databaseManager, new CalendarQuarterSeasonResolver());
            statisticsManager.startArchivalScheduler();
            leaderboardCache = new LeaderboardCache(this, databaseManager, statisticsManager.getSeasonResolver());
            leaderboardCache.start();
        }

        bossStateManager.saveAll();
        bossScheduler.stop();
        bossScheduler.despawnAll();
        damageTracker.clearAll();
        tankTracker.clearAll();
        supportTracker.clearAll();

        bossScheduler = new BossScheduler(this, scheduleManager);
        bossScheduler.start();
        bossStateManager.restoreFromState(bossScheduler);

        // H3 FIX: clear flag only after new scheduler is running
        reloading = false;
    }

    public boolean isReloading() { return reloading; }

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
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public StatisticsManager getStatisticsManager() { return statisticsManager; }
    public LeaderboardCache getLeaderboardCache() { return leaderboardCache; }
}
