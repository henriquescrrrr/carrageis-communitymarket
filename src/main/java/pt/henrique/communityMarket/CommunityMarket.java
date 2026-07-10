package pt.henrique.communityMarket;

import org.bukkit.plugin.java.JavaPlugin;
import pt.henrique.communityMarket.command.MarketCommand;
import pt.henrique.communityMarket.config.ConfigManager;
import pt.henrique.communityMarket.config.MessageManager;
import pt.henrique.communityMarket.db.DatabaseManager;
import pt.henrique.communityMarket.economy.EconomyManager;
import pt.henrique.communityMarket.gui.GuiManager;
import pt.henrique.communityMarket.listener.GuiListener;
import pt.henrique.communityMarket.listener.PlayerListener;
import pt.henrique.communityMarket.service.*;
import pt.henrique.communityMarket.task.AuctionTask;
import pt.henrique.communityMarket.task.ExpiredListingTask;


public final class CommunityMarket extends JavaPlugin {

    private static CommunityMarket instance;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private GuiManager guiManager;

    private ListingService listingService;
    private AuctionService auctionService;
    private ClaimService claimService;
    private EarningsService earningsService;
    private TransactionService transactionService;

    private AuctionTask auctionTask;
    private ExpiredListingTask expiredListingTask;

    @Override
    public void onEnable() {
        instance = this;

        // Load configurations
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);

        // Initialize economy
        economyManager = new EconomyManager(this);
        if (!economyManager.setupEconomy()) {
            getLogger().severe("Disabling CommunityMarket due to missing economy provider.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize database
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("Failed to initialize database!");
            getLogger().severe("Disabling CommunityMarket...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Database initialized successfully.");

        // Initialize services
        claimService = new ClaimService(this);
        earningsService = new EarningsService(this);
        listingService = new ListingService(this);
        auctionService = new AuctionService(this);
        transactionService = new TransactionService(this);

        // Initialize GUI manager
        guiManager = new GuiManager(this);

        // Register command
        MarketCommand marketCommand = new MarketCommand(this);
        var command = getCommand("market");
        if (command != null) {
            command.setExecutor(marketCommand);
            command.setTabCompleter(marketCommand);
        }

        // Register listeners
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // Start tasks
        startTasks();

        getLogger().info("CommunityMarket has been enabled!");
    }

    @Override
    public void onDisable() {
        // Stop tasks
        if (auctionTask != null) {
            auctionTask.cancel();
        }
        if (expiredListingTask != null) {
            expiredListingTask.cancel();
        }

        // Close GUI manager
        if (guiManager != null) {
            guiManager.closeAllGuis();
        }

        // Close database
        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        getLogger().info("CommunityMarket has been disabled!");
    }

    private void startTasks() {
        // Auction check task
        int auctionInterval = configManager.getAuctionCheckInterval() * 20; // Convert to ticks
        auctionTask = new AuctionTask(this);
        auctionTask.runTaskTimerAsynchronously(this, auctionInterval, auctionInterval);

        // Expired listing check task
        int expiredInterval = configManager.getExpiredCheckInterval() * 60 * 20; // Convert minutes to ticks
        expiredListingTask = new ExpiredListingTask(this);
        expiredListingTask.runTaskTimerAsynchronously(this, expiredInterval, expiredInterval);
    }

    public void reload() {
        configManager.reload();
        messageManager.reload();
        getLogger().info("Configuration reloaded.");
    }

    public static CommunityMarket getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public ListingService getListingService() {
        return listingService;
    }

    public AuctionService getAuctionService() {
        return auctionService;
    }

    public ClaimService getClaimService() {
        return claimService;
    }

    public EarningsService getEarningsService() {
        return earningsService;
    }

    public TransactionService getTransactionService() {
        return transactionService;
    }
}
