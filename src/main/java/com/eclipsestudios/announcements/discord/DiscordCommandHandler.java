package com.eclipsestudios.announcements.discord;

import com.eclipsestudios.announcements.EclipseStudiosAnnouncements;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Member;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Message;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Role;
import github.scarsz.discordsrv.dependencies.jda.api.entities.ChannelType;
import github.scarsz.discordsrv.dependencies.jda.api.events.interaction.SlashCommandEvent;
import github.scarsz.discordsrv.dependencies.jda.api.events.message.MessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.hooks.ListenerAdapter;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.OptionType;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.build.CommandData;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Handles Discord slash commands and prefix commands for sending announcements from Discord
 * Fully functional implementation with JDA API integration
 */
public class DiscordCommandHandler extends ListenerAdapter {
    
    private final EclipseStudiosAnnouncements plugin;
    private final FileConfiguration discordConfig;
    
    public DiscordCommandHandler(EclipseStudiosAnnouncements plugin, FileConfiguration discordConfig) {
        this.plugin = plugin;
        this.discordConfig = discordConfig;
    }
    
    /**
     * Register Discord commands (both slash and prefix)
     */
    public void registerCommand() {
        try {
            JDA jda = DiscordSRV.getPlugin().getJda();
            
            if (jda == null) {
                plugin.getLogger().warning("JDA not available! Discord commands cannot be registered.");
                return;
            }
            
            // Register event listener for both slash and prefix commands
            jda.addEventListener(this);
            
            // Register slash command if enabled
            if (discordConfig.getBoolean("discord-commands.slash-commands.enabled", true)) {
                String commandName = discordConfig.getString("discord-commands.slash-commands.command-name", "announce");
                String commandDesc = discordConfig.getString("discord-commands.slash-commands.command-description", 
                    "Send an announcement to Minecraft server");
                
                CommandData commandData = new CommandData(commandName, commandDesc)
                        .addOption(OptionType.STRING, "message", "The announcement message", true)
                        .addOption(OptionType.STRING, "target", "Target world or 'all' for all players", false);
                
                jda.upsertCommand(commandData).queue(
                    success -> plugin.getLogger().info("Discord slash command /" + commandName + " registered successfully!"),
                    error -> plugin.getLogger().warning("Failed to register Discord slash command: " + error.getMessage())
                );
            }
            
            // Log prefix command status
            if (discordConfig.getBoolean("discord-commands.prefix-commands.enabled", true)) {
                String prefix = discordConfig.getString("discord-commands.prefix-commands.prefix", "!");
                String commandName = discordConfig.getString("discord-commands.prefix-commands.command-name", "announce");
                plugin.getLogger().info("Discord prefix command " + prefix + commandName + " listener registered!");
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register Discord commands: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle Discord slash command events
     */
    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        if (!discordConfig.getBoolean("discord-commands.slash-commands.enabled", true)) {
            return;
        }
        
        String commandName = discordConfig.getString("discord-commands.slash-commands.command-name", "announce");
        
        if (!event.getName().equals(commandName)) {
            return;
        }
        
        // Check permissions
        if (!hasPermission(event.getMember())) {
            event.reply("❌ You don't have permission to use this command!").setEphemeral(true).queue();
            return;
        }
        
        // Get command options
        String message = event.getOption("message") != null ? event.getOption("message").getAsString() : null;
        String target = event.getOption("target") != null ? event.getOption("target").getAsString() : "all";
        
        if (message == null || message.isEmpty()) {
            event.reply("❌ Please provide a message!").setEphemeral(true).queue();
            return;
        }
        
        // Defer reply so we have more time to process
        event.deferReply().setEphemeral(!discordConfig.getBoolean("discord-commands.options.show-confirmation", true)).queue();
        
        // Send announcement on main thread
        String userId = event.getUser().getId();
        String finalMessage = message;
        String finalTarget = target;
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean success = handleDiscordAnnouncement(finalMessage, finalTarget, userId);
            
            if (success && discordConfig.getBoolean("discord-commands.options.show-confirmation", true)) {
                String confirmMsg = discordConfig.getString("discord-commands.options.confirmation-format", 
                    "✅ Announcement sent to **%target%**: %message%")
                    .replace("%target%", finalTarget)
                    .replace("%message%", finalMessage);
                event.getHook().editOriginal(confirmMsg).queue();
            } else if (!success) {
                event.getHook().editOriginal("❌ Failed to send announcement. Check console for errors.").queue();
            }
        });
    }
    
    /**
     * Handle Discord prefix command messages
     */
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bot messages
        if (event.getAuthor().isBot()) {
            return;
        }
        
        if (!discordConfig.getBoolean("discord-commands.prefix-commands.enabled", true)) {
            return;
        }
        
        String prefix = discordConfig.getString("discord-commands.prefix-commands.prefix", "!");
        String commandName = discordConfig.getString("discord-commands.prefix-commands.command-name", "announce");
        String fullCommand = prefix + commandName;
        
        Message message = event.getMessage();
        String content = message.getContentRaw();
        
        // Check if message starts with our command
        if (!content.startsWith(fullCommand)) {
            return;
        }
        
        // Check if in DM and if DMs are allowed
        if (event.getChannelType() == ChannelType.PRIVATE) {
            if (!discordConfig.getBoolean("discord-commands.prefix-commands.allow-in-dms", false)) {
                message.reply("❌ This command cannot be used in DMs!").queue();
                return;
            }
        }
        
        // Check permissions
        if (!hasPermission(event.getMember())) {
            message.reply("❌ You don't have permission to use this command!").queue();
            return;
        }
        
        // Parse command arguments
        String args = content.substring(fullCommand.length()).trim();
        
        if (args.isEmpty()) {
            message.reply("❌ Usage: " + fullCommand + " <message> [target]").queue();
            return;
        }
        
        // Split message and target (optional)
        String announcementMsg;
        String target = "all";
        
        // Check if world selection is enabled and if there's a target specified
        if (discordConfig.getBoolean("discord-commands.options.enable-world-selection", true)) {
            String[] parts = args.split(" ", 2);
            if (parts.length == 2 && Bukkit.getWorld(parts[0]) != null) {
                target = parts[0];
                announcementMsg = parts[1];
            } else {
                announcementMsg = args;
            }
        } else {
            announcementMsg = args;
        }
        
        // Send announcement on main thread
        String userId = event.getAuthor().getId();
        String finalAnnouncementMsg = announcementMsg;
        String finalTarget = target;
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean success = handleDiscordAnnouncement(finalAnnouncementMsg, finalTarget, userId);
            
            if (success && discordConfig.getBoolean("discord-commands.options.show-confirmation", true)) {
                String confirmMsg = discordConfig.getString("discord-commands.options.confirmation-format", 
                    "✅ Announcement sent to **%target%**: %message%")
                    .replace("%target%", finalTarget)
                    .replace("%message%", finalAnnouncementMsg);
                message.reply(confirmMsg).queue();
            } else if (!success) {
                message.reply("❌ Failed to send announcement. Check console for errors.").queue();
            }
        });
    }
    
    /**
     * Send announcement from Discord command
     * Returns true if successful, false otherwise
     */
    private boolean handleDiscordAnnouncement(String message, String target, String senderId) {
        try {
            // Determine targets
            Collection<Player> targets = new ArrayList<>();
            
            boolean perWorldEnabled = plugin.getConfig().getBoolean("per-world.enabled", false);
            boolean enableWorldSelection = discordConfig.getBoolean("discord-commands.options.enable-world-selection", true);
            
            if (target == null || target.equalsIgnoreCase("all")) {
                targets = new ArrayList<>(Bukkit.getOnlinePlayers());
            } else if (enableWorldSelection && perWorldEnabled) {
                World world = Bukkit.getWorld(target);
                if (world != null) {
                    targets = new ArrayList<>(world.getPlayers());
                } else {
                    plugin.getLogger().warning("World '" + target + "' not found for Discord announcement");
                    return false;
                }
            } else {
                targets = new ArrayList<>(Bukkit.getOnlinePlayers());
            }
            
            if (targets.isEmpty()) {
                plugin.getLogger().warning("No players online to send Discord announcement to");
                return false;
            }
            
            // Send announcement
            plugin.sendAnnouncement(targets, message, null);
            plugin.getLogger().info("Announcement sent from Discord (User ID: " + senderId + ") to " + targets.size() + " player(s)");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error sending Discord announcement: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Check if Discord member has permission to use command
     */
    private boolean hasPermission(Member member) {
        if (member == null) {
            return false;
        }
        
        boolean adminOnly = discordConfig.getBoolean("discord-commands.permissions.admin-only", true);
        if (!adminOnly) {
            return true;
        }
        
        String userId = member.getId();
        
        // Check user IDs
        List<String> allowedUserIds = discordConfig.getStringList("discord-commands.permissions.allowed-user-ids");
        if (allowedUserIds != null && allowedUserIds.contains(userId)) {
            return true;
        }
        
        // Check role IDs
        List<String> allowedRoleIds = discordConfig.getStringList("discord-commands.permissions.allowed-role-ids");
        if (allowedRoleIds != null && !allowedRoleIds.isEmpty()) {
            for (Role role : member.getRoles()) {
                if (allowedRoleIds.contains(role.getId())) {
                    return true;
                }
            }
        }
        
        // If no specific users or roles are configured, deny by default when admin-only is true
        return false;
    }
}
