package pt.henrique.communityMarket.task;

import org.bukkit.scheduler.BukkitRunnable;
import pt.henrique.communityMarket.CommunityMarket;

/**
 * Periodic task that checks for ended auctions and processes them.
 * Runs asynchronously to avoid blocking the main thread.
 */
public class AuctionTask extends BukkitRunnable {

    private final CommunityMarket plugin;

    public AuctionTask(CommunityMarket plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Process ended auctions
        plugin.getAuctionService().processEndedAuctions()
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Error processing ended auctions: " + ex.getMessage());
                    return null;
                });
    }
}

