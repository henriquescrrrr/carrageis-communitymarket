package pt.henrique.communityMarket.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pt.henrique.communityMarket.CommunityMarket;

/**
 * Listener for player join/quit events.
 * Notifies players of pending items and earnings on join.
 */
public class PlayerListener implements Listener {

    private final CommunityMarket plugin;

    public PlayerListener(CommunityMarket plugin) {
        this.plugin = plugin;
    }

    /**
     * Notifies players of pending claims and earnings when they join.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Delay notification slightly to ensure player is fully loaded
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            notifyPendingItems(player);
        }, 40L); // 2 second delay
    }

    /**
     * Cleans up when a player quits.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Close any open market GUI to prevent issues
        if (plugin.getGuiManager().hasGuiOpen(player.getUniqueId())) {
            player.closeInventory();
        }
    }

    /**
     * Notifies a player about pending claims and earnings.
     */
    private void notifyPendingItems(Player player) {
        var msgManager = plugin.getMessageManager();

        // Check for pending claim items
        plugin.getClaimService().countPlayerClaimItems(player.getUniqueId())
                .thenAccept(claimCount -> {
                    if (claimCount > 0) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                player.sendMessage(msgManager.getPrefixed("messages.claim-items",
                                        java.util.Map.of("count", String.valueOf(claimCount))));
                            }
                        });
                    }
                });

        // Check for pending earnings
        plugin.getEarningsService().getPendingEarnings(player.getUniqueId())
                .thenAccept(earnings -> {
                    if (earnings > 0) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                player.sendMessage(msgManager.getPrefixed("messages.earnings-balance",
                                        "amount", msgManager.formatCurrency(earnings)));
                            }
                        });
                    }
                });
    }
}

