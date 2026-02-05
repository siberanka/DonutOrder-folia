/*
 * Language manager for DonutOrder plugin.
 * Handles loading and retrieving translated strings from language files.
 */
package me.clanify.donutOrder.store;

import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.Utils;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class LangManager {
    private final DonutOrder plugin;
    private FileConfiguration lang;
    private String currentLang;
    private File langFile;

    public LangManager(DonutOrder plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        // Get the language setting from config
        String langCode = plugin.cfg().cfg().getString("default-language", "en");
        this.currentLang = langCode;

        // Ensure lang folder exists
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        // Save default language files if they don't exist
        saveDefaultLang("en.yml");
        saveDefaultLang("tr.yml");

        // Load the configured language file
        this.langFile = new File(langFolder, langCode + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file '" + langCode + ".yml' not found, falling back to 'en.yml'");
            this.langFile = new File(langFolder, "en.yml");
            this.currentLang = "en";
        }

        this.lang = YamlConfiguration.loadConfiguration(langFile);

        // Set defaults from jar
        InputStream defStream = plugin.getResource("lang/" + currentLang + ".yml");
        if (defStream == null) {
            defStream = plugin.getResource("lang/en.yml");
        }
        if (defStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defStream, StandardCharsets.UTF_8));
            lang.setDefaults(defConfig);
        }

        plugin.getLogger().info("Loaded language: " + currentLang);
    }

    private void saveDefaultLang(String fileName) {
        File langFile = new File(plugin.getDataFolder(), "lang/" + fileName);
        if (!langFile.exists()) {
            plugin.saveResource("lang/" + fileName, false);
        }
    }

    /**
     * Get a translated string with color codes applied.
     */
    public String get(String key) {
        String value = lang.getString(key);
        if (value == null) {
            return key;
        }
        return Utils.formatColors(value);
    }

    /**
     * Get a translated string with default fallback.
     */
    public String get(String key, String def) {
        String value = lang.getString(key, def);
        return Utils.formatColors(value);
    }

    /**
     * Get a translated string with placeholders replaced.
     */
    public String get(String key, Map<String, String> placeholders) {
        String value = get(key);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                value = value.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return value;
    }

    /**
     * Get a translated string with default and placeholders.
     */
    public String get(String key, String def, Map<String, String> placeholders) {
        String value = get(key, def);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                value = value.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return value;
    }

    /**
     * Get a list of translated strings with color codes applied.
     */
    public List<String> getList(String key) {
        List<String> list = lang.getStringList(key);
        List<String> result = new ArrayList<>();
        for (String s : list) {
            result.add(Utils.formatColors(s));
        }
        return result;
    }

    /**
     * Get a list of translated strings with placeholders replaced.
     */
    public List<String> getList(String key, Map<String, String> placeholders) {
        List<String> list = getList(key);
        if (placeholders == null) {
            return list;
        }
        List<String> result = new ArrayList<>();
        for (String s : list) {
            String line = s;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                line = line.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            result.add(line);
        }
        return result;
    }

    /**
     * Get translated item name for chat messages.
     * Falls back to Title Case of material name if not found.
     */
    public String getItemName(Material material) {
        String key = "items." + material.name();
        String value = lang.getString(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        // Fallback to Title Case
        return OrderManager.nice(material);
    }

    /**
     * Get translated item name for a specific material key.
     */
    public String getItemName(String materialName) {
        String key = "items." + materialName;
        String value = lang.getString(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        // Fallback to Title Case
        return titleCase(materialName);
    }

    /**
     * Get sort type display name.
     */
    public String getSortName(String sortType) {
        return get("sort-names." + sortType, titleCase(sortType.replace('_', ' ')));
    }

    /**
     * Get filter category display name.
     */
    public String getFilterName(String filter) {
        if ("All".equalsIgnoreCase(filter)) {
            return get("filters.all", "All");
        }
        return get("filters." + filter, filter);
    }

    private String titleCase(String s) {
        String lower = s.toLowerCase().replace('_', ' ');
        String[] parts = lower.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty())
                continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    public String getCurrentLang() {
        return currentLang;
    }
}
