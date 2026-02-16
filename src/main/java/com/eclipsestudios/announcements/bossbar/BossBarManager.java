package com.eclipsestudios.announcements.bossbar;

import com.eclipsestudios.announcements.EclipseStudiosAnnouncements;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages animated boss bar announcements
 */
public class BossBarManager {
    
    private final EclipseStudiosAnnouncements plugin;
    private final Map<Player, BossBar> activeBossBars = new HashMap<>();
    private final Map<Player, BukkitTask> animationTasks = new HashMap<>();
    
    public BossBarManager(EclipseStudiosAnnouncements plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Send boss bar announcement to players
     */
    public void sendBossBar(Collection<Player> targets, String message, Player sender) {
        if (!plugin.getConfig().getBoolean("bossbar.enabled", false)) {
            return;
        }
        
        String bossBarMessage = plugin.getConfig().getString("bossbar.message", "&6&l✦ &f%message% &6&l✦")
            .replace("%message%", message);
        
        // Get boss bar settings
        String colorStr = plugin.getConfig().getString("bossbar.color", "YELLOW");
        String styleStr = plugin.getConfig().getString("bossbar.style", "SOLID");
        
        BossBar.Color color = parseBossBarColor(colorStr);
        BossBar.Overlay overlay = parseBossBarStyle(styleStr);
        
        // Animation settings
        boolean animationEnabled = plugin.getConfig().getBoolean("bossbar.animation.enabled", true);
        String animationType = plugin.getConfig().getString("bossbar.animation.type", "FILL");
        int duration = plugin.getConfig().getInt("bossbar.animation.duration", 5);
        int updateInterval = plugin.getConfig().getInt("bossbar.animation.update-interval", 2);
        
        for (Player player : targets) {
            // Cancel any existing boss bar for this player
            removeBossBar(player);
            
            // Parse message with placeholders for each player
            String parsedMessage = bossBarMessage;
            if (plugin.isPlaceholderAPIEnabled()) {
                parsedMessage = PlaceholderAPI.setPlaceholders(player, parsedMessage);
            }
            
            Component titleComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(parsedMessage);
            
            // Create boss bar
            BossBar bossBar = BossBar.bossBar(
                titleComponent,
                animationEnabled ? 0.0f : 1.0f,
                color,
                overlay
            );
            
            // Show boss bar
            player.showBossBar(bossBar);
            activeBossBars.put(player, bossBar);
            
            // Start animation
            if (animationEnabled) {
                BukkitTask task = startAnimation(player, bossBar, animationType, duration, updateInterval);
                animationTasks.put(player, task);
            } else {
                // Just remove after duration
                Bukkit.getScheduler().runTaskLater(plugin, () -> removeBossBar(player), duration * 20L);
            }
        }
    }
    
    /**
     * Start boss bar animation
     */
    private BukkitTask startAnimation(Player player, BossBar bossBar, String type, int durationSeconds, int updateInterval) {
        final long totalTicks = durationSeconds * 20L;
        final long updateTicks = Math.max(1, updateInterval);
        final int totalUpdates = (int) (totalTicks / updateTicks);
        
        return new BukkitRunnable() {
            int currentUpdate = 0;
            
            @Override
            public void run() {
                if (currentUpdate >= totalUpdates || !player.isOnline()) {
                    removeBossBar(player);
                    cancel();
                    return;
                }
                
                float progress = calculateProgress(type, currentUpdate, totalUpdates);
                bossBar.progress(progress);
                
                currentUpdate++;
            }
        }.runTaskTimer(plugin, 0L, updateTicks);
    }
    
    /**
     * Calculate progress based on animation type
     */
    private float calculateProgress(String type, int current, int total) {
        float normalProgress = (float) current / (float) total;
        
        switch (type.toUpperCase()) {
            case "FILL":
                // Bar fills from 0 to 1
                return normalProgress;
                
            case "DRAIN":
                // Bar drains from 1 to 0
                return 1.0f - normalProgress;
                
            case "PULSE":
                // Bar pulses (0 -> 1 -> 0)
                if (normalProgress <= 0.5f) {
                    return normalProgress * 2.0f; // 0 to 1
                } else {
                    return (1.0f - normalProgress) * 2.0f; // 1 to 0
                }
                
            default:
                return normalProgress;
        }
    }
    
    /**
     * Remove boss bar from player
     */
    private void removeBossBar(Player player) {
        BossBar bossBar = activeBossBars.remove(player);
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
        
        BukkitTask task = animationTasks.remove(player);
        if (task != null) {
            task.cancel();
        }
    }
    
    /**
     * Parse boss bar color from string
     */
    private BossBar.Color parseBossBarColor(String colorStr) {
        try {
            return BossBar.Color.valueOf(colorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid boss bar color: " + colorStr + ", using YELLOW");
            return BossBar.Color.YELLOW;
        }
    }
    
    /**
     * Parse boss bar style from string
     */
    private BossBar.Overlay parseBossBarStyle(String styleStr) {
        try {
            // Convert style names
            switch (styleStr.toUpperCase()) {
                case "SOLID":
                    return BossBar.Overlay.PROGRESS;
                case "SEGMENTED_6":
                    return BossBar.Overlay.NOTCHED_6;
                case "SEGMENTED_10":
                    return BossBar.Overlay.NOTCHED_10;
                case "SEGMENTED_12":
                    return BossBar.Overlay.NOTCHED_12;
                case "SEGMENTED_20":
                    return BossBar.Overlay.NOTCHED_20;
                default:
                    return BossBar.Overlay.PROGRESS;
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid boss bar style: " + styleStr + ", using SOLID");
            return BossBar.Overlay.PROGRESS;
        }
    }
    
    /**
     * Clean up all active boss bars
     */
    public void cleanup() {
        for (Player player : activeBossBars.keySet()) {
            removeBossBar(player);
        }
    }
}
