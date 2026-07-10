package pt.henrique.communityMarket.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import pt.henrique.communityMarket.CommunityMarket;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Manages economy operations using Vault (preferred) or EssentialsX (fallback).
 * <p>
 * Priority order:
 * 1. Vault with a registered economy provider
 * 2. EssentialsX economy (direct integration)
 * <p>
 * All economy operations can be called from any thread, but the actual
 * economy API calls are dispatched to the main thread for safety.
 */
public class EconomyManager {

    private final CommunityMarket plugin;
    private EconomyAdapter adapter;
    private String providerName = "None";

    public EconomyManager(CommunityMarket plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempts to set up an economy provider.
     * Tries Vault first, then EssentialsX as fallback.
     *
     * @return true if an economy provider was found
     */
    public boolean setupEconomy() {
        plugin.getLogger().info("=== Economy Detection ===");

        // Step 1: Check for Vault
        boolean vaultPresent = Bukkit.getPluginManager().getPlugin("Vault") != null;
        plugin.getLogger().info("Vault detected: " + (vaultPresent ? "YES" : "NO"));

        if (vaultPresent) {
            if (setupVaultEconomy()) {
                plugin.getLogger().info("=== Economy Setup Complete ===");
                return true;
            }
            // Vault found but no provider - log and continue to fallback
            plugin.getLogger().warning("Vault is installed but no economy provider is registered.");
            plugin.getLogger().warning("This usually means you have Vault but no economy plugin (EssentialsX, CMI, etc).");
        }

        // Step 2: Try EssentialsX fallback
        boolean essentialsPresent = Bukkit.getPluginManager().getPlugin("Essentials") != null;
        plugin.getLogger().info("Essentials detected: " + (essentialsPresent ? "YES" : "NO"));

        if (essentialsPresent) {
            if (setupEssentialsEconomy()) {
                plugin.getLogger().info("=== Economy Setup Complete ===");
                return true;
            }
        }

        // Step 3: No economy available
        plugin.getLogger().severe("=== Economy Setup FAILED ===");
        plugin.getLogger().severe("No compatible economy provider found!");
        plugin.getLogger().severe("");
        plugin.getLogger().severe("To fix this, install ONE of the following:");
        plugin.getLogger().severe("  Option A: Vault + an economy plugin (EssentialsX, CMI, etc)");
        plugin.getLogger().severe("  Option B: EssentialsX (with economy enabled in config)");
        plugin.getLogger().severe("");
        if (vaultPresent && !essentialsPresent) {
            plugin.getLogger().severe("You have Vault installed but no economy plugin.");
            plugin.getLogger().severe("Install EssentialsX or another Vault-compatible economy plugin.");
        } else if (!vaultPresent && essentialsPresent) {
            plugin.getLogger().severe("EssentialsX is installed but economy service failed to initialize.");
            plugin.getLogger().severe("Check that economy is enabled in Essentials config.yml.");
        } else if (!vaultPresent && !essentialsPresent) {
            plugin.getLogger().severe("Neither Vault nor EssentialsX is installed.");
            plugin.getLogger().severe("Please install an economy system.");
        }

        return false;
    }

    /**
     * Attempts to set up Vault economy provider.
     */
    private boolean setupVaultEconomy() {
        try {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                plugin.getLogger().info("Vault economy provider: NONE REGISTERED");
                return false;
            }

            Economy vaultEconomy = rsp.getProvider();
            if (vaultEconomy == null) {
                plugin.getLogger().info("Vault economy provider: NULL (registration found but provider is null)");
                return false;
            }

            adapter = new VaultAdapter(vaultEconomy);
            providerName = "Vault (" + vaultEconomy.getName() + ")";
            plugin.getLogger().info("Vault economy provider: " + vaultEconomy.getName());
            plugin.getLogger().info("Selected provider: " + providerName);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to hook into Vault economy", e);
            return false;
        }
    }

    /**
     * Attempts to set up EssentialsX economy as fallback.
     */
    private boolean setupEssentialsEconomy() {
        try {
            Plugin essPlugin = Bukkit.getPluginManager().getPlugin("Essentials");
            if (essPlugin == null || !essPlugin.isEnabled()) {
                plugin.getLogger().info("Essentials plugin: NOT ENABLED");
                return false;
            }

            // Check if it's actually EssentialsX (has the expected class)
            if (!essPlugin.getClass().getName().equals("com.earth2me.essentials.Essentials")) {
                plugin.getLogger().warning("Essentials plugin found but is not EssentialsX.");
                plugin.getLogger().warning("Class: " + essPlugin.getClass().getName());
                return false;
            }

            // Try to create the adapter
            EssentialsAdapter essAdapter = new EssentialsAdapter(essPlugin);
            if (!essAdapter.isAvailable()) {
                plugin.getLogger().warning("EssentialsX found but economy service is not available.");
                plugin.getLogger().warning("Check that economy is enabled in Essentials config.yml");
                return false;
            }

            adapter = essAdapter;
            providerName = "EssentialsX (Direct)";
            plugin.getLogger().info("Using EssentialsX direct economy integration.");
            plugin.getLogger().info("Selected provider: " + providerName);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to set up EssentialsX economy fallback", e);
            return false;
        }
    }

    /**
     * Gets the name of the active economy provider
     */
    public String getProviderName() {
        return providerName;
    }

    /**
     * Checks if the economy manager is ready
     */
    public boolean isReady() {
        return adapter != null;
    }

    /**
     * Gets a player's current balance.
     * Thread-safe: can be called from any thread.
     *
     * @param playerUuid The player's UUID
     * @return The player's balance
     */
    public double getBalance(UUID playerUuid) {
        if (adapter == null) return 0.0;

        // If on main thread, call directly
        if (Bukkit.isPrimaryThread()) {
            return adapter.getBalance(playerUuid);
        }

        // Off main thread - schedule sync and wait
        return runSync(() -> adapter.getBalance(playerUuid));
    }

    /**
     * Checks if a player has at least the specified amount.
     * Thread-safe: can be called from any thread.
     *
     * @param playerUuid The player's UUID
     * @param amount     The amount to check
     * @return true if the player has enough money
     */
    public boolean has(UUID playerUuid, double amount) {
        return getBalance(playerUuid) >= amount;
    }

    /**
     * Withdraws money from a player's account.
     * Thread-safe: can be called from any thread.
     *
     * @param playerUuid The player's UUID
     * @param amount     The amount to withdraw
     * @return true if successful
     */
    public boolean withdraw(UUID playerUuid, double amount) {
        if (amount <= 0) return true;
        if (adapter == null) return false;

        // If on main thread, call directly
        if (Bukkit.isPrimaryThread()) {
            return adapter.withdraw(playerUuid, amount);
        }

        // Off main thread - schedule sync and wait
        return runSync(() -> adapter.withdraw(playerUuid, amount));
    }

    /**
     * Deposits money into a player's account.
     * Thread-safe: can be called from any thread.
     *
     * @param playerUuid The player's UUID
     * @param amount     The amount to deposit
     * @return true if successful
     */
    public boolean deposit(UUID playerUuid, double amount) {
        if (amount <= 0) return true;
        if (adapter == null) return false;

        // If on main thread, call directly
        if (Bukkit.isPrimaryThread()) {
            return adapter.deposit(playerUuid, amount);
        }

        // Off main thread - schedule sync and wait
        Boolean result = runSync(() -> adapter.deposit(playerUuid, amount));
        return result != null && result;
    }

    /**
     * Transfers money between two players.
     * Thread-safe: can be called from any thread.
     *
     * @param fromUuid The UUID of the payer
     * @param toUuid   The UUID of the receiver
     * @param amount   The amount to transfer
     * @return true if successful
     */
    public boolean transfer(UUID fromUuid, UUID toUuid, double amount) {
        if (amount <= 0) return true;

        // Withdraw first
        if (!withdraw(fromUuid, amount)) {
            return false;
        }

        // Then deposit - if this fails, refund the withdrawal
        if (!deposit(toUuid, amount)) {
            deposit(fromUuid, amount); // Attempt refund
            return false;
        }

        return true;
    }

    /**
     * Formats an amount according to the economy's formatting.
     *
     * @param amount The amount to format
     * @return Formatted currency string
     */
    public String format(double amount) {
        if (adapter != null) {
            return adapter.format(amount);
        }
        return plugin.getMessageManager().formatCurrency(amount);
    }

    /**
     * Runs a task synchronously on the main thread and waits for the result.
     * If already on main thread, runs directly.
     */
    private <T> T runSync(java.util.function.Supplier<T> task) {
        if (Bukkit.isPrimaryThread()) {
            return task.get();
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                future.complete(task.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            return future.join();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Economy operation failed", e);
            return null;
        }
    }

    // ==================== ADAPTER INTERFACE ====================

    /**
     * Interface for economy adapters
     */
    private interface EconomyAdapter {
        double getBalance(UUID playerUuid);
        boolean withdraw(UUID playerUuid, double amount);
        boolean deposit(UUID playerUuid, double amount);
        String format(double amount);
    }

    // ==================== VAULT ADAPTER ====================

    /**
     * Adapter for Vault economy
     */
    private static class VaultAdapter implements EconomyAdapter {
        private final Economy economy;

        VaultAdapter(Economy economy) {
            this.economy = economy;
        }

        @Override
        public double getBalance(UUID playerUuid) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            return economy.getBalance(player);
        }

        @Override
        public boolean withdraw(UUID playerUuid, double amount) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            if (!economy.has(player, amount)) {
                return false;
            }
            return economy.withdrawPlayer(player, amount).transactionSuccess();
        }

        @Override
        public boolean deposit(UUID playerUuid, double amount) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            return economy.depositPlayer(player, amount).transactionSuccess();
        }

        @Override
        public String format(double amount) {
            return economy.format(amount);
        }
    }

    // ==================== ESSENTIALS ADAPTER ====================

    /**
     * Adapter for EssentialsX direct economy integration
     */
    private static class EssentialsAdapter implements EconomyAdapter {
        private final Object essentials;
        private final java.lang.reflect.Method getUserMethod;
        private final java.lang.reflect.Method getMoneyMethod;
        private final java.lang.reflect.Method setMoneyMethod;
        private final boolean available;

        EssentialsAdapter(Plugin essPlugin) {
            this.essentials = essPlugin;

            java.lang.reflect.Method getUserTemp = null;
            java.lang.reflect.Method getMoneyTemp = null;
            java.lang.reflect.Method setMoneyTemp = null;
            boolean availableTemp = false;

            try {
                // Get the getUser method
                Class<?> essClass = essPlugin.getClass();
                getUserTemp = essClass.getMethod("getUser", UUID.class);

                // Get User class and its money methods
                Class<?> userClass = Class.forName("com.earth2me.essentials.User");
                getMoneyTemp = userClass.getMethod("getMoney");
                setMoneyTemp = userClass.getMethod("setMoney", BigDecimal.class);

                // Test that it works
                availableTemp = true;
            } catch (Exception e) {
                // Methods not available
            }

            this.getUserMethod = getUserTemp;
            this.getMoneyMethod = getMoneyTemp;
            this.setMoneyMethod = setMoneyTemp;
            this.available = availableTemp;
        }

        public boolean isAvailable() {
            return available;
        }

        @Override
        public double getBalance(UUID playerUuid) {
            try {
                Object user = getUserMethod.invoke(essentials, playerUuid);
                if (user == null) return 0.0;

                BigDecimal money = (BigDecimal) getMoneyMethod.invoke(user);
                return money != null ? money.doubleValue() : 0.0;
            } catch (Exception e) {
                return 0.0;
            }
        }

        @Override
        public boolean withdraw(UUID playerUuid, double amount) {
            try {
                Object user = getUserMethod.invoke(essentials, playerUuid);
                if (user == null) return false;

                BigDecimal current = (BigDecimal) getMoneyMethod.invoke(user);
                if (current == null) current = BigDecimal.ZERO;

                BigDecimal amountBD = BigDecimal.valueOf(amount);
                if (current.compareTo(amountBD) < 0) {
                    return false;
                }

                BigDecimal newBalance = current.subtract(amountBD);
                setMoneyMethod.invoke(user, newBalance);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean deposit(UUID playerUuid, double amount) {
            try {
                Object user = getUserMethod.invoke(essentials, playerUuid);
                if (user == null) return false;

                BigDecimal current = (BigDecimal) getMoneyMethod.invoke(user);
                if (current == null) current = BigDecimal.ZERO;

                BigDecimal newBalance = current.add(BigDecimal.valueOf(amount));
                setMoneyMethod.invoke(user, newBalance);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public String format(double amount) {
            // EssentialsX doesn't have a simple format method accessible this way
            // Return a reasonable default
            return String.format("$%.2f", amount);
        }
    }
}
