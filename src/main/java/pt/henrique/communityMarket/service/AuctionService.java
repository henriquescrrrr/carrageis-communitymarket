package pt.henrique.communityMarket.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pt.henrique.communityMarket.CommunityMarket;
import pt.henrique.communityMarket.model.Auction;
import pt.henrique.communityMarket.model.Bid;
import pt.henrique.communityMarket.model.ClaimItem;
import pt.henrique.communityMarket.model.PendingEarnings;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service layer for managing auctions.
 * Handles creation, bidding, buyout, and auction end processing.
 */
public class AuctionService {

    private final CommunityMarket plugin;

    // Simple cache for active auctions
    private List<Auction> cachedAuctions;
    private long cacheExpiry = 0;

    // Track pending operations to prevent race conditions
    private final Map<Integer, Boolean> pendingBids = new ConcurrentHashMap<>();

    public AuctionService(CommunityMarket plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates a new auction
     *
     * @param player        The seller
     * @param item          The item to auction
     * @param startPrice    Starting bid price
     * @param buyoutPrice   Optional buyout price (null for no buyout)
     * @param durationHours Duration in hours
     * @return CompletableFuture with the auction ID or -1 if failed
     */
    public CompletableFuture<Integer> createAuction(Player player, ItemStack item, double startPrice,
                                                     Double buyoutPrice, int durationHours) {
        // Validate
        if (item == null || item.getType().isAir()) {
            return CompletableFuture.completedFuture(-1);
        }

        if (plugin.getConfigManager().isMaterialBlacklisted(item.getType())) {
            return CompletableFuture.completedFuture(-1);
        }

        // Create auction object
        Auction auction = new Auction(
                player.getUniqueId(),
                player.getName(),
                item.clone(),
                startPrice,
                buyoutPrice,
                Instant.now().plusSeconds(durationHours * 3600L)
        );

        // Save to database
        return plugin.getDatabaseManager().createAuction(auction)
                .thenApply(id -> {
                    if (id > 0) {
                        invalidateCache();
                    }
                    return id;
                });
    }

    /**
     * Gets all active auctions with caching
     */
    public CompletableFuture<List<Auction>> getActiveAuctions() {
        long now = System.currentTimeMillis();

        if (cachedAuctions != null && now < cacheExpiry) {
            return CompletableFuture.completedFuture(cachedAuctions);
        }

        return plugin.getDatabaseManager().getActiveAuctions()
                .thenApply(auctions -> {
                    cachedAuctions = auctions;
                    cacheExpiry = System.currentTimeMillis() +
                            (plugin.getConfigManager().getCacheDuration() * 1000L);
                    return auctions;
                });
    }

    /**
     * Gets a specific auction by ID
     */
    public CompletableFuture<Optional<Auction>> getAuction(int id) {
        return plugin.getDatabaseManager().getAuction(id);
    }

    /**
     * Gets all auctions for a specific player
     */
    public CompletableFuture<List<Auction>> getPlayerAuctions(UUID playerUuid) {
        return plugin.getDatabaseManager().getPlayerAuctions(playerUuid);
    }

    /**
     * Counts active auctions for a player
     */
    public CompletableFuture<Integer> countPlayerAuctions(UUID playerUuid) {
        return plugin.getDatabaseManager().countPlayerAuctions(playerUuid);
    }

    /**
     * Checks if a player can create a new auction (not at limit)
     */
    public CompletableFuture<Boolean> canCreateAuction(UUID playerUuid) {
        int maxAuctions = plugin.getConfigManager().getMaxAuctionsPerPlayer();

        return countPlayerAuctions(playerUuid)
                .thenApply(count -> count < maxAuctions);
    }

    /**
     * Calculates the minimum bid for an auction
     *
     * @param auction The auction
     * @return Minimum bid amount
     */
    public double calculateMinBid(Auction auction) {
        return auction.getMinimumBid(
                plugin.getConfigManager().getMinBidIncrementPercent(),
                plugin.getConfigManager().getMinBidIncrementAbsolute()
        );
    }

    /**
     * Places a bid on an auction
     *
     * @param auctionId The auction to bid on
     * @param bidder    The bidder
     * @param amount    The bid amount
     * @return CompletableFuture with bid result
     */
    public CompletableFuture<BidResult> placeBid(int auctionId, Player bidder, double amount) {
        // Check if already processing this auction
        if (pendingBids.putIfAbsent(auctionId, true) != null) {
            return CompletableFuture.completedFuture(BidResult.ALREADY_PROCESSING);
        }

        try {
            return getAuction(auctionId)
                    .thenCompose(optAuction -> {
                        if (optAuction.isEmpty()) {
                            pendingBids.remove(auctionId);
                            return CompletableFuture.completedFuture(BidResult.NOT_FOUND);
                        }

                        Auction auction = optAuction.get();

                        // Can't bid on own auction
                        if (auction.getSellerUuid().equals(bidder.getUniqueId())) {
                            pendingBids.remove(auctionId);
                            return CompletableFuture.completedFuture(BidResult.OWN_AUCTION);
                        }

                        // Check bid amount
                        double minBid = calculateMinBid(auction);
                        if (amount < minBid) {
                            pendingBids.remove(auctionId);
                            return CompletableFuture.completedFuture(BidResult.BID_TOO_LOW);
                        }

                        // Check bidder funds
                        if (!plugin.getEconomyManager().has(bidder.getUniqueId(), amount)) {
                            pendingBids.remove(auctionId);
                            return CompletableFuture.completedFuture(BidResult.INSUFFICIENT_FUNDS);
                        }

                        // Store previous bidder info for refund
                        UUID previousBidder = auction.getHighestBidderUuid();
                        double previousBid = auction.getCurrentBid();

                        // Withdraw from bidder first
                        if (!plugin.getEconomyManager().withdraw(bidder.getUniqueId(), amount)) {
                            pendingBids.remove(auctionId);
                            return CompletableFuture.completedFuture(BidResult.ECONOMY_ERROR);
                        }

                        // Place bid in database
                        return plugin.getDatabaseManager().placeBid(auctionId, bidder.getUniqueId(), bidder.getName(), amount)
                                .thenApply(success -> {
                                    if (!success) {
                                        // Refund bidder
                                        plugin.getEconomyManager().deposit(bidder.getUniqueId(), amount);
                                        pendingBids.remove(auctionId);
                                        return BidResult.AUCTION_ENDED;
                                    }

                                    // Refund previous bidder
                                    if (previousBidder != null && previousBid > 0) {
                                        plugin.getEconomyManager().deposit(previousBidder, previousBid);

                                        // Notify previous bidder if online
                                        if (plugin.getConfigManager().isNotifyOnOutbid()) {
                                            Player prevBidderPlayer = Bukkit.getPlayer(previousBidder);
                                            if (prevBidderPlayer != null && prevBidderPlayer.isOnline()) {
                                                Map<String, String> placeholders = Map.of(
                                                        "item", auction.getItem().getType().name(),
                                                        "amount", plugin.getMessageManager().formatCurrency(amount),
                                                        "bidder", bidder.getName()
                                                );
                                                prevBidderPlayer.sendMessage(plugin.getMessageManager()
                                                        .getPrefixed("messages.auction-outbid", placeholders));
                                            }
                                        }
                                    }

                                    invalidateCache();
                                    pendingBids.remove(auctionId);
                                    return BidResult.SUCCESS;
                                });
                    });
        } catch (Exception e) {
            pendingBids.remove(auctionId);
            throw e;
        }
    }

    /**
     * Buys out an auction immediately
     *
     * @param auctionId The auction to buyout
     * @param buyer     The buyer
     * @return CompletableFuture with buyout result
     */
    public CompletableFuture<BidResult> buyout(int auctionId, Player buyer) {
        return getAuction(auctionId)
                .thenCompose(optAuction -> {
                    if (optAuction.isEmpty()) {
                        return CompletableFuture.completedFuture(BidResult.NOT_FOUND);
                    }

                    Auction auction = optAuction.get();

                    // Check if buyout is available
                    if (!auction.hasBuyout()) {
                        return CompletableFuture.completedFuture(BidResult.NO_BUYOUT);
                    }

                    // Can't buyout own auction
                    if (auction.getSellerUuid().equals(buyer.getUniqueId())) {
                        return CompletableFuture.completedFuture(BidResult.OWN_AUCTION);
                    }

                    // Check buyer funds
                    double buyoutPrice = auction.getBuyoutPrice();
                    if (!plugin.getEconomyManager().has(buyer.getUniqueId(), buyoutPrice)) {
                        return CompletableFuture.completedFuture(BidResult.INSUFFICIENT_FUNDS);
                    }

                    // Place bid at buyout price (this will end the auction)
                    return placeBid(auctionId, buyer, buyoutPrice)
                            .thenCompose(result -> {
                                if (result == BidResult.SUCCESS) {
                                    // Immediately end the auction
                                    return processAuctionEnd(auctionId)
                                            .thenApply(v -> BidResult.BUYOUT_SUCCESS);
                                }
                                return CompletableFuture.completedFuture(result);
                            });
                });
    }

    /**
     * Cancels an auction (only if no bids)
     *
     * @param auctionId  The auction to cancel
     * @param playerUuid The player attempting to cancel
     * @param isAdmin    Whether this is an admin action
     * @return CompletableFuture with success status
     */
    public CompletableFuture<CancelResult> cancelAuction(int auctionId, UUID playerUuid, boolean isAdmin) {
        return getAuction(auctionId)
                .thenCompose(optAuction -> {
                    if (optAuction.isEmpty()) {
                        return CompletableFuture.completedFuture(CancelResult.NOT_FOUND);
                    }

                    Auction auction = optAuction.get();

                    // Check permission
                    if (!isAdmin && !auction.getSellerUuid().equals(playerUuid)) {
                        return CompletableFuture.completedFuture(CancelResult.NOT_OWNER);
                    }

                    // Can only cancel if no bids (unless admin)
                    if (!isAdmin && auction.getBidCount() > 0) {
                        return CompletableFuture.completedFuture(CancelResult.HAS_BIDS);
                    }

                    // Atomically flip to CANCELLED only if still ACTIVE. This guarantees the
                    // refund + item return below run exactly once and never race with the
                    // periodic end-processing task (which would otherwise duplicate the item
                    // or hand the highest bidder both the item and a refund).
                    return plugin.getDatabaseManager().updateAuctionStatusIfActive(auctionId, Auction.AuctionStatus.CANCELLED)
                            .thenApply(success -> {
                                if (!success) {
                                    return CancelResult.FAILED;
                                }

                                // If admin cancelling with bids, refund highest bidder
                                if (isAdmin && auction.getBidCount() > 0 && auction.getHighestBidderUuid() != null) {
                                    plugin.getEconomyManager().deposit(auction.getHighestBidderUuid(), auction.getCurrentBid());
                                }

                                // Return item to claim storage
                                ClaimItem claimItem = new ClaimItem(
                                        auction.getSellerUuid(),
                                        auction.getItem().clone(),
                                        ClaimItem.ClaimReason.CANCELLED_AUCTION,
                                        "Auction #" + auctionId
                                );
                                plugin.getDatabaseManager().addClaimItem(claimItem);
                                invalidateCache();
                                return CancelResult.SUCCESS;
                            });
                });
    }

    /**
     * Processes ended auctions - delivers items and handles payments
     */
    public CompletableFuture<Void> processEndedAuctions() {
        return plugin.getDatabaseManager().getEndedAuctions()
                .thenAccept(auctions -> {
                    for (Auction auction : auctions) {
                        processAuctionEnd(auction.getId());
                    }
                    if (!auctions.isEmpty()) {
                        invalidateCache();
                    }
                });
    }

    /**
     * Processes a single auction end
     */
    private CompletableFuture<Void> processAuctionEnd(int auctionId) {
        return getAuction(auctionId)
                .thenCompose(optAuction -> {
                    if (optAuction.isEmpty()) return CompletableFuture.<Void>completedFuture(null);

                    Auction auction = optAuction.get();

                    // Determine terminal status
                    Auction.AuctionStatus newStatus = auction.getBidCount() > 0
                            ? Auction.AuctionStatus.SOLD
                            : Auction.AuctionStatus.EXPIRED;

                    // Atomically claim the auction end: only the caller that flips the row
                    // away from ACTIVE proceeds to credit earnings / deliver the item. This
                    // makes end-processing idempotent and prevents duplication when the
                    // periodic task and a buyout (or two task runs) race on the same auction.
                    return plugin.getDatabaseManager().updateAuctionStatusIfActive(auctionId, newStatus)
                            .thenAccept(claimed -> {
                    if (!claimed) return;

                    if (auction.getBidCount() > 0 && auction.getHighestBidderUuid() != null) {
                        // Auction has a winner
                        double winningBid = auction.getCurrentBid();
                        double tax = plugin.getConfigManager().getAuctionTax();
                        double sellerEarnings = winningBid * (1 - tax / 100);

                        // Add pending earnings for seller
                        PendingEarnings earnings = new PendingEarnings(
                                auction.getSellerUuid(),
                                sellerEarnings,
                                "Auction #" + auctionId
                        );
                        plugin.getDatabaseManager().addPendingEarnings(earnings);

                        // Add item to winner's claim storage
                        ClaimItem claimItem = new ClaimItem(
                                auction.getHighestBidderUuid(),
                                auction.getItem().clone(),
                                ClaimItem.ClaimReason.WON_AUCTION,
                                "Auction #" + auctionId
                        );
                        plugin.getDatabaseManager().addClaimItem(claimItem);

                        // Notify winner if online
                        if (plugin.getConfigManager().isNotifyOnWin()) {
                            Player winner = Bukkit.getPlayer(auction.getHighestBidderUuid());
                            if (winner != null && winner.isOnline()) {
                                Map<String, String> placeholders = Map.of(
                                        "item", auction.getItem().getType().name(),
                                        "price", plugin.getMessageManager().formatCurrency(winningBid)
                                );
                                winner.sendMessage(plugin.getMessageManager()
                                        .getPrefixed("messages.auction-ended-winner", placeholders));
                            }
                        }

                        // Notify seller if online
                        if (plugin.getConfigManager().isNotifyOnSale()) {
                            Player seller = Bukkit.getPlayer(auction.getSellerUuid());
                            if (seller != null && seller.isOnline()) {
                                Map<String, String> placeholders = Map.of(
                                        "item", auction.getItem().getType().name(),
                                        "winner", auction.getHighestBidderName(),
                                        "price", plugin.getMessageManager().formatCurrency(sellerEarnings)
                                );
                                seller.sendMessage(plugin.getMessageManager()
                                        .getPrefixed("messages.auction-ended-seller", placeholders));
                            }
                        }
                    } else {
                        // No bids - return item to seller
                        ClaimItem claimItem = new ClaimItem(
                                auction.getSellerUuid(),
                                auction.getItem().clone(),
                                ClaimItem.ClaimReason.AUCTION_NO_BIDS,
                                "Auction #" + auctionId
                        );
                        plugin.getDatabaseManager().addClaimItem(claimItem);

                        // Notify seller if online
                        if (plugin.getConfigManager().isNotifyOnExpire()) {
                            Player seller = Bukkit.getPlayer(auction.getSellerUuid());
                            if (seller != null && seller.isOnline()) {
                                seller.sendMessage(plugin.getMessageManager().getPrefixed(
                                        "messages.auction-ended-no-bids",
                                        "item", auction.getItem().getType().name()
                                ));
                            }
                        }
                    }
                            });
                });
    }

    /**
     * Invalidates the auction cache
     */
    public void invalidateCache() {
        cachedAuctions = null;
        cacheExpiry = 0;
    }

    /**
     * Result of a bid attempt
     */
    public enum BidResult {
        SUCCESS,
        BUYOUT_SUCCESS,
        NOT_FOUND,
        AUCTION_ENDED,
        OWN_AUCTION,
        BID_TOO_LOW,
        INSUFFICIENT_FUNDS,
        ECONOMY_ERROR,
        ALREADY_PROCESSING,
        NO_BUYOUT
    }

    /**
     * Result of a cancel attempt
     */
    public enum CancelResult {
        SUCCESS,
        NOT_FOUND,
        NOT_OWNER,
        HAS_BIDS,
        FAILED
    }
}

