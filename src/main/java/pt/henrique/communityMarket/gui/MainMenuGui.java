package pt.henrique.communityMarket.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pt.henrique.communityMarket.CommunityMarket;
import pt.henrique.communityMarket.util.ItemBuilder;
import pt.henrique.communityMarket.util.SoundUtil;
import pt.henrique.communityMarket.util.TextUtil;

import java.util.Map;

/**
 * The main menu GUI - the hub of the marketplace.
 * Contains buttons for all major features.
 */
public class MainMenuGui implements MarketGui {

    private final CommunityMarket plugin;
    private final GuiManager guiManager;
    private Inventory inventory;
    private Player player;

    // Slot positions for buttons
    private static final int SLOT_BROWSE_MARKET = 10;
    private static final int SLOT_BROWSE_AUCTIONS = 12;
    private static final int SLOT_CREATE_LISTING = 14;
    private static final int SLOT_CREATE_AUCTION = 16;
    private static final int SLOT_MY_LISTINGS = 28;
    private static final int SLOT_MY_AUCTIONS = 30;
    private static final int SLOT_CLAIM = 32;
    private static final int SLOT_EARNINGS = 34;
    private static final int SLOT_HELP = 40;
    private static final int SLOT_ADMIN = 44;

    public MainMenuGui(CommunityMarket plugin, GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    /**
     * Opens the main menu for a player
     */
    public void open(Player player) {
        this.player = player;

        String title = TextUtil.colorizeToString(
                plugin.getMessageManager().getRaw("gui-titles.main-menu"));
        inventory = Bukkit.createInventory(this, 54, title);

        // Fill background with glass panes
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(" ")
                .build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Build menu items asynchronously (to get counts from DB)
        buildMenuAsync();
    }

    private void buildMenuAsync() {
        // Get player stats for lore
        var listingCountFuture = plugin.getListingService().countPlayerListings(player.getUniqueId());
        var auctionCountFuture = plugin.getAuctionService().countPlayerAuctions(player.getUniqueId());
        var claimCountFuture = plugin.getClaimService().countPlayerClaimItems(player.getUniqueId());
        var earningsFuture = plugin.getEarningsService().getPendingEarnings(player.getUniqueId());

        // When all futures complete, build the GUI
        listingCountFuture.thenCombine(auctionCountFuture, (listingCount, auctionCount) ->
            claimCountFuture.thenCombine(earningsFuture, (claimCount, earnings) -> {
                // Run on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    buildMenu(listingCount, auctionCount, claimCount, earnings);
                    player.openInventory(inventory);
                    guiManager.registerGui(player.getUniqueId(), this);
                    playSound(player, plugin.getConfigManager().getClickSound());
                });
                return null;
            })
        );
    }

    private void buildMenu(int listingCount, int auctionCount, int claimCount, double earnings) {
        var msgManager = plugin.getMessageManager();
        var configManager = plugin.getConfigManager();

        // Browse Market
        inventory.setItem(SLOT_BROWSE_MARKET, new ItemBuilder(Material.CHEST)
                .name(msgManager.getButton("browse-market"))
                .lore(msgManager.getLore("browse-market"))
                .build());

        // Browse Auctions
        inventory.setItem(SLOT_BROWSE_AUCTIONS, new ItemBuilder(Material.GOLD_INGOT)
                .name(msgManager.getButton("browse-auctions"))
                .lore(msgManager.getLore("browse-auctions"))
                .build());

        // Create Listing
        inventory.setItem(SLOT_CREATE_LISTING, new ItemBuilder(Material.WRITABLE_BOOK)
                .name(msgManager.getButton("create-listing"))
                .lore(msgManager.getLore("create-listing", Map.of(
                        "tax", String.valueOf(configManager.getMarketTax())
                )))
                .build());

        // Create Auction
        inventory.setItem(SLOT_CREATE_AUCTION, new ItemBuilder(Material.GOLDEN_HELMET)
                .name(msgManager.getButton("create-auction"))
                .lore(msgManager.getLore("create-auction", Map.of(
                        "tax", String.valueOf(configManager.getAuctionTax())
                )))
                .build());

        // My Listings
        inventory.setItem(SLOT_MY_LISTINGS, new ItemBuilder(Material.BOOK)
                .name(msgManager.getButton("my-listings"))
                .lore(msgManager.getLore("my-listings", Map.of(
                        "count", String.valueOf(listingCount),
                        "max", String.valueOf(configManager.getMaxListingsPerPlayer())
                )))
                .build());

        // My Auctions
        inventory.setItem(SLOT_MY_AUCTIONS, new ItemBuilder(Material.CLOCK)
                .name(msgManager.getButton("my-auctions"))
                .lore(msgManager.getLore("my-auctions", Map.of(
                        "count", String.valueOf(auctionCount),
                        "max", String.valueOf(configManager.getMaxAuctionsPerPlayer())
                )))
                .build());

        // Claim Items
        inventory.setItem(SLOT_CLAIM, new ItemBuilder(Material.ENDER_CHEST)
                .name(msgManager.getButton("claim-items"))
                .lore(msgManager.getLore("claim-items", Map.of(
                        "count", String.valueOf(claimCount)
                )))
                .glow(claimCount > 0) // Glow if there are items to claim
                .build());

        // Earnings
        inventory.setItem(SLOT_EARNINGS, new ItemBuilder(Material.EMERALD)
                .name(msgManager.getButton("earnings"))
                .lore(msgManager.getLore("earnings", Map.of(
                        "amount", msgManager.formatCurrency(earnings)
                )))
                .glow(earnings > 0) // Glow if there are earnings
                .build());

        // Help (only if enabled in config)
        if (configManager.isHelpButtonEnabled()) {
            inventory.setItem(SLOT_HELP, new ItemBuilder(Material.OAK_SIGN)
                    .name(msgManager.getButton("help"))
                    .lore(msgManager.getLore("help"))
                    .build());
        }
        // If help is disabled, the slot stays as glass pane (already filled)

        // Admin button (only if player has permission)
        if (player.hasPermission("communitymarket.admin")) {
            inventory.setItem(SLOT_ADMIN, new ItemBuilder(Material.COMMAND_BLOCK)
                    .name(msgManager.getButton("admin"))
                    .lore(msgManager.getLore("admin"))
                    .build());
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        switch (slot) {
            case SLOT_BROWSE_MARKET -> {
                if (player.hasPermission("communitymarket.buy")) {
                    guiManager.openBrowseMarket(player, 0);
                } else {
                    player.sendMessage(plugin.getMessageManager().getPrefixed("messages.no-permission"));
                    playSound(player, plugin.getConfigManager().getErrorSound());
                }
            }
            case SLOT_BROWSE_AUCTIONS -> {
                if (player.hasPermission("communitymarket.bid")) {
                    guiManager.openBrowseAuctions(player, 0);
                } else {
                    player.sendMessage(plugin.getMessageManager().getPrefixed("messages.no-permission"));
                    playSound(player, plugin.getConfigManager().getErrorSound());
                }
            }
            case SLOT_CREATE_LISTING -> {
                if (player.hasPermission("communitymarket.sell")) {
                    guiManager.openCreateListing(player);
                } else {
                    player.sendMessage(plugin.getMessageManager().getPrefixed("messages.no-permission"));
                    playSound(player, plugin.getConfigManager().getErrorSound());
                }
            }
            case SLOT_CREATE_AUCTION -> {
                if (player.hasPermission("communitymarket.auction")) {
                    guiManager.openCreateAuction(player);
                } else {
                    player.sendMessage(plugin.getMessageManager().getPrefixed("messages.no-permission"));
                    playSound(player, plugin.getConfigManager().getErrorSound());
                }
            }
            case SLOT_MY_LISTINGS -> guiManager.openMyListings(player);
            case SLOT_MY_AUCTIONS -> guiManager.openMyAuctions(player);
            case SLOT_CLAIM -> {
                if (player.hasPermission("communitymarket.claim")) {
                    guiManager.openClaim(player);
                } else {
                    player.sendMessage(plugin.getMessageManager().getPrefixed("messages.no-permission"));
                    playSound(player, plugin.getConfigManager().getErrorSound());
                }
            }
            case SLOT_EARNINGS -> {
                if (player.hasPermission("communitymarket.withdraw")) {
                    guiManager.openEarnings(player);
                } else {
                    player.sendMessage(plugin.getMessageManager().getPrefixed("messages.no-permission"));
                    playSound(player, plugin.getConfigManager().getErrorSound());
                }
            }
            case SLOT_HELP -> {
                if (plugin.getConfigManager().isHelpButtonEnabled()) {
                    guiManager.openHelp(player);
                }
            }
            case SLOT_ADMIN -> {
                if (player.hasPermission("communitymarket.admin")) {
                    guiManager.openAdmin(player);
                }
            }
        }
    }

    private void playSound(Player player, String soundName) {
        SoundUtil.playSound(player, soundName);
    }

    @Override
    public GuiType getType() {
        return GuiType.MAIN_MENU;
    }

    @Override
    public @org.jetbrains.annotations.NotNull Inventory getInventory() {
        return inventory;
    }
}

