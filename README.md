# CommunityMarket

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/carrageis-communitymarket?logo=modrinth)](https://modrinth.com/plugin/carrageis-communitymarket)
[![GitHub License](https://img.shields.io/github/license/henrique/CommunityMarket)](LICENSE)

A professional, production-ready **GUI-only marketplace plugin** for Minecraft Paper 1.21+.

Players can create fixed-price listings and auctions, browse, buy, bid, claim items, and withdraw earnings — all through intuitive GUIs. **No complex commands to learn!**

Features

Fixed-Price Market
- Create listings with custom prices and durations
- Browse paginated listings with sorting
- Safe atomic purchases to prevent double-buying
- Configurable taxes on sales

Auction System
- Start auctions with minimum bid and optional buyout price
- **Anti-snipe protection** extends auction when bids arrive near the end
- Bid history and automatic outbid notifications
- Safe handling of auction endings and payouts

Claim Storage
- Items from expired listings go to claim storage
- Won auction items are safely delivered
- Handles full inventories gracefully

Earnings Management
- Pending earnings from sales accumulate
- Withdraw all earnings at once
- Complete transaction history

Admin Features (GUI-based)
- View all listings and auctions
- Remove any listing or cancel auctions
- Force-end auctions
- Reload configuration in-game

Intuitive GUI Flow
The creation flow for listings and auctions:
1. **Main Menu** - Central hub for all actions
2. **Select Item** - Click an item from your inventory
3. **Select Quantity** - Choose how many to sell (for stackable items)
4. **Settings** - Set price and duration with easy click-to-adjust controls
5. **Confirm** - Review and create your listing/auction

Requirements

| Requirement | Version |
|-------------|---------|
| **Server** | Paper 1.21+ (or Purpur, Folia-compatible forks) |
| **Java** | Java 21+ |
| **Economy** | Vault + economy plugin **OR** EssentialsX |

### Economy Support
CommunityMarket supports two economy configurations:

1. **Vault + Economy Plugin** (Recommended)
   - Install [VaultUnlocked](https://modrinth.com/plugin/vaultunlocked)
   - Install an economy plugin: [EssentialsX](https://essentialsx.net/), CMI, or any Vault-compatible economy

2. **EssentialsX Standalone** (Fallback)
   - Install [EssentialsX](https://essentialsx.net/) with economy enabled
   - CommunityMarket will use EssentialsX directly if Vault is not present

Installation

1. Download the latest `CommunityMarket-x.x.x.jar` from [Modrinth](https://modrinth.com/plugin/carrageis-communitymarket)
2. Place it in your server's `plugins/` folder
3. Ensure you have an economy system installed (see above)
4. Start or restart your server
5. Edit `plugins/CommunityMarket/config.yml` as needed
6. Use `/market` to open the marketplace!

Commands

| Command | Alias | Description | Permission |
|---------|-------|-------------|------------|
| `/market` | `/cmarket` | Opens the main market GUI | `communitymarket.use` |

**That's it!** Everything else is done through GUIs.

Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `communitymarket.*` | All permissions | op |
| `communitymarket.use` | Access the market GUI | everyone |
| `communitymarket.sell` | Create fixed-price listings | everyone |
| `communitymarket.auction` | Create auctions | everyone |
| `communitymarket.buy` | Purchase from the market | everyone |
| `communitymarket.bid` | Bid on auctions | everyone |
| `communitymarket.claim` | Claim items from storage | everyone |
| `communitymarket.withdraw` | Withdraw earnings | everyone |
| `communitymarket.admin` | Access admin functions | op |

Configuration

### config.yml Highlights

```yaml
# Language setting (available: en_US, pt_PT)
language: en_US

# Database: sqlite (default) or mysql
database:
  type: sqlite

# Economy settings
economy:
  currency-format: "$#,##0.00"
  taxes:
    market-tax: 5.0      # Tax on fixed-price sales
    auction-tax: 7.5     # Tax on auction sales

# Fixed-Price Market
market:
  max-listings-per-player: 20
  default-duration-hours: 168  # 7 days
  min-price: 1.0
  max-price: 1000000000.0

# Auctions
auction:
  max-auctions-per-player: 10
  default-duration-hours: 24
  anti-snipe:
    enabled: true
    trigger-seconds: 30
    extension-seconds: 30

# GUI Settings
gui:
  show-help-button: true  # Toggle help button visibility
  sounds:
    click: UI_BUTTON_CLICK
    success: ENTITY_PLAYER_LEVELUP
    error: ENTITY_VILLAGER_NO
```

### Available Languages
- `en_US` - English (default)
- `pt_PT` - Portuguese

Language files are located in `plugins/CommunityMarket/lang/` and can be customized.

Troubleshooting

### "No compatible economy provider found!"

This error appears when CommunityMarket cannot find an economy system.

**Solutions:**
1. **Install Vault + an economy plugin:**
   - Download [VaultUnlocked](https://modrinth.com/plugin/vaultunlocked)
   - Download [EssentialsX](https://essentialsx.net/) or another economy plugin
   - Restart your server

2. **Using EssentialsX without Vault:**
   - Make sure EssentialsX is installed and enabled
   - Check that `economy` is not disabled in EssentialsX's `config.yml`

### Items stuck in claim storage

If a player's inventory was full when they purchased an item or won an auction, the item goes to their claim storage. They can retrieve it from the main menu → "Claim Items".

### Auctions not ending

Auctions are checked periodically (every 5 seconds by default). If auctions seem stuck:
- Check server console for errors
- Verify the database is working properly
- Try `/market admin` → View Auctions to see auction status

### Database issues

By default, CommunityMarket uses SQLite (no setup required). For larger servers, MySQL is recommended:

```yaml
database:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    database: communitymarket
    username: your_user
    password: your_password
```

API

CommunityMarket does not currently provide a public API. If you need integration capabilities, please open an issue on GitHub.

Support

- **Issues & Bug Reports:** [GitHub Issues](https://github.com/henriquescrrrr/carrageis-communitymarket/issues)
- **Feature Requests:** [GitHub Issues](https://github.com/henriquescrrrr/carrageis-communitymarket/issues)

License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

Credits

- Built for the Paper/Spigot community
- Uses [Vault](https://github.com/MilkBowl/VaultAPI) for economy integration
- Uses [HikariCP](https://github.com/brettwooldridge/HikariCP) for database connection pooling

