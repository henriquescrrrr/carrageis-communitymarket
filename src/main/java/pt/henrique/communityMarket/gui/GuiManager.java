package pt.henrique.communityMarket.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import pt.henrique.communityMarket.CommunityMarket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages all GUI instances and player GUI states.
 * Central hub for opening, closing, and tracking GUI interactions.
 */
public class GuiManager {

    private final CommunityMarket plugin;

    // Track open GUIs per player
    private final Map<UUID, MarketGui> openGuis = new HashMap<>();

    // GUI builders/templates
    private final MainMenuGui mainMenuGui;
    private final BrowseMarketGui browseMarketGui;
    private final BrowseAuctionsGui browseAuctionsGui;
    private final CreateListingGui createListingGui;
    private final CreateAuctionGui createAuctionGui;
    private final MyListingsGui myListingsGui;
    private final MyAuctionsGui myAuctionsGui;
    private final ClaimGui claimGui;
    private final EarningsGui earningsGui;
    private final HelpGui helpGui;
    private final AdminGui adminGui;

    public GuiManager(CommunityMarket plugin) {
        this.plugin = plugin;

        // Initialize GUI handlers
        this.mainMenuGui = new MainMenuGui(plugin, this);
        this.browseMarketGui = new BrowseMarketGui(plugin, this);
        this.browseAuctionsGui = new BrowseAuctionsGui(plugin, this);
        this.createListingGui = new CreateListingGui(plugin, this);
        this.createAuctionGui = new CreateAuctionGui(plugin, this);
        this.myListingsGui = new MyListingsGui(plugin, this);
        this.myAuctionsGui = new MyAuctionsGui(plugin, this);
        this.claimGui = new ClaimGui(plugin, this);
        this.earningsGui = new EarningsGui(plugin, this);
        this.helpGui = new HelpGui(plugin, this);
        this.adminGui = new AdminGui(plugin, this);
    }

    /**
     * Opens the main menu for a player
     */
    public void openMainMenu(Player player) {
        mainMenuGui.open(player);
    }

    /**
     * Opens the browse market GUI
     */
    public void openBrowseMarket(Player player, int page) {
        browseMarketGui.open(player, page);
    }

    /**
     * Opens the browse auctions GUI
     */
    public void openBrowseAuctions(Player player, int page) {
        browseAuctionsGui.open(player, page);
    }

    /**
     * Opens the create listing flow (starts with item selection)
     */
    public void openCreateListing(Player player) {
        // Open item selection first, which will then open CreateListingGui
        new ItemSelectionGui(plugin, this, ItemSelectionGui.SelectionMode.LISTING).open(player);
    }

    /**
     * Opens the create auction flow (starts with item selection)
     */
    public void openCreateAuction(Player player) {
        // Open item selection first, which will then open CreateAuctionGui
        new ItemSelectionGui(plugin, this, ItemSelectionGui.SelectionMode.AUCTION).open(player);
    }

    /**
     * Opens the player's listings GUI
     */
    public void openMyListings(Player player) {
        myListingsGui.open(player);
    }

    /**
     * Opens the player's auctions GUI
     */
    public void openMyAuctions(Player player) {
        myAuctionsGui.open(player);
    }

    /**
     * Opens the claim GUI
     */
    public void openClaim(Player player) {
        claimGui.open(player);
    }

    /**
     * Opens the earnings GUI
     */
    public void openEarnings(Player player) {
        earningsGui.open(player);
    }

    /**
     * Opens the help GUI
     */
    public void openHelp(Player player) {
        helpGui.open(player);
    }

    /**
     * Opens the admin panel
     */
    public void openAdmin(Player player) {
        adminGui.open(player);
    }

    /**
     * Opens a number input GUI
     */
    public void openNumberInput(Player player, NumberInputGui.NumberInputCallback callback,
                                 double currentValue, double minValue, double maxValue, String title) {
        new NumberInputGui(plugin, this, callback, currentValue, minValue, maxValue, title).open(player);
    }

    /**
     * Opens a confirmation GUI
     */
    public void openConfirmation(Player player, ConfirmationGui.ConfirmCallback callback,
                                  String title, String... infoLines) {
        new ConfirmationGui(plugin, this, callback, title, infoLines).open(player);
    }

    /**
     * Registers an open GUI for a player
     */
    public void registerGui(UUID playerUuid, MarketGui gui) {
        openGuis.put(playerUuid, gui);
    }

    /**
     * Unregisters a GUI when closed
     */
    public void unregisterGui(UUID playerUuid) {
        openGuis.remove(playerUuid);
    }

    /**
     * Gets the currently open GUI for a player
     */
    public MarketGui getOpenGui(UUID playerUuid) {
        return openGuis.get(playerUuid);
    }

    /**
     * Checks if a player has a market GUI open
     */
    public boolean hasGuiOpen(UUID playerUuid) {
        return openGuis.containsKey(playerUuid);
    }

    /**
     * Closes all open market GUIs (used on plugin disable)
     */
    public void closeAllGuis() {
        for (UUID uuid : openGuis.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        openGuis.clear();
    }

    /**
     * Gets the plugin instance
     */
    public CommunityMarket getPlugin() {
        return plugin;
    }

    // Getters for GUI handlers
    public MainMenuGui getMainMenuGui() { return mainMenuGui; }
    public BrowseMarketGui getBrowseMarketGui() { return browseMarketGui; }
    public BrowseAuctionsGui getBrowseAuctionsGui() { return browseAuctionsGui; }
    public CreateListingGui getCreateListingGui() { return createListingGui; }
    public CreateAuctionGui getCreateAuctionGui() { return createAuctionGui; }
    public MyListingsGui getMyListingsGui() { return myListingsGui; }
    public MyAuctionsGui getMyAuctionsGui() { return myAuctionsGui; }
    public ClaimGui getClaimGui() { return claimGui; }
    public EarningsGui getEarningsGui() { return earningsGui; }
    public HelpGui getHelpGui() { return helpGui; }
    public AdminGui getAdminGui() { return adminGui; }
}

