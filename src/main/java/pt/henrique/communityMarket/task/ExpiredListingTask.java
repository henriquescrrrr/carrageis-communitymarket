package pt.henrique.communityMarket.task;

import org.bukkit.scheduler.BukkitRunnable;
import pt.henrique.communityMarket.CommunityMarket;

/**
 * Periodic task that checks for expired listings and moves items to claim storage.
 * Runs asynchronously to avoid blocking the main thread.
 */
public class ExpiredListingTask extends BukkitRunnable {

    private final CommunityMarket plugin;

    public ExpiredListingTask(CommunityMarket plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Process expired listings
        plugin.getListingService().processExpiredListings()
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Error processing expired listings: " + ex.getMessage());
                    return null;
                });
    }
}

