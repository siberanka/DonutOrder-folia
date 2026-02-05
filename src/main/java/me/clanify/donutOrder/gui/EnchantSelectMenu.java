/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemFlag
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.EnchantmentStorageMeta
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.metadata.FixedMetadataValue
 *  org.bukkit.metadata.MetadataValue
 *  org.bukkit.plugin.Plugin
 */
package me.clanify.donutOrder.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.Utils;
import me.clanify.donutOrder.gui.MenuOwner;
import me.clanify.donutOrder.gui.NewOrderMenu;
import me.clanify.donutOrder.gui.SelectItemMenu;
import me.clanify.donutOrder.store.EnchantmentsManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

public class EnchantSelectMenu
        implements InventoryHolder,
        MenuOwner {
    private final DonutOrder pl;
    private final Player p;
    private final ItemStack base;
    private Inventory inv;
    private final Map<Enchantment, Integer> selected = new LinkedHashMap<Enchantment, Integer>();
    private List<EnchantmentsManager.EnchantOption> options = List.of();
    private List<Integer> gridSlots = List.of();
    private int page = 0;

    public EnchantSelectMenu(DonutOrder pl, Player p, ItemStack base) {
        this.pl = pl;
        this.p = p;
        this.base = base.clone();
    }

    public Inventory getInventory() {
        return this.inv;
    }

    private int rows() {
        return Math.max(1, this.pl.ench().gui().rows);
    }

    private void buildGridSlots() {
        int size = this.rows() * 9;
        int bottomStart = (this.rows() - 1) * 9;
        EnchantmentsManager.GUI gui = this.pl.ench().gui();
        HashSet<Integer> reserved = new HashSet<Integer>(
                List.of(Integer.valueOf(gui.slotItem), Integer.valueOf(gui.slotCancel), Integer.valueOf(gui.slotPrev),
                        Integer.valueOf(gui.slotNext), Integer.valueOf(gui.slotConfirm)));
        ArrayList<Integer> list = new ArrayList<Integer>();
        for (int i = 0; i < size; ++i) {
            if (i >= bottomStart || reserved.contains(i))
                continue;
            list.add(i);
        }
        this.gridSlots = list;
    }

    private boolean canAnyEnchant(ItemStack item) {
        for (Enchantment e : Enchantment.values()) {
            try {
                if (e == null || !e.canEnchantItem(item))
                    continue;
                return true;
            } catch (Throwable throwable) {
                // empty catch block
            }
        }
        return false;
    }

    public void open() {
        if (!this.pl.ench().hasOptionsFor(this.base.getType()) || !this.canAnyEnchant(this.base)) {
            this.p.setMetadata("donutorder.tmpChosenStack",
                    (MetadataValue) new FixedMetadataValue((Plugin) this.pl, (Object) this.base.clone()));
            this.pl.chat().session((UUID) this.p.getUniqueId()).chosenItem = this.base.getType().name();
            new NewOrderMenu(this.pl, this.p).open();
            return;
        }
        this.inv = Bukkit.createInventory((InventoryHolder) this, (int) (this.rows() * 9),
                (String) Utils.formatColors(this.pl.ench().gui().title));
        this.buildGridSlots();
        ArrayList<EnchantmentsManager.EnchantOption> all = new ArrayList<EnchantmentsManager.EnchantOption>(
                this.pl.ench().optionsFor(this.base.getType()));
        all.removeIf(opt -> opt.ench == null || !opt.ench.canEnchantItem(this.base));
        this.options = all;
        this.render();
        this.p.openInventory(this.inv);
        this.pl.cfg().play(this.p, "sounds.open", "BLOCK_CHEST_OPEN", 0.7f, 1.0f);
    }

    private void render() {
        ItemStack filler;
        this.inv.clear();
        EnchantmentsManager.GUI gui = this.pl.ench().gui();
        EnchantmentsManager.Messages msg = this.pl.ench().messages();
        ItemStack preview = this.base.clone();
        ItemMeta pm = preview.getItemMeta();
        if (pm != null) {
            pm.removeItemFlags(new ItemFlag[] { ItemFlag.HIDE_ENCHANTS });
            for (Map.Entry<Enchantment, Integer> en : this.selected.entrySet()) {
                pm.addEnchant(en.getKey(), en.getValue().intValue(), true);
            }
            preview.setItemMeta(pm);
        }
        this.inv.setItem(gui.slotItem, preview);
        int perPage = this.gridSlots.size();
        int maxPageDefined = this.pl.ench().maxPage(this.options);
        int autoMax = Math.max(0, (this.options.size() - 1) / Math.max(1, perPage));
        int maxPage = Math.max(maxPageDefined, autoMax);
        if (this.page > maxPage) {
            this.page = maxPage;
        }
        if (this.page < 0) {
            this.page = 0;
        }
        for (EnchantmentsManager.EnchantOption opt : this.options) {
            int optPage = Math.max(1, opt.page);
            if (optPage - 1 != this.page || opt.slot == null || opt.slot < 0 || opt.slot >= this.gridSlots.size())
                continue;
            boolean selectedAlready = this.selected.containsKey(opt.ench)
                    && Objects.equals(this.selected.get(opt.ench), opt.level);
            boolean conflicts = !selectedAlready && this.conflictsWithCurrent(opt.ench);
            String stateLine = msg.loreSelect;
            if (selectedAlready) {
                stateLine = msg.loreSelected;
            } else if (conflicts) {
                stateLine = msg.loreCannot;
            }
            this.inv.setItem(this.gridSlots.get(opt.slot).intValue(), this.makeBookOption(opt, List.of(stateLine)));
        }
        this.inv.setItem(gui.slotPrev, this.makeButton(Material.ARROW, "&fPrevious Page", List.of()));
        this.inv.setItem(gui.slotNext, this.makeButton(Material.ARROW, "&fNext Page", List.of()));
        this.inv.setItem(gui.slotConfirm, this.makeButton(gui.confirmMat, gui.confirmName, gui.confirmLore));
        this.inv.setItem(gui.slotCancel, this.makeButton(gui.cancelMat, gui.cancelName, gui.cancelLore));
        if (gui.fillerEnabled) {
            int bottom;
            filler = this.makeButton(gui.fillerMat, gui.fillerName, List.of());
            for (int s = bottom = (this.rows() - 1) * 9; s < bottom + 9; ++s) {
                if (s == gui.slotPrev || s == gui.slotNext || s == gui.slotConfirm || s == gui.slotCancel
                        || this.inv.getItem(s) != null)
                    continue;
                this.inv.setItem(s, filler);
            }
        }
        if (gui.extraFillerEnabled) {
            filler = this.makeButton(gui.extraFillerMat, gui.extraFillerName, List.of());
            for (Integer fs : gui.extraFillerSlots) {
                if (fs == null || fs < 0 || fs >= this.rows() * 9 || fs == gui.slotPrev || fs == gui.slotNext
                        || fs == gui.slotConfirm || fs == gui.slotCancel || fs == gui.slotItem
                        || this.inv.getItem(fs.intValue()) != null)
                    continue;
                this.inv.setItem(fs.intValue(), filler);
            }
        }
    }

    private boolean conflictsWithCurrent(Enchantment next) {
        for (Enchantment e : this.selected.keySet()) {
            try {
                if (!next.conflictsWith(e) && !e.conflictsWith(next))
                    continue;
                return true;
            } catch (Throwable throwable) {
            }
        }
        return false;
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
        EnchantmentsManager.GUI gui = this.pl.ench().gui();
        int s = e.getSlot();
        if (s == gui.slotPrev) {
            this.page = Math.max(0, this.page - 1);
            this.pl.cfg().play(this.p, "sounds.page", "UI_BUTTON_CLICK", 1.0f, 1.1f);
            this.render();
            return;
        }
        if (s == gui.slotNext) {
            ++this.page;
            this.pl.cfg().play(this.p, "sounds.page", "UI_BUTTON_CLICK", 1.0f, 1.1f);
            this.render();
            return;
        }
        if (s == gui.slotCancel) {
            this.pl.cfg().play(this.p, "sounds.cancel", "BLOCK_NOTE_BLOCK_BASS", 1.0f, 0.8f);
            new SelectItemMenu(this.pl, this.p).open();
            return;
        }
        if (s == gui.slotConfirm) {
            ItemStack out = this.base.clone();
            ItemMeta om = out.getItemMeta();
            if (om != null) {
                om.removeItemFlags(new ItemFlag[] { ItemFlag.HIDE_ENCHANTS });
                for (Map.Entry<Enchantment, Integer> en : this.selected.entrySet()) {
                    om.addEnchant(en.getKey(), en.getValue().intValue(), true);
                }
                out.setItemMeta(om);
            }
            if (this.selected.isEmpty()) {
                this.p.setMetadata("donutorder.skipEnchantOnce",
                        (MetadataValue) new FixedMetadataValue((Plugin) this.pl, (Object) true));
            }
            this.pl.chat().session((UUID) this.p.getUniqueId()).chosenItem = this.base.getType().name();
            this.p.setMetadata("donutorder.tmpChosenStack",
                    (MetadataValue) new FixedMetadataValue((Plugin) this.pl, (Object) out));
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            new NewOrderMenu(this.pl, this.p).open();
            return;
        }
        int gridIndex = this.gridSlots.indexOf(s);
        if (gridIndex >= 0) {
            for (EnchantmentsManager.EnchantOption opt : this.options) {
                int optPage = Math.max(1, opt.page);
                if (optPage - 1 != this.page || opt.slot == null || opt.slot != gridIndex)
                    continue;
                this.toggle(opt);
                return;
            }
        }
    }

    private void toggle(EnchantmentsManager.EnchantOption opt) {
        boolean already;
        boolean bl = already = this.selected.containsKey(opt.ench)
                && Objects.equals(this.selected.get(opt.ench), opt.level);
        if (already) {
            this.selected.remove(opt.ench);
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
        } else if (!this.conflictsWithCurrent(opt.ench)) {
            this.selected.put(opt.ench, opt.level);
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
        } else {
            this.pl.cfg().play(this.p, "sounds.cancel", "BLOCK_NOTE_BLOCK_BASS", 1.0f, 0.8f);
        }
        this.render();
    }

    @Override
    public void onClose(InventoryCloseEvent e) {
    }

    private ItemStack makeButton(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            if (name != null) {
                im.setDisplayName(Utils.formatColors(name));
            }
            if (lore != null && !lore.isEmpty()) {
                ArrayList<String> ll = new ArrayList<String>();
                for (String line : lore) {
                    ll.add(Utils.formatColors(line));
                }
                im.setLore(ll);
            }
            it.setItemMeta(im);
        }
        return it;
    }

    private ItemStack makeBookOption(EnchantmentsManager.EnchantOption opt, List<String> lore) {
        ItemStack it = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta sm = (EnchantmentStorageMeta) it.getItemMeta();
        if (sm != null) {
            try {
                sm.addStoredEnchant(opt.ench, opt.level, true);
            } catch (Throwable throwable) {
                // empty catch block
            }
            if (lore != null && !lore.isEmpty()) {
                ArrayList<String> ll = new ArrayList<String>();
                for (String line : lore) {
                    ll.add(Utils.formatColors(line));
                }
                sm.setLore(ll);
            }
            it.setItemMeta((ItemMeta) sm);
        }
        return it;
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
