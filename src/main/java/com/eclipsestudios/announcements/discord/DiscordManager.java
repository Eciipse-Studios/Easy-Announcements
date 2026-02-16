package com.eclipsestudios.announcements.discord;

import com.eclipsestudios.announcements.EclipseStudiosAnnouncements;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.time.Instant;
import java.util.logging.Level;

/**
 * Manages Discord integration with fully customizable embeds
 */
public class DiscordManager {
    
    private final EclipseStudiosAnnouncements plugin;
    private final FileConfiguration discordConfig;
    
    public DiscordManager(EclipseStudiosAnnouncements plugin, FileConfiguration discordConfig) {
        this.plugin = plugin;
        this.discordConfig = discordConfig;
    }
    
    /**
     * Send announcement to Discord with fully customizable embed
     */
    public void sendToDiscord(String message, Player sender, int playerCount) {
        if (!discordConfig.getBoolean("discord.enabled", false)) {
            return;
        }
        
        if (!discordConfig.getBoolean("embed.enabled", true)) {
            return;
        }
        
        try {
            String channelId = discordConfig.getString("discord.channel-id", "");
            if (channelId.isEmpty() || channelId.equals("YOUR_CHANNEL_ID_HERE")) {
                plugin.getLogger().warning("Discord channel ID not configured! Please set discord.channel-id in discord.yml");
                return;
            }
            
            TextChannel channel = DiscordSRV.getPlugin().getJda().getTextChannelById(channelId);
            if (channel == null) {
                plugin.getLogger().warning("Discord channel not found with ID: " + channelId);
                return;
            }
            
            EmbedBuilder embed = buildEmbed(message, sender, playerCount);
            
            channel.sendMessageEmbeds(embed.build()).queue();
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to send announcement to Discord", e);
        }
    }
    
    /**
     * Build custom embed based on discord.yml configuration
     */
    private EmbedBuilder buildEmbed(String message, Player sender, int playerCount) {
        EmbedBuilder embed = new EmbedBuilder();
        
        // Color (HEX format)
        String colorHex = discordConfig.getString("embed.color", "FFD700");
        try {
            Color color = Color.decode("#" + colorHex.replace("#", ""));
            embed.setColor(color);
        } catch (Exception e) {
            embed.setColor(Color.decode("#FFD700")); // Default gold
        }
        
        // Title
        String title = discordConfig.getString("embed.title", "📢 Server Announcement");
        if (title != null && !title.isEmpty()) {
            embed.setTitle(title);
        }
        
        // Description
        String description = discordConfig.getString("embed.description", "%message%")
            .replace("%message%", message);
        embed.setDescription(description);
        
        // Timestamp
        if (discordConfig.getBoolean("embed.show-timestamp", true)) {
            embed.setTimestamp(Instant.now());
        }
        
        // Author section
        if (discordConfig.getBoolean("embed.author.enabled", true)) {
            String authorName;
            String authorIcon = null;
            
            if (sender != null && discordConfig.getBoolean("embed.author.show-player-name", true)) {
                String format = discordConfig.getString("embed.author.format", "Sent by %player%");
                authorName = format.replace("%player%", sender.getName());
                
                String iconUrl = discordConfig.getString("embed.author.icon-url", 
                    "https://minotar.net/avatar/%player%/64.png");
                authorIcon = iconUrl.replace("%player%", sender.getName());
            } else {
                String format = discordConfig.getString("embed.author.format", "Sent by %player%");
                authorName = format.replace("%player%", "Server Console");
            }
            
            if (authorIcon != null && !authorIcon.isEmpty()) {
                embed.setAuthor(authorName, null, authorIcon);
            } else {
                embed.setAuthor(authorName);
            }
        }
        
        // Thumbnail (small image top right)
        if (discordConfig.getBoolean("embed.thumbnail.enabled", false)) {
            String thumbnailUrl = discordConfig.getString("embed.thumbnail.url", "");
            if (!thumbnailUrl.isEmpty()) {
                embed.setThumbnail(thumbnailUrl);
            }
        }
        
        // Image (large image below description)
        if (discordConfig.getBoolean("embed.image.enabled", false)) {
            String imageUrl = discordConfig.getString("embed.image.url", "");
            if (!imageUrl.isEmpty()) {
                embed.setImage(imageUrl);
            }
        }
        
        // Footer
        if (discordConfig.getBoolean("embed.footer.enabled", true)) {
            String footerText = discordConfig.getString("embed.footer.text", "Eciipse Studios™");
            
            // Add player count if enabled
            if (discordConfig.getBoolean("embed.footer.show-player-count", true)) {
                String playerCountFormat = discordConfig.getString("embed.footer.player-count-format", 
                    "Sent to %count% player(s)")
                    .replace("%count%", String.valueOf(playerCount));
                
                if (!footerText.isEmpty()) {
                    footerText += " • " + playerCountFormat;
                } else {
                    footerText = playerCountFormat;
                }
            }
            
            String footerIcon = discordConfig.getString("embed.footer.icon-url", "");
            if (!footerIcon.isEmpty()) {
                embed.setFooter(footerText, footerIcon);
            } else {
                embed.setFooter(footerText);
            }
        }
        
        // Custom fields (embed builder functionality)
        ConfigurationSection fieldsSection = discordConfig.getConfigurationSection("embed.fields");
        if (fieldsSection != null) {
            for (String key : fieldsSection.getKeys(false)) {
                ConfigurationSection field = fieldsSection.getConfigurationSection(key);
                if (field != null && field.getBoolean("enabled", false)) {
                    String name = field.getString("name", "Field");
                    String value = field.getString("value", "Value");
                    boolean inline = field.getBoolean("inline", false);
                    
                    embed.addField(name, value, inline);
                }
            }
        }
        
        return embed;
    }
}
