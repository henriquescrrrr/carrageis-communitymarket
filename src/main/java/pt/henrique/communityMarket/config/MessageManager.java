package pt.henrique.communityMarket.config;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pt.henrique.communityMarket.CommunityMarket;
import pt.henrique.communityMarket.util.TextUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages plugin messages and localization.
 * Supports multiple languages loaded from lang/ folder.
 */
public class MessageManager {

    private final CommunityMarket plugin;
    private FileConfiguration messagesConfig;
    private final Map<String, String> messageCache;
    private DecimalFormat currencyFormatter;
    private String currentLanguage;

    public MessageManager(CommunityMarket plugin) {
        this.plugin = plugin;
        this.messageCache = new HashMap<>();
        reload();
    }

    public void reload() {
        messageCache.clear();

        // Get language from config (default pt_PT; en_US remains bundled as a fallback)
        currentLanguage = plugin.getConfig().getString("language", "pt_PT");

        // Save default language files
        saveDefaultLanguageFiles();

        // Load the selected language file
        File langFolder = new File(plugin.getDataFolder(), "lang");
        File langFile = new File(langFolder, currentLanguage + ".yml");

        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file not found: " + currentLanguage + ".yml, falling back to en_US");
            langFile = new File(langFolder, "en_US.yml");
        }

        messagesConfig = YamlConfiguration.loadConfiguration(langFile);

        // Load defaults from jar as fallback
        InputStream defaultStream = plugin.getResource("lang/en_US.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            messagesConfig.setDefaults(defaultConfig);
        }

        // Setup currency formatter
        String format = plugin.getConfigManager().getCurrencyFormat();
        try {
            currencyFormatter = new DecimalFormat(format.replace("$", ""));
        } catch (Exception e) {
            currencyFormatter = new DecimalFormat("#,##0.00");
        }

        plugin.getLogger().info("Loaded language: " + currentLanguage);
    }

    private void saveDefaultLanguageFiles() {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        // Save default language files if they don't exist
        String[] languages = {"en_US.yml", "pt_PT.yml"};
        for (String lang : languages) {
            File langFile = new File(langFolder, lang);
            if (!langFile.exists()) {
                plugin.saveResource("lang/" + lang, false);
            }
        }
    }

    /**
     * Gets the current language code
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }

    /**
     * Gets a raw message string from the config
     */
    public String getRaw(String path) {
        if (messageCache.containsKey(path)) {
            return messageCache.get(path);
        }

        String message = messagesConfig.getString(path, "&cMissing message: " + path);
        messageCache.put(path, message);
        return message;
    }

    /**
     * Gets a message as a Component
     */
    public Component get(String path) {
        return TextUtil.colorize(getRaw(path));
    }

    /**
     * Gets a message with placeholders replaced
     */
    public Component get(String path, Map<String, String> placeholders) {
        String message = getRaw(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return TextUtil.colorize(message);
    }

    /**
     * Gets a message with a single placeholder replaced
     */
    public Component get(String path, String placeholder, String value) {
        String message = getRaw(path).replace("{" + placeholder + "}", value);
        return TextUtil.colorize(message);
    }

    /**
     * Gets a prefixed message
     */
    public Component getPrefixed(String path) {
        return TextUtil.colorize(getRaw("prefix") + getRaw(path));
    }

    /**
     * Gets a prefixed message with placeholders
     */
    public Component getPrefixed(String path, Map<String, String> placeholders) {
        String message = getRaw(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return TextUtil.colorize(getRaw("prefix") + message);
    }

    /**
     * Gets a prefixed message with a single placeholder
     */
    public Component getPrefixed(String path, String placeholder, String value) {
        String message = getRaw(path).replace("{" + placeholder + "}", value);
        return TextUtil.colorize(getRaw("prefix") + message);
    }

    /**
     * Gets a list of messages from the config
     */
    public List<String> getList(String path) {
        return messagesConfig.getStringList(path);
    }

    /**
     * Gets a list of messages as Components
     */
    public List<Component> getComponentList(String path) {
        return getList(path).stream()
                .map(TextUtil::colorize)
                .toList();
    }

    /**
     * Formats a currency amount
     */
    public String formatCurrency(double amount) {
        String symbol = plugin.getConfigManager().getCurrencySymbol();
        return symbol + currencyFormatter.format(amount);
    }

    /**
     * Gets a button name from config
     */
    public String getButton(String buttonKey) {
        return getRaw("buttons." + buttonKey);
    }

    /**
     * Gets button lore list from config
     */
    public List<String> getLore(String loreKey) {
        return getList("lore." + loreKey);
    }

    /**
     * Gets lore with placeholders replaced
     */
    public List<String> getLore(String loreKey, Map<String, String> placeholders) {
        List<String> lore = getList("lore." + loreKey);
        return lore.stream()
                .map(line -> {
                    String result = line;
                    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                        result = result.replace("{" + entry.getKey() + "}", entry.getValue());
                    }
                    return result;
                })
                .toList();
    }

    /**
     * Gets filter display name
     */
    public String getFilter(String filterKey) {
        return getRaw("filters." + filterKey);
    }

    /**
     * Gets sort display name
     */
    public String getSort(String sortKey) {
        return getRaw("sort." + sortKey);
    }
}
