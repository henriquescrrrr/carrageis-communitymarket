package pt.henrique.communityMarket.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pt.henrique.communityMarket.CommunityMarket;
import pt.henrique.communityMarket.model.ClaimItem;
import pt.henrique.communityMarket.service.ClaimService;
import pt.henrique.communityMarket.util.ItemBuilder;
import pt.henrique.communityMarket.util.TextUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI for claiming items from expired listings, won auctions, etc.
 * Click on an item to claim it, or use "Claim All" button.
 */
public class ClaimGui implements MarketGui {

    private final CommunityMarket plugin;
    private final GuiManager guiManager;
    private Inventory inventory;
    private Player player;
    private List<ClaimItem> claimItems;

    private static final int BACK_SLOT = 49;
    private static final int CLAIM_ALL_SLOT = 45;

    public ClaimGui(CommunityMarket plugin, GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    public void open(Player player) {
        this.player = player;

        plugin.getClaimService().getPlayerClaimItems(player.getUniqueId())
                .thenAccept(items -> {
                    this.claimItems = items;

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        buildGui();
                        player.openInventory(inventory);
                        guiManager.registerGui(player.getUniqueId(), this);
                    });
                });
    }

    private void buildGui() {
        var msgManager = plugin.getMessageManager();

        String title = TextUtil.colorizeToString(msgManager.getRaw("gui-titles.claim-items"));
        inventory = Bukkit.createInventory(this, 54, title);

        // Fill bottom row
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Add claim items
        for (int i = 0; i < Math.min(claimItems.size(), 45); i++) {
            ClaimItem item = claimItems.get(i);
            inventory.setItem(i, createClaimItemDisplay(item));
        }

        // Claim All button
        if (!claimItems.isEmpty()) {
            inventory.setItem(CLAIM_ALL_SLOT, new ItemBuilder(Material.HOPPER)
                    .name(msgManager.getButton("claim-all"))
                    .lore("&7Claim all items at once")
                    .glow()
                    .build());
        }

        // Back button
        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.BARRIER)
                .name(msgManager.getButton("back"))
                .build());

        // Show empty message if no items
        if (claimItems.isEmpty()) {
            inventory.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name("&cNo items to claim")
                    .lore("&7Items from expired listings,",
                          "&7won auctions, etc. appear here.")
                    .build());
        }
    }

    private ItemStack createClaimItemDisplay(ClaimItem claimItem) {
        var msgManager = plugin.getMessageManager();

        ItemStack display = claimItem.getItem().clone();

        String age = TextUtil.formatDuration(
                Duration.between(claimItem.getCreatedAt(), Instant.now())) + " ago";

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(msgManager.getLore("claim-item-info", Map.of(
                "reason", claimItem.getReason().getDisplayName(),
                "source", claimItem.getSourceInfo() != null ? claimItem.getSourceInfo() : "Unknown",
                "date", age
        )));

        return new ItemBuilder(display)
                .addLore(lore)
                .build();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == BACK_SLOT) {
            guiManager.openMainMenu(player);
            return;
        }

        if (slot == CLAIM_ALL_SLOT && !claimItems.isEmpty()) {
            claimAll(player);
            return;
        }

        // Click on item to claim
        if (slot >= 0 && slot < 45 && slot < claimItems.size()) {
            ClaimItem item = claimItems.get(slot);
            claimSingle(player, item);
        }
    }

    private void claimSingle(Player player, ClaimItem item) {
        var msgManager = plugin.getMessageManager();

        plugin.getClaimService().claimItem(item.getId(), player)
                .thenAccept(result -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        switch (result) {
                            case SUCCESS -> {
                                player.sendMessage(msgManager.getPrefixed("messages.claim-success"));
                                playSound(player, plugin.getConfigManager().getSuccessSound());
                                open(player); // Refresh
                            }
                            case INVENTORY_FULL -> {
                                player.sendMessage(msgManager.getPrefixed("messages.claim-inventory-full"));
                                playSound(player, plugin.getConfigManager().getErrorSound());
                            }
                            default -> {
                                player.sendMessage(msgManager.getPrefixed("messages.claim-empty"));
                                playSound(player, plugin.getConfigManager().getErrorSound());
                                open(player);
                            }
                        }
                    });
                });
    }

    private void claimAll(Player player) {
        var msgManager = plugin.getMessageManager();

        plugin.getClaimService().claimAll(player)
                .thenAccept(count -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (count > 0) {
                            player.sendMessage(msgManager.getPrefixed("messages.claim-all-success",
                                    "count", String.valueOf(count)));
                            playSound(player, plugin.getConfigManager().getSuccessSound());
                        } else {
                            player.sendMessage(msgManager.getPrefixed("messages.claim-empty"));
                        }
                        open(player); // Refresh
                    });
                });
    }

    private void playSound(Player player, String soundName) {
        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 0.5f, 1.0f);
        } catch (IllegalArgumentException ignored) {}
    }

    @Override
    public GuiType getType() {
        return GuiType.CLAIM;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

