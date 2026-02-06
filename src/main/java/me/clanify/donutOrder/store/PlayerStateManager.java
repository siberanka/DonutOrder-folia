/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.FileConfiguration
 */
package me.clanify.donutOrder.store;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.data.AlphaSort;
import me.clanify.donutOrder.data.SortType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class PlayerStateManager {
    private final DonutOrder plugin;
    private final Map<UUID, View> main = new ConcurrentHashMap<UUID, View>();
    private final Map<UUID, ItemView> selectItem = new ConcurrentHashMap<UUID, ItemView>();

    public PlayerStateManager(DonutOrder plugin) {
        this.plugin = plugin;
        this.loadAllPrefs();
    }

    public View main(UUID u) {
        return this.main.computeIfAbsent(u, k -> new View());
    }

    public ItemView items(UUID u) {
        return this.selectItem.computeIfAbsent(u, k -> new ItemView());
    }

    public void saveAllPrefs() {
        String base;
        FileConfiguration sv = this.plugin.cfg().saves();
        for (Map.Entry<UUID, View> entry : this.main.entrySet()) {
            base = "players." + String.valueOf(entry.getKey()) + ".main.";
            sv.set(base + "sort", (Object) entry.getValue().sort.name());
            sv.set(base + "filter", (Object) entry.getValue().filter);
        }
        for (Map.Entry<UUID, ItemView> entry : this.selectItem.entrySet()) {
            base = "players." + String.valueOf(entry.getKey()) + ".select.";
            sv.set(base + "alpha", (Object) ((ItemView) entry.getValue()).alpha.name());
            sv.set(base + "filter", (Object) ((ItemView) entry.getValue()).filter);
        }
        this.plugin.cfg().saveSaves();
    }

    public void loadAllPrefs() {
        FileConfiguration sv = this.plugin.cfg().saves();
        if (!sv.isConfigurationSection("players")) {
            return;
        }
        for (String puid : sv.getConfigurationSection("players").getKeys(false)) {
            ConfigurationSection iSec;
            UUID u;
            try {
                u = UUID.fromString(puid);
            } catch (IllegalArgumentException ex) {
                this.plugin.getLogger().warning("Skipping invalid player UUID in saves.yml: " + puid);
                continue;
            }
            ConfigurationSection vSec = sv.getConfigurationSection("players." + puid + ".main");
            if (vSec != null) {
                View v = new View();
                try {
                    v.sort = SortType.valueOf(vSec.getString("sort", "MOST_PAID"));
                } catch (Exception exception) {
                    // empty catch block
                }
                v.filter = vSec.getString("filter", "All");
                this.main.put(u, v);
            }
            if ((iSec = sv.getConfigurationSection("players." + puid + ".select")) == null)
                continue;
            ItemView iv = new ItemView();
            try {
                iv.alpha = AlphaSort.valueOf(iSec.getString("alpha", "A_Z"));
            } catch (Exception exception) {
                // empty catch block
            }
            iv.filter = iSec.getString("filter", "All");
            this.selectItem.put(u, iv);
        }
    }

    public static class View {
        public int page = 0;
        public SortType sort = SortType.MOST_PAID;
        public String filter = "All";
        public String search = "";
    }

    public static class ItemView {
        public int page = 0;
        public AlphaSort alpha = AlphaSort.A_Z;
        public String filter = "All";
        public String search = "";
    }
}
