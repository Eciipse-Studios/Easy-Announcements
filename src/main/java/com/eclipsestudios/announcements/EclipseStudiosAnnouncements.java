package com.eclipsestudios.announcements;

import com.eclipsestudios.announcements.bossbar.BossBarManager;
import com.eclipsestudios.announcements.commands.AnnouncementCommandHandler;
import com.eclipsestudios.announcements.discord.DiscordCommandHandler;
import com.eclipsestudios.announcements.discord.DiscordManager;
import github.scarsz.discordsrv.DiscordSRV;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collection;
import java.util.logging.Level;

public final class EclipseStudiosAnnouncements extends JavaPlugin {

    private static EclipseStudiosAnnouncements instance;
    private boolean placeholderAPIEnabled = false;
    private boolean discordSRVEnabled = false;
    
    // Config files
    private FileConfiguration discordConfig;
    private File discordConfigFile;
    
    // Managers
    private DiscordManager discordManager;
    private DiscordCommandHandler discordCommandHandler;
    private BossBarManager bossBarManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // Load configs
        saveDefaultConfig();
        loadDiscordConfig();
        
        // Initialize managers
        bossBarManager = new BossBarManager(this);

        // Check for PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderAPIEnabled = true;
            getLogger().info("PlaceholderAPI found! Placeholder support enabled.");
        }

        // Check for DiscordSRV
        if (getServer().getPluginManager().getPlugin("DiscordSRV") != null) {
            discordSRVEnabled = true;
            getLogger().info("DiscordSRV found! Discord integration enabled.");
            
            // Initialize Discord features
            discordManager = new DiscordManager(this, discordConfig);
            discordCommandHandler = new DiscordCommandHandler(this, discordConfig);
            
            // Register Discord command handler (delayed to ensure DiscordSRV is ready)
            Bukkit.getScheduler().runTaskLater(this, () -> {
                discordCommandHandler.registerCommand();
            }, 40L); // 2 second delay
        }

        registerCommands();

        getLogger().info("Easy Announcements enabled. Plugin by Sketch494. Eciipse Studios™");
    }

    @Override
    public void onDisable() {
        // Clean up boss bars
        if (bossBarManager != null) {
            bossBarManager.cleanup();
        }
        
        getLogger().info("Easy Announcements disabled.");
    }

    /**
     * Load discord.yml configuration
     */
    private void loadDiscordConfig() {
        discordConfigFile = new File(getDataFolder(), "discord.yml");
        
        if (!discordConfigFile.exists()) {
            try {
                // Create plugin data folder if it doesn't exist
                if (!getDataFolder().exists()) {
                    getDataFolder().mkdirs();
                }
                
                // Copy default discord.yml from resources
                InputStream inputStream = getResource("discord.yml");
                if (inputStream != null) {
                    Files.copy(inputStream, discordConfigFile.toPath());
                    inputStream.close();
                }
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not create discord.yml", e);
            }
        }
        
        discordConfig = YamlConfiguration.loadConfiguration(discordConfigFile);
    }
    
    /**
     * Reload both config files
     */
    public void reloadConfigs() {
        reloadConfig();
        loadDiscordConfig();
        
        // Reinitialize Discord manager with new config
        if (discordSRVEnabled) {
            discordManager = new DiscordManager(this, discordConfig);
        }
    }

    private void registerCommands() {
        AnnouncementCommandHandler handler = new AnnouncementCommandHandler(this);
        
        var announcementCmd = getCommand("announcement");
        if (announcementCmd != null) {
            announcementCmd.setExecutor(handler);
            announcementCmd.setTabCompleter(handler);
        }
        
        var announceCmd = getCommand("announce");
        if (announceCmd != null) {
            announceCmd.setExecutor(handler);
            announceCmd.setTabCompleter(handler);
        }
        
        var eaCmd = getCommand("ea");
        if (eaCmd != null) {
            eaCmd.setExecutor(handler);
            eaCmd.setTabCompleter(handler);
        }
        
        var reloadCmd = getCommand("esareload");
        if (reloadCmd != null) {
            reloadCmd.setExecutor(handler);
        }
    }

    public static EclipseStudiosAnnouncements getInstance() {
        return instance;
    }

    public boolean isPlaceholderAPIEnabled() {
        return placeholderAPIEnabled;
    }

    public boolean isDiscordSRVEnabled() {
        return discordSRVEnabled;
    }
    
    public FileConfiguration getDiscordConfig() {
        return discordConfig;
    }
    
    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public void log(String msg) {
        getLogger().log(Level.INFO, msg);
    }

    /**
     * Send announcement to players with title, chat, boss bar, sound, and optional Discord message
     * @param targets Collection of players to send announcement to
     * @param message The announcement message
     * @param sender The player or console who sent the announcement (null for console)
     */
    public void sendAnnouncement(Collection<Player> targets, String message, Player sender) {
        // Send title announcement
        if (getConfig().getBoolean("title.enabled", true)) {
            sendTitleAnnouncement(targets, message, sender);
        }
        
        // Send chat announcement
        if (getConfig().getBoolean("chat.enabled", true)) {
            sendChatAnnouncement(targets, message, sender);
        }
        
        // Send boss bar announcement
        if (getConfig().getBoolean("bossbar.enabled", false)) {
            bossBarManager.sendBossBar(targets, message, sender);
        }
        
        // Play sound
        if (getConfig().getBoolean("sound.enabled", true)) {
            playSoundToPlayers(targets);
        }

        // Send to Discord if enabled
        if (discordSRVEnabled && discordConfig.getBoolean("discord.enabled", false)) {
            discordManager.sendToDiscord(message, sender, targets.size());
        }
    }
    
    /**
     * Send title announcement to players
     */
    private void sendTitleAnnouncement(Collection<Player> targets, String message, Player sender) {
        // Get config values
        String titleStr = getConfig().getString("title.title", "&6&lAnnouncement");
        String subtitleStr = getConfig().getString("title.subtitle", "&7%message%")
                .replace("%message%", message);
        
        // Get display times
        int fadeIn = getConfig().getInt("title.display-time.fade-in", 1);
        int stay = getConfig().getInt("title.display-time.stay", 5);
        int fadeOut = getConfig().getInt("title.display-time.fade-out", 1);

        // Parse color codes
        Component titleComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(titleStr);

        // Create title with custom timings
        Title.Times times = Title.Times.times(
            Duration.ofSeconds(fadeIn),
            Duration.ofSeconds(stay),
            Duration.ofSeconds(fadeOut)
        );

        // Send to all target players
        for (Player player : targets) {
            // Parse placeholders for each player individually if enabled
            String playerSubtitle = subtitleStr;
            if (placeholderAPIEnabled) {
                playerSubtitle = PlaceholderAPI.setPlaceholders(player, playerSubtitle);
            }
            Component playerSubtitleComponent = LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(playerSubtitle);
            Title playerTitle = Title.title(titleComponent, playerSubtitleComponent, times);
            player.showTitle(playerTitle);
        }
    }
    
    /**
     * Send chat announcement to players
     */
    private void sendChatAnnouncement(Collection<Player> targets, String message, Player sender) {
        String senderName = sender != null ? sender.getName() : "Server";
        
        // Build chat message from config format
        String[] lines = {
            getConfig().getString("chat.format.header", ""),
            getConfig().getString("chat.format.spacing-top", ""),
            getConfig().getString("chat.format.title", ""),
            getConfig().getString("chat.format.spacing-middle", ""),
            getConfig().getString("chat.format.message", "").replace("%message%", message),
            getConfig().getString("chat.format.spacing-bottom", ""),
            getConfig().getString("chat.format.sender", "").replace("%player%", senderName),
            getConfig().getString("chat.format.footer", "")
        };
        
        for (Player player : targets) {
            for (String line : lines) {
                if (line == null || line.isEmpty()) {
                    // Send blank line
                    player.sendMessage(Component.empty());
                    continue;
                }
                
                // Parse placeholders for each player
                String parsedLine = line;
                if (placeholderAPIEnabled) {
                    parsedLine = PlaceholderAPI.setPlaceholders(player, parsedLine);
                }
                
                Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(parsedLine);
                player.sendMessage(component);
            }
        }
    }
    
    /**
     * Play sound to players
     */
    private void playSoundToPlayers(Collection<Player> targets) {
        String soundType = getConfig().getString("sound.type", "ENTITY_EXPERIENCE_ORB_PICKUP");
        float volume = (float) getConfig().getDouble("sound.volume", 1.0);
        float pitch = (float) getConfig().getDouble("sound.pitch", 1.0);
        
        try {
            Sound sound = Sound.valueOf(soundType);
            for (Player player : targets) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid sound type in config: " + soundType);
        }
    }
}
