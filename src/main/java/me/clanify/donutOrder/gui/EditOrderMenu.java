/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.Utils;
import me.clanify.donutOrder.data.Order;
import me.clanify.donutOrder.gui.CollectItemsMenu;
import me.clanify.donutOrder.gui.DeleteOrderMenu;
import me.clanify.donutOrder.gui.GuiVariant;
import me.clanify.donutOrder.gui.MenuOwner;
import me.clanify.donutOrder.gui.YourOrdersMenu;
import me.clanify.donutOrder.util.TaskUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

public class EditOrderMenu
        implements InventoryHolder,
        MenuOwner {
    private static final String META_SUPPRESS_CLOSE = "donutorder.suppressClose";
    private final DonutOrder pl;
    private final Player p;
    private final Order order;
    private Inventory inv;
    // Anti-exploit: Click cooldown
    private long lastClickTime = 0;
    private static final long CLICK_COOLDOWN_MS = 300;

    public EditOrderMenu(DonutOrder pl, Player p, Order order) {
        this.pl = pl;
        this.p = p;
        this.order = order;
    }

    public Inventory getInventory() {
        return this.inv;
    }

    public void open() {
        if (this.pl.bedrock().isBedrockPlayer(this.p)) {
            this.pl.bedrock().sendOrderActionMenu(this.p, this.order);
            return;
        }
        boolean canCancel;
        int rows = this.pl.cfg().rows("edit", 3);
        this.inv = Bukkit.createInventory((InventoryHolder) this, (int) (rows * 9),
                (String) this.pl.cfg().title("edit", "&#44b3ffOrders -> Edit Order"));
        int[] fillerSlots = new int[] { 0, 1, 2, 9, 11, 18, 19, 20 };
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(Utils.formatColors("&7 "));
            filler.setItemMeta(fm);
        }
        for (int s : fillerSlots) {
            if (s < 0 || s >= this.inv.getSize())
                continue;
            this.inv.setItem(s, filler);
        }
        HashMap<String, String> ph = new HashMap<String, String>();
        ph.put("item", this.order.key.displayName());
        ph.put("requested", Utils.abbr(this.order.requested));
        ph.put("delivered", Utils.abbr(this.order.delivered));
        ph.put("paid", Utils.abbr((double) this.order.delivered * this.order.priceEach));
        ph.put("total", Utils.abbr(this.order.totalPrice()));
        ph.put("player", this.p.getName());
        ph.put("price_each", Utils.abbr(this.order.priceEach));
        ArrayList<String> baseLore = new ArrayList<String>(
                List.of("&7Requested: &f{requested}", "&7Delivered: &f{delivered}", "&7Paid: &f${paid}/&f${total}"));
        List<String> enchLore = this.order.key.enchantLoreLines("&7");
        if (!enchLore.isEmpty()) {
            baseLore.add("&7");
            baseLore.addAll(enchLore);
        }
        ItemStack target = this.pl.cfg().dynamicItem(this.order.key.material, "gui.edit.items.target", "&f{item}",
                baseLore, ph);
        target = GuiVariant.merge(target, this.order.key.buildIcon());
        this.inv.setItem(10, target);
        boolean hasCollect = !this.order.storage.isEmpty();
        boolean bl = canCancel = !this.order.completed;
        if (canCancel) {
            this.inv.setItem(13, this.pl.cfg().button("gui.edit.items.cancel", "RED_TERRACOTTA", "&cCANCEL",
                    List.of("&fClick to cancel this order")));
        }
        if (hasCollect) {
            int slot = canCancel ? 15 : 13;
            this.inv.setItem(slot, this.pl.cfg().button("gui.edit.items.collect", "CHEST", "&fCOLLECT",
                    List.of("&fClick to collect items")));
        }
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
        // Anti-exploit: Click cooldown
        long now = System.currentTimeMillis();
        if (now - lastClickTime < CLICK_COOLDOWN_MS)
            return;
        lastClickTime = now;

        int slot = e.getSlot();
        if (slot == 13 && !this.order.completed) {
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            this.p.setMetadata(META_SUPPRESS_CLOSE,
                    (MetadataValue) new FixedMetadataValue((Plugin) this.pl, (Object) true));
            new DeleteOrderMenu(this.pl, this.p, this.order).open();
            return;
        }
        if (slot == 15 && !this.order.storage.isEmpty()
                || slot == 13 && this.order.completed && !this.order.storage.isEmpty()) {
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            this.p.setMetadata(META_SUPPRESS_CLOSE,
                    (MetadataValue) new FixedMetadataValue((Plugin) this.pl, (Object) true));
            new CollectItemsMenu(this.pl, this.p, this.order).open();
        }
    }

    @Override
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() != this) {
            return;
        }
        if (this.p.hasMetadata(META_SUPPRESS_CLOSE)) {
            this.p.removeMetadata(META_SUPPRESS_CLOSE, (Plugin) this.pl);
            return;
        }
        TaskUtil.runEntityLater((Plugin) this.pl, (Entity) this.p, () -> new YourOrdersMenu(this.pl, this.p).open(),
                1L);
    }

    @Override
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() != this) {
            return;
        }
        // Block any drag involving the top inventory
        for (int slot : e.getRawSlots()) {
            if (slot < e.getView().getTopInventory().getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
