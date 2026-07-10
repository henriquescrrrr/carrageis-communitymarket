package pt.henrique.communityMarket.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pt.henrique.communityMarket.CommunityMarket;
import pt.henrique.communityMarket.util.ItemBuilder;
import pt.henrique.communityMarket.util.TextUtil;

import java.util.List;

/**
 * Help GUI showing how to use the marketplace.
 */
public class HelpGui implements MarketGui {

    private final CommunityMarket plugin;
    private final GuiManager guiManager;
    private Inventory inventory;

    private static final int BACK_SLOT = 49;

    public HelpGui(CommunityMarket plugin, GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    public void open(Player player) {
        var msgManager = plugin.getMessageManager();

        String title = TextUtil.colorizeToString(msgManager.getRaw("gui-titles.help"));
        inventory = Bukkit.createInventory(this, 54, title);

        buildGui();
        player.openInventory(inventory);
        guiManager.registerGui(player.getUniqueId(), this);
    }

    private void buildGui() {
        var msgManager = plugin.getMessageManager();

        // Fill with glass
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Help content (localized). Rendered as the book's lore so the Help screen
        // respects the configured language instead of showing hardcoded English.
        List<String> helpContent = msgManager.getList("help.content");

        // Main help book with the localized explanation of every feature
        inventory.setItem(22, new ItemBuilder(Material.WRITTEN_BOOK)
                .name(msgManager.getRaw("help.title"))
                .lore(helpContent)
                .build());

        // Tax info (values are dynamic; labels come from the language file)
        inventory.setItem(40, new ItemBuilder(Material.GOLD_NUGGET)
                .name(msgManager.getRaw("help.tax-title"))
                .lore(
                        msgManager.getRaw("help.tax-market").replace("{tax}", String.valueOf(plugin.getConfigManager().getMarketTax())),
                        msgManager.getRaw("help.tax-auction").replace("{tax}", String.valueOf(plugin.getConfigManager().getAuctionTax())),
                        "",
                        msgManager.getRaw("help.tax-note")
                )
                .build());

        // Back button
        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.BARRIER)
                .name(msgManager.getButton("back"))
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == BACK_SLOT) {
            guiManager.openMainMenu(player);
        }
    }

    @Override
    public GuiType getType() {
        return GuiType.HELP;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

