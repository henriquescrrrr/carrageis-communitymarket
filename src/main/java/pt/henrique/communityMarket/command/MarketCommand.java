package pt.henrique.communityMarket.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pt.henrique.communityMarket.CommunityMarket;

import java.util.Collections;
import java.util.List;

/**
 * The only command in the plugin: /market (alias: /cmarket)
 * Opens the main market GUI. All other actions are done through GUIs.
 */
public class MarketCommand implements CommandExecutor, TabCompleter {

    private final CommunityMarket plugin;

    public MarketCommand(CommunityMarket plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // Only players can use this command
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().get("messages.player-only"));
            return true;
        }

        // Check basic permission
        if (!player.hasPermission("communitymarket.use")) {
            player.sendMessage(plugin.getMessageManager().getPrefixed("messages.no-permission"));
            return true;
        }

        // Open the main market GUI
        plugin.getGuiManager().openMainMenu(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        // No tab completions - everything is GUI-based
        return Collections.emptyList();
    }
}

