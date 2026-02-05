/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.metadata.FixedMetadataValue
 *  org.bukkit.metadata.MetadataValue
 *  org.bukkit.plugin.Plugin
 */
package me.clanify.donutOrder.gui;

import java.lang.invoke.CallSite;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.Utils;
import me.clanify.donutOrder.catalog.ItemCatalog;
import me.clanify.donutOrder.data.AlphaSort;
import me.clanify.donutOrder.data.ItemKey;
import me.clanify.donutOrder.gui.GuiVariant;
import me.clanify.donutOrder.gui.MenuOwner;
import me.clanify.donutOrder.gui.NewOrderMenu;
import me.clanify.donutOrder.store.PlayerStateManager;
import me.clanify.donutOrder.util.TaskUtil;
import me.clanify.donutOrder.utils.SignInputUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

public class SelectItemMenu
        implements InventoryHolder,
        MenuOwner {
    private final DonutOrder pl;
    private final Player p;
    private Inventory inv;
    private final List<ItemCatalog.Entry> pageEntries = new ArrayList<ItemCatalog.Entry>();
    private int page = 0;
    private boolean suppressClose = false;

    public SelectItemMenu(DonutOrder pl, Player p) {
        this.pl = pl;
        this.p = p;
    }

    public Inventory getInventory() {
        return this.inv;
    }

    private int perPage() {
        int rows = this.pl.cfg().rows("select", 6);
        return (rows - 1) * 9;
    }

    private List<ItemCatalog.Entry> computeList() {
        Set<Material> allow;
        PlayerStateManager.ItemView v = this.pl.state().items(this.p.getUniqueId());
        if (v.filter == null || v.filter.isBlank()) {
            v.filter = "All";
        }
        if (v.alpha == null) {
            v.alpha = AlphaSort.A_Z;
        }
        List<ItemCatalog.Entry> all = ItemCatalog.build(this.pl);
        if (!"All".equalsIgnoreCase(v.filter) && (allow = this.pl.filters().resolve(v.filter)) != null
                && !allow.isEmpty()) {
            all.removeIf(e -> !allow.contains(e.base));
        }
        if (v.search != null && !v.search.isBlank()) {
            String s = v.search.toLowerCase(Locale.ENGLISH);
            all.removeIf(e -> !e.search.contains(s));
            Comparator<ItemCatalog.Entry> rank = Comparator.comparingInt(e -> {
                String d = e.display.toLowerCase(Locale.ENGLISH);
                if (d.equals(s)) {
                    return 0;
                }
                if (d.startsWith(s)) {
                    return 1;
                }
                return 2;
            });
            Comparator<ItemCatalog.Entry> alpha = Comparator.comparing(e -> e.display);
            if (v.alpha == AlphaSort.Z_A) {
                alpha = alpha.reversed();
            }
            all.sort(rank.thenComparing(alpha));
        } else {
            Comparator<ItemCatalog.Entry> alpha = Comparator.comparing(e -> e.display);
            if (v.alpha == AlphaSort.Z_A) {
                alpha = alpha.reversed();
            }
            all.sort(alpha);
        }
        return all;
    }

    public void open() {
        int from;
        PlayerStateManager.ItemView v = this.pl.state().items(this.p.getUniqueId());
        int rows = this.pl.cfg().rows("select", 6);
        List<ItemCatalog.Entry> list = this.computeList();
        int per = this.perPage();
        int max = Math.max(0, (list.size() - 1) / Math.max(1, per));
        if (this.page > max) {
            this.page = max;
        }
        if (this.page < 0) {
            this.page = 0;
        }
        if ((from = this.page * per) >= list.size() && this.page > 0) {
            this.page = max;
            from = this.page * per;
        }
        this.inv = Bukkit.createInventory((InventoryHolder) this, (int) (rows * 9),
                (String) this.pl.cfg().title("select", "&#44b3ffOrders -> Select Item"));
        this.pageEntries.clear();
        int to = Math.min(from + per, list.size());
        for (int i = from; i < to; ++i) {
            ItemMeta im;
            List<String> enchLore;
            ItemCatalog.Entry ce = list.get(i);
            this.pageEntries.add(ce);
            Map<String, String> ph = Map.of("item", ce.display);
            // Use null for name to force vanilla sorting/display
            ItemStack ui = this.pl.cfg().dynamicItem(ce.base, "gui.select.items.item", null,
                    List.of("&fClick to select"), ph);
            ItemKey key = ItemKey.fromStack(ce.stack);
            if (key != null && !(enchLore = key.enchantLoreLines("&7")).isEmpty() && (im = ui.getItemMeta()) != null) {
                List<String> lore = im.getLore();
                if (lore == null) {
                    lore = new ArrayList<String>();
                }
                lore.add(Utils.formatColors("&7"));
                lore.addAll(Utils.formatColors(enchLore));
                im.setLore(lore);
                ui.setItemMeta(im);
            }
            ui = GuiVariant.merge(ui, ce.stack);
            this.inv.setItem(i - from, ui);
        }
        int prevSlot = this.pl.cfg().slot("gui.select.items.prev", (rows - 1) * 9);
        int nextSlot = this.pl.cfg().slot("gui.select.items.next", rows * 9 - 1);
        int sortSlot = this.pl.cfg().slot("gui.select.items.sort", (rows - 1) * 9 + 3);
        int filterSlot = this.pl.cfg().slot("gui.select.items.filter", (rows - 1) * 9 + 4);
        int searchSlot = this.pl.cfg().slot("gui.select.items.search", (rows - 1) * 9 + 5);
        this.inv.setItem(prevSlot,
                this.pl.cfg().button("gui.select.items.prev", "ARROW", "&fPrevious Page", List.of()));
        this.inv.setItem(nextSlot, this.pl.cfg().button("gui.select.items.next", "ARROW", "&fNext Page", List.of()));
        String selPrefix = this.pl.cfg().selectedPrefix("select");
        String unsPrefix = this.pl.cfg().unselectedPrefix("select");
        ItemStack sortBtn = this.pl.cfg().button("gui.select.items.sort", "CAULDRON", "&fSort", List.of());
        ItemMeta sm = sortBtn.getItemMeta();
        if (sm != null) {
            sm.setLore(List
                    .of((v.alpha == AlphaSort.A_Z ? selPrefix : unsPrefix) + "A-Z",
                            (v.alpha == AlphaSort.Z_A ? selPrefix : unsPrefix) + "Z-A")
                    .stream().map(Utils::formatColors).toList());
            sortBtn.setItemMeta(sm);
        }
        this.inv.setItem(sortSlot, sortBtn);
        ArrayList<String> cats = new ArrayList<String>();
        cats.add("All");
        cats.addAll(this.pl.filters().categoryNames());
        ItemStack filterBtn = this.pl.cfg().button("gui.select.items.filter", "HOPPER", "&fFilter", List.of());
        ItemMeta fm = filterBtn.getItemMeta();
        if (fm != null) {
            ArrayList<String> lines = new ArrayList<>();
            for (String name : cats) {
                String translated = this.pl.lang().getFilterName(name);
                lines.add((name.equalsIgnoreCase(v.filter) ? selPrefix : unsPrefix) + translated);
            }
            fm.setLore(lines.stream().map(Utils::formatColors).toList());
            filterBtn.setItemMeta(fm);
        }
        this.inv.setItem(filterSlot, filterBtn);
        this.inv.setItem(searchSlot,
                this.pl.cfg().button("gui.select.items.search", "OAK_SIGN", "&fSearch", List.of("&fClick to search")));
        this.p.openInventory(this.inv);
        this.pl.cfg().play(this.p, "sounds.open", "BLOCK_CHEST_OPEN", 0.7f, 1.0f);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null) {
            return;
        }
        if (e.getClickedInventory().getHolder() != this) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        PlayerStateManager.ItemView v = this.pl.state().items(this.p.getUniqueId());
        int rows = this.pl.cfg().rows("select", 6);
        int prevSlot = this.pl.cfg().slot("gui.select.items.prev", (rows - 1) * 9);
        int nextSlot = this.pl.cfg().slot("gui.select.items.next", rows * 9 - 1);
        int sortSlot = this.pl.cfg().slot("gui.select.items.sort", (rows - 1) * 9 + 3);
        int filterSlot = this.pl.cfg().slot("gui.select.items.filter", (rows - 1) * 9 + 4);
        int searchSlot = this.pl.cfg().slot("gui.select.items.search", (rows - 1) * 9 + 5);
        int slot = e.getSlot();
        List<ItemCatalog.Entry> all = this.computeList();
        int per = this.perPage();
        int max = Math.max(0, (all.size() - 1) / Math.max(1, per));
        if (slot == prevSlot) {
            this.suppressClose = true;
            this.page = Math.max(0, this.page - 1);
            this.pl.cfg().play(this.p, "sounds.page", "UI_BUTTON_CLICK", 1.0f, 1.1f);
            this.open();
            return;
        }
        if (slot == nextSlot) {
            this.suppressClose = true;
            this.page = Math.min(max, this.page + 1);
            this.pl.cfg().play(this.p, "sounds.page", "UI_BUTTON_CLICK", 1.0f, 1.1f);
            this.open();
            return;
        }
        if (slot == sortSlot) {
            this.suppressClose = true;
            v.alpha = v.alpha.toggle();
            this.pl.state().saveAllPrefs();
            this.page = 0;
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            this.open();
            return;
        }
        if (slot == filterSlot) {
            this.suppressClose = true;
            ArrayList<String> cats = new ArrayList<String>();
            cats.add("All");
            cats.addAll(this.pl.filters().categoryNames());
            int idx = Math.max(0, cats.indexOf(v.filter));
            v.filter = (String) cats.get((idx + 1) % cats.size());
            this.pl.state().saveAllPrefs();
            this.page = 0;
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            this.open();
            return;
        }
        if (slot == searchSlot) {
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            this.suppressClose = true;
            this.p.closeInventory();
            ConfigurationSection sec = this.pl.cfg().cfg().getConfigurationSection("search-sign");
            SignInputUtil.openFromConfig(this.pl, this.p, sec, input -> {
                String trimmed;
                if (!this.p.isOnline()) {
                    return;
                }
                String string = trimmed = input == null ? "" : input.trim();
                if (trimmed.equals("-")) {
                    trimmed = "";
                }
                v.search = trimmed.isEmpty() ? null : trimmed;
                this.page = 0;
                this.pl.state().saveAllPrefs();
                new SelectItemMenu(this.pl, this.p).open();
            });
            return;
        }
        int gridMax = Math.min(per, this.pageEntries.size());
        if (slot >= 0 && slot < gridMax) {
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            ItemCatalog.Entry chosen = this.pageEntries.get(slot);
            this.p.setMetadata("donutorder.tmpChosenStack",
                    (MetadataValue) new FixedMetadataValue((Plugin) this.pl, (Object) chosen.stack));
            this.pl.chat().session((UUID) this.p.getUniqueId()).chosenItem = chosen.base.name();
            v.search = null;
            this.page = 0;
            this.pl.state().saveAllPrefs();
            this.suppressClose = true;
            new NewOrderMenu(this.pl, this.p).open();
        }
    }

    @Override
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() != this) {
            return;
        }
        if (this.suppressClose) {
            this.suppressClose = false;
            return;
        }
        PlayerStateManager.ItemView v = this.pl.state().items(this.p.getUniqueId());
        v.search = null;
        this.page = 0;
        this.pl.state().saveAllPrefs();
        TaskUtil.runEntityLater((Plugin) this.pl, (Entity) this.p, () -> new NewOrderMenu(this.pl, this.p).open(), 1L);
    }

    @Override
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() != this) {
            return;
        }
        for (int slot : e.getRawSlots()) {
            if (slot < e.getView().getTopInventory().getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
