package com.eclipsestudios.announcements.commands;

import com.eclipsestudios.announcements.EclipseStudiosAnnouncements;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class AnnouncementCommandHandler implements CommandExecutor, TabCompleter {

    private final EclipseStudiosAnnouncements plugin;

    public AnnouncementCommandHandler(EclipseStudiosAnnouncements plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        // Handle reload command
        if (cmd.equals("esareload")) {
            return handleReload(sender);
        }

        // Handle announcement commands
        if (cmd.equals("announcement") || cmd.equals("announce") || cmd.equals("ea")) {
            return handleAnnouncement(sender, args);
        }

        return false;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("esa.reload") && !sender.isOp()) {
            sender.sendMessage(color("&cYou don't have permission to use this command."));
            return true;
        }

        plugin.reloadConfigs();
        sender.sendMessage(color("&aEasy Announcements configs reloaded (config.yml + discord.yml)."));
        return true;
    }

    private boolean handleAnnouncement(CommandSender sender, String[] args) {
        // Check permission
        if (!sender.hasPermission("esa.announcement") && !sender.hasPermission("esa.announce") && !sender.isOp()) {
            sender.sendMessage(color("&cYou don't have permission to use this command."));
            return true;
        }

        if (args.length < 1) {
            showUsage(sender);
            return true;
        }

        boolean defaultToAll = plugin.getConfig().getBoolean("general.default-to-all", true);
        boolean perWorldEnabled = plugin.getConfig().getBoolean("per-world.enabled", false);
        
        Collection<Player> targets = new ArrayList<>();
        String message;
        Player senderPlayer = sender instanceof Player ? (Player) sender : null;

        // Parse arguments for target selection
        String firstArg = args[0];
        int messageStartIndex = 1;
        
        // Check for @a (all players)
        if (firstArg.equalsIgnoreCase("@a")) {
            if (args.length < 2) {
                sender.sendMessage(color("&cUsage: /announcement @a <message>"));
                return true;
            }
            targets = new ArrayList<>(Bukkit.getOnlinePlayers());
            message = String.join(" ", Arrays.copyOfRange(args, messageStartIndex, args.length));
            
        // Check for @w (world selector) - only if per-world is enabled
        } else if (firstArg.equalsIgnoreCase("@w") && perWorldEnabled) {
            if (args.length < 3) {
                sender.sendMessage(color("&cUsage: /announcement @w <world> <message>"));
                sender.sendMessage(color("&7Available worlds: " + getWorldNames()));
                return true;
            }
            
            String worldName = args[1];
            World world = Bukkit.getWorld(worldName);
            
            if (world == null) {
                sender.sendMessage(color("&cWorld '" + worldName + "' not found."));
                sender.sendMessage(color("&7Available worlds: " + getWorldNames()));
                return true;
            }
            
            // Check if world is allowed
            List<String> allowedWorlds = plugin.getConfig().getStringList("per-world.allowed-worlds");
            if (!allowedWorlds.isEmpty() && !allowedWorlds.contains(worldName)) {
                sender.sendMessage(color("&cAnnouncements are not allowed in world '" + worldName + "'."));
                return true;
            }
            
            targets = new ArrayList<>(world.getPlayers());
            message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            
        // Check if first argument is a player name
        } else {
            Player targetPlayer = Bukkit.getPlayer(firstArg);
            
            if (targetPlayer != null && targetPlayer.isOnline()) {
                // First argument is a player name
                if (args.length < 2) {
                    sender.sendMessage(color("&cUsage: /announcement " + firstArg + " <message>"));
                    return true;
                }
                targets.add(targetPlayer);
                message = String.join(" ", Arrays.copyOfRange(args, messageStartIndex, args.length));
                
            } else if (defaultToAll) {
                // First argument is not a player, and default-to-all is true
                // Treat entire args as message and send to all
                targets = new ArrayList<>(Bukkit.getOnlinePlayers());
                message = String.join(" ", args);
                
            } else {
                // First argument is not a valid player and default-to-all is false
                sender.sendMessage(color("&cPlayer '" + firstArg + "' not found."));
                showUsage(sender);
                return true;
            }
        }

        // Validate message length
        if (message.length() > 500) {
            sender.sendMessage(color("&cAnnouncement message too long (max 500 characters)."));
            return true;
        }

        if (targets.isEmpty()) {
            sender.sendMessage(color("&cNo players online to send announcement to."));
            return true;
        }

        // Send the announcement
        plugin.sendAnnouncement(targets, message, senderPlayer);

        // Confirm to sender
        sendConfirmation(sender, targets);

        return true;
    }
    
    /**
     * Show command usage
     */
    private void showUsage(CommandSender sender) {
        sender.sendMessage(color("&6&lEasy Announcements - Usage"));
        sender.sendMessage(color("&e/announcement @a <message> &7- Send to all players"));
        sender.sendMessage(color("&e/announcement <player> <message> &7- Send to specific player"));
        
        if (plugin.getConfig().getBoolean("per-world.enabled", false)) {
            sender.sendMessage(color("&e/announcement @w <world> <message> &7- Send to specific world"));
        }
        
        if (plugin.getConfig().getBoolean("general.default-to-all", true)) {
            sender.sendMessage(color("&e/announcement <message> &7- Send to all players (default)"));
        }
        
        sender.sendMessage(color("&7Examples:"));
        sender.sendMessage(color("&7  /announcement @a Server restart in 5 minutes!"));
        sender.sendMessage(color("&7  /announcement Steve Welcome to the server!"));
        
        if (plugin.getConfig().getBoolean("per-world.enabled", false)) {
            sender.sendMessage(color("&7  /announcement @w world Event starting in spawn!"));
        }
    }
    
    /**
     * Send confirmation message to sender
     */
    private void sendConfirmation(CommandSender sender, Collection<Player> targets) {
        if (targets.size() == Bukkit.getOnlinePlayers().size()) {
            sender.sendMessage(color("&a✓ Announcement sent to all players (" + targets.size() + ")"));
        } else if (targets.size() == 1) {
            Player target = targets.iterator().next();
            sender.sendMessage(color("&a✓ Announcement sent to: " + target.getName()));
        } else {
            // Check if it's a world announcement
            if (plugin.getConfig().getBoolean("per-world.enabled", false)) {
                // Find common world
                World commonWorld = null;
                for (Player p : targets) {
                    if (commonWorld == null) {
                        commonWorld = p.getWorld();
                    } else if (!p.getWorld().equals(commonWorld)) {
                        commonWorld = null;
                        break;
                    }
                }
                
                if (commonWorld != null) {
                    sender.sendMessage(color("&a✓ Announcement sent to " + targets.size() + " player(s) in world: " + commonWorld.getName()));
                    return;
                }
            }
            
            // Default: list players if reasonable count
            if (targets.size() <= 5) {
                String targetNames = targets.stream()
                        .map(Player::getName)
                        .collect(Collectors.joining(", "));
                sender.sendMessage(color("&a✓ Announcement sent to: " + targetNames));
            } else {
                sender.sendMessage(color("&a✓ Announcement sent to " + targets.size() + " player(s)"));
            }
        }
    }
    
    /**
     * Get comma-separated list of world names
     */
    private String getWorldNames() {
        return Bukkit.getWorlds().stream()
                .map(World::getName)
                .collect(Collectors.joining(", "));
    }

    private Component color(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        
        // No tab completion for reload
        if (cmd.equals("esareload")) {
            return Collections.emptyList();
        }

        // Tab completion for announcement/announce/ea
        if (cmd.equals("announcement") || cmd.equals("announce") || cmd.equals("ea")) {
            if (args.length == 1) {
                // First argument: suggest @a, @w (if per-world enabled), and player names
                List<String> suggestions = new ArrayList<>();
                suggestions.add("@a");
                
                if (plugin.getConfig().getBoolean("per-world.enabled", false)) {
                    suggestions.add("@w");
                }
                
                String input = args[0].toLowerCase(Locale.ROOT);
                
                // Add online players
                suggestions.addAll(
                    Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                        .collect(Collectors.toList())
                );
                
                return suggestions.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(input))
                    .sorted()
                    .collect(Collectors.toList());
                    
            } else if (args.length == 2 && args[0].equalsIgnoreCase("@w")) {
                // Second argument after @w: suggest world names
                String input = args[1].toLowerCase(Locale.ROOT);
                
                List<String> allowedWorlds = plugin.getConfig().getStringList("per-world.allowed-worlds");
                
                if (allowedWorlds.isEmpty()) {
                    // No restriction, show all worlds
                    return Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                        .sorted()
                        .collect(Collectors.toList());
                } else {
                    // Only show allowed worlds
                    return allowedWorlds.stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                        .sorted()
                        .collect(Collectors.toList());
                }
            }
        }

        return Collections.emptyList();
    }
}
