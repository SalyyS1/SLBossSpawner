package dev.fluffyworld.fluffyBossSpawner;

import dev.fluffyworld.fluffyBossSpawner.boss.BossScheduler;
import dev.fluffyworld.fluffyBossSpawner.commands.BossCommand;
import dev.fluffyworld.fluffyBossSpawner.config.ConfigManager;
import dev.fluffyworld.fluffyBossSpawner.config.MessageManager;
import dev.fluffyworld.fluffyBossSpawner.config.ScheduleManager;
import dev.fluffyworld.fluffyBossSpawner.placeholder.BossPlaceholder;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class FluffyBossSpawner extends JavaPlugin {
    
    private ConfigManager configManager;
    private MessageManager messageManager;
    private ScheduleManager scheduleManager;
    private BossScheduler bossScheduler;
    private BossPlaceholder placeholder;

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
        bossScheduler = new BossScheduler(this, scheduleManager);
        
        registerCommands();
        
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholder = new BossPlaceholder(this);
            placeholder.register();
            getLogger().info("PlaceholderAPI integration enabled!");
        }
        
        bossScheduler.start();
        getLogger().info("FluffyBossSpawner enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (bossScheduler != null) {
            bossScheduler.stop();
            bossScheduler.despawnAll();
        }
        
        if (placeholder != null) {
            placeholder.unregister();
        }
        
        getLogger().info("FluffyBossSpawner disabled!");
    }
    
    private boolean checkDependencies() {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            getLogger().severe("MythicMobs not found! This plugin requires MythicMobs to work.");
            return false;
        }
        return true;
    }
    
    private void registerCommands() {
        PluginCommand command = getCommand("fboss");
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
        
        bossScheduler.stop();
        bossScheduler = new BossScheduler(this, scheduleManager);
        bossScheduler.start();
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public MessageManager getMessageManager() {
        return messageManager;
    }
    
    public ScheduleManager getScheduleManager() {
        return scheduleManager;
    }
    
    public BossScheduler getBossScheduler() {
        return bossScheduler;
    }
}
