/*
 * Decompiled with CFR 0.152.
 */
package me.clanify.donutOrder.store;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.Utils;
import me.clanify.donutOrder.data.SortType;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ConfigManager {
    private final DonutOrder plugin;
    private FileConfiguration cfg;
    private FileConfiguration saves;
    private final Set<String> disabledTokens = new HashSet<>();
    private final File savesFile;

    public ConfigManager(DonutOrder plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.cfg = plugin.getConfig();
        this.savesFile = new File(plugin.getDataFolder(), "saves.yml");
        if (!this.savesFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                this.savesFile.createNewFile();
            } catch (Exception ignored) {
            }
        }
        this.saves = YamlConfiguration.loadConfiguration((File) this.savesFile);
        File filterFile = new File(plugin.getDataFolder(), "filter.yml");
        if (!filterFile.exists()) {
            YamlConfiguration def = new YamlConfiguration();
            def.set("Blocks", List.of("STONE", "GRASS_BLOCK", "OAK_PLANKS"));
            def.set("Tools", List.of("WOODEN_PICKAXE", "IRON_AXE"));
            def.set("Food", List.of("APPLE", "BREAD"));
            def.set("Combat", List.of("WOODEN_SWORD", "BOW", "ARROW"));
            def.set("Potions", List.of("POTION", "SPLASH_POTION"));
            def.set("Books", List.of("BOOK", "ENCHANTED_BOOK"));
            def.set("Ingredients", List.of("WHEAT", "SUGAR", "EGG"));
            def.set("Utilities", List.of("COMPASS", "CLOCK", "BUCKET"));
            try {
                def.save(filterFile);
            } catch (Exception ignored) {
            }
        }
        this.loadDisabled();
    }

    public void reload() {
        try {
            this.plugin.reloadConfig();
            this.cfg = this.plugin.getConfig();
            this.saves = YamlConfiguration.loadConfiguration((File) this.savesFile);
            this.loadDisabled();
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to reload configuration safely", e);
        }
    }

    private void loadDisabled() {
        this.disabledTokens.clear();
        List<String> list = this.cfg.getStringList("disabled-items");
        if (list != null) {
            for (String s : list) {
                this.disabledTokens.add(s.trim().toUpperCase());
            }
        }
        if (this.disabledTokens.isEmpty()) {
            this.disabledTokens.add("SPAWNER");
            this.disabledTokens.add("SPAWNER_EGGS");
            this.disabledTokens.add("BEDROCK");
            this.disabledTokens.add("END_PORTAL_FRAME");
            this.disabledTokens.add("BARRIER");
            this.disabledTokens.add("STRUCTURE_BLOCK");
            this.disabledTokens.add("STRUCTURE_VOID");
            this.disabledTokens.add("JIGSAW");
            this.disabledTokens.add("COMMAND_BLOCK");
            this.disabledTokens.add("CHAIN_COMMAND_BLOCK");
            this.disabledTokens.add("REPEATING_COMMAND_BLOCK");
            this.disabledTokens.add("LIGHT");
            this.disabledTokens.add("DEBUG_STICK");
            this.disabledTokens.add("REINFORCED_DEEPSLATE");
            this.disabledTokens.add("TRIAL_SPAWNER");
            this.disabledTokens.add("VAULT");
        }
    }

    public boolean isDisabled(Material m) {
        if (m == null)
            return false;
        return this.disabledTokens.contains(m.name());
    }

    public double getMinPricePerItem() {
        double min = this.cfg.getDouble("minimum-price-per-item", 0.05);
        // Ensure minimum is never negative or zero
        if (min <= 0 || Double.isNaN(min) || Double.isInfinite(min)) {
            return 0.05;
        }
        return min;
    }

    public double getMaxPricePerItem() {
        double max = this.cfg.getDouble("maximum-price-per-item", 1_000_000_000_000.0);
        // Ensure maximum is valid
        if (max <= 0 || Double.isNaN(max) || Double.isInfinite(max)) {
            return 1_000_000_000_000.0;
        }
        return max;
    }

    public int getMaxItemsPerOrder() {
        int max = this.cfg.getInt("maximum-items-per-order", 1_000_000);
        // Ensure maximum is at least 1
        if (max < 1) {
            return 1_000_000;
        }
        return max;
    }

    public FileConfiguration cfg() {
        return this.cfg;
    }

    public FileConfiguration saves() {
        return this.saves;
    }

    public synchronized void saveSaves() {
        try {
            this.saves.save(this.savesFile);
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not save saves.yml", e);
        }
    }

    private LangManager getLang() {
        return this.plugin.lang();
    }

    public String msg(String path, String def) {
        LangManager lang = getLang();
        if (lang != null) {
            return Utils.formatColors(lang.get(path, def));
        }
        return Utils.formatColors(this.cfg.getString(path, def));
    }

    public int rows(String path, int def) {
        return this.cfg.getInt(path, def);
    }

    public String title(String path, String def) {
        LangManager lang = getLang();
        if (lang != null) {
            return Utils.formatColors(lang.get("gui." + path + ".title", def));
        }
        return Utils.formatColors(this.cfg.getString(path, def));
    }

    public int slot(String path, int def) {
        return this.cfg.getInt(path, def);
    }

    public ItemStack button(String path, String defMat, String defName, List<?> defLoreLines) {
        LangManager lang = getLang();
        String name;
        List<String> lore;

        if (lang != null) {
            name = Utils.formatColors(lang.get(path + ".displayname", defName));
            lore = lang.getList(path + ".lore");
        } else {
            name = Utils.formatColors(this.cfg.getString(path + ".displayname", defName));
            lore = this.cfg.getStringList(path + ".lore");
        }

        String matName = this.cfg.getString(path + ".material", defMat);
        Material m = Material.matchMaterial((String) matName);
        if (m == null) {
            m = Material.ARROW;
        }
        ItemStack stack = new ItemStack(m);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.isEmpty() && defLoreLines != null && !defLoreLines.isEmpty()) {
                lore = new ArrayList<>();
                for (Object o : defLoreLines)
                    lore.add(o.toString());
            }
            if (lore.isEmpty()) {
                lore = new ArrayList<>();
            }
            meta.setLore(Utils.formatColors(lore));
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public void play(Player p, String path, String defSound, float vol, float pitch) {
        if (p == null) {
            return;
        }
        String s = this.cfg.getString(path + ".sound", defSound);
        if (s == null || s.equalsIgnoreCase("NONE")) {
            return;
        }
        try {
            Sound sound = Sound.valueOf((String) s.toUpperCase());
            if (sound != null) {
                p.playSound(p.getLocation(), sound, vol, pitch);
            }
        } catch (IllegalArgumentException | NullPointerException illegalArgumentException) {
            // Invalid sound name, silently ignore
        }
    }

    public String sortName(SortType t) {
        LangManager lang = getLang();
        String key = "sort." + t.name();
        String def = switch (t) {
            case MOST_PAID -> "Most Paid";
            case MOST_DELIVERED -> "Most Delivered";
            case RECENTLY_LISTED -> "Recently Listed";
            case MOST_MONEY_PER_ITEM -> "Most Money Per Item";
            default -> throw new IllegalStateException("Unexpected value: " + t);
        };
        if (lang != null) {
            return Utils.formatColors(lang.get(key, def));
        }
        return Utils.formatColors(this.cfg.getString("sort-names." + t.name(), def));
    }

    public String selectedPrefix(String gui) {
        return Utils.formatColors(this.cfg.getString("gui." + gui + ".format.selected_prefix", "&a• "));
    }

    public String unselectedPrefix(String gui) {
        return Utils.formatColors(this.cfg.getString("gui." + gui + ".format.unselected_prefix", "&f• "));
    }

    public ItemStack dynamicItem(Material mat, String path, String defName, List<String> defLore,
            Map<String, String> placeholders) {
        LangManager lang = getLang();
        String name;
        List<String> lore;

        if (lang != null) {
            name = Utils.formatColors(lang.get(path + ".displayname", defName));
            lore = lang.getList(path + ".lore");
        } else {
            name = Utils.formatColors(this.cfg.getString(path + ".displayname", defName));
            lore = this.cfg.getStringList(path + ".lore");
        }

        if (placeholders != null && name != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                name = name.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(name);
            }
            if (lore.isEmpty() && defLore != null) {
                lore = new ArrayList<String>(defLore);
            }
            if (placeholders != null) {
                ArrayList<String> finalLore = new ArrayList<String>();
                for (String line : lore) {
                    String processed = line;
                    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                        processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
                    }
                    finalLore.add(processed);
                }
                lore = finalLore;
            }
            meta.setLore(Utils.formatColors(lore));
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
