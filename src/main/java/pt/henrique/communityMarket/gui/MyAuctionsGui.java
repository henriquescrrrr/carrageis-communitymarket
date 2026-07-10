package pt.henrique.communityMarket.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pt.henrique.communityMarket.CommunityMarket;
import pt.henrique.communityMarket.model.Auction;
import pt.henrique.communityMarket.service.AuctionService;
import pt.henrique.communityMarket.util.ItemBuilder;
import pt.henrique.communityMarket.util.TextUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI for viewing and managing the player's own auctions.
 * Click to cancel (only if no bids).
 */
public class MyAuctionsGui implements MarketGui {

    private final CommunityMarket plugin;
    private final GuiManager guiManager;
    private Inventory inventory;
    private Player player;
    private List<Auction> auctions;

    private static final int BACK_SLOT = 49;

    public MyAuctionsGui(CommunityMarket plugin, GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    public void open(Player player) {
        this.player = player;

        plugin.getAuctionService().getPlayerAuctions(player.getUniqueId())
                .thenAccept(loadedAuctions -> {
                    this.auctions = loadedAuctions;

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        buildGui();
                        player.openInventory(inventory);
                        guiManager.registerGui(player.getUniqueId(), this);
                    });
                });
    }

    private void buildGui() {
        var msgManager = plugin.getMessageManager();

        String title = TextUtil.colorizeToString(msgManager.getRaw("gui-titles.my-auctions"));
        inventory = Bukkit.createInventory(this, 54, title);

        // Fill bottom
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Add auctions
        for (int i = 0; i < Math.min(auctions.size(), 45); i++) {
            Auction auction = auctions.get(i);
            inventory.setItem(i, createAuctionItem(auction));
        }

        // Back button
        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.BARRIER)
                .name(msgManager.getButton("back"))
                .build());
    }

    private ItemStack createAuctionItem(Auction auction) {
        var msgManager = plugin.getMessageManager();

        ItemStack display = auction.getItem().clone();

        Duration remaining = Duration.between(Instant.now(), auction.getEndsAt());
        String ends = TextUtil.formatDuration(remaining);

        String bidder = auction.getHighestBidderName() != null ? auction.getHighestBidderName() : "&7None";
        String currentBid = auction.getBidCount() > 0
                ? msgManager.formatCurrency(auction.getCurrentBid())
                : "&7No bids";

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(msgManager.getLore("my-auction-info", Map.of(
                "start_price", msgManager.formatCurrency(auction.getStartPrice()),
                "current_bid", currentBid,
                "bidder", bidder,
                "bid_count", String.valueOf(auction.getBidCount()),
                "ends", ends
        )));

        // Show if cancellable
        if (auction.getBidCount() == 0) {
            lore.add("");
            lore.add("&aClick to cancel");
        } else {
            lore.add("");
            lore.add("&cCannot cancel - has bids");
        }

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

        // Click on auction to cancel
        if (slot >= 0 && slot < 45 && slot < auctions.size()) {
            Auction auction = auctions.get(slot);

            if (auction.getBidCount() > 0) {
                player.sendMessage(plugin.getMessageManager().getPrefixed("messages.auction-not-found"));
                playSound(player, plugin.getConfigManager().getErrorSound());
                return;
            }

            confirmCancel(player, auction);
        }
    }

    private void confirmCancel(Player player, Auction auction) {
        var msgManager = plugin.getMessageManager();

        String[] info = {
                "&7Item: &f" + auction.getItem().getType().name(),
                "&7Starting Price: &a" + msgManager.formatCurrency(auction.getStartPrice()),
                "",
                "&cThis will cancel your auction.",
                "&cThe item will be moved to claim storage."
        };

        guiManager.openConfirmation(player, confirmed -> {
            if (confirmed) {
                plugin.getAuctionService().cancelAuction(auction.getId(), player.getUniqueId(), false)
                        .thenAccept(result -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (result == AuctionService.CancelResult.SUCCESS) {
                                    player.sendMessage(msgManager.getPrefixed("messages.auction-cancelled"));
                                    playSound(player, plugin.getConfigManager().getSuccessSound());
                                } else if (result == AuctionService.CancelResult.HAS_BIDS) {
                                    player.sendMessage(msgManager.getPrefixed("messages.auction-not-found"));
                                    playSound(player, plugin.getConfigManager().getErrorSound());
                                } else {
                                    player.sendMessage(msgManager.getPrefixed("messages.auction-not-found"));
                                    playSound(player, plugin.getConfigManager().getErrorSound());
                                }
                                open(player); // Refresh
                            });
                        });
            } else {
                open(player);
            }
        }, msgManager.getRaw("gui-titles.confirm-cancel"), info);
    }

    private void playSound(Player player, String soundName) {
        pt.henrique.communityMarket.util.SoundUtil.playSound(player, soundName);
    }

    @Override
    public GuiType getType() {
        return GuiType.MY_AUCTIONS;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

