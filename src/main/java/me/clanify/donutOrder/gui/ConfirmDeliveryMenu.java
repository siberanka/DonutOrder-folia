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
 *  org.bukkit.plugin.Plugin
 */
package me.clanify.donutOrder.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.Utils;
import me.clanify.donutOrder.data.Order;
import me.clanify.donutOrder.gui.DeliverItemsMenu;
import me.clanify.donutOrder.gui.GuiVariant;
import me.clanify.donutOrder.gui.MenuOwner;
import me.clanify.donutOrder.gui.OrdersMainMenu;
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
import org.bukkit.plugin.Plugin;

public class ConfirmDeliveryMenu
        implements InventoryHolder,
        MenuOwner {
    private final DonutOrder pl;
    private final Player p;
    private final Order order;
    private final List<ItemStack> accepted;
    private final int acceptedAmount;
    private Inventory inv;
    private volatile boolean finalized = false;
    private long lastClickTime = 0;
    private static final long CLICK_COOLDOWN_MS = 300;

    public ConfirmDeliveryMenu(DonutOrder pl, Player p, Order order, List<ItemStack> accepted, int acceptedAmount) {
        this.pl = pl;
        this.p = p;
        this.order = order;
        this.accepted = accepted;
        this.acceptedAmount = acceptedAmount;
    }

    public Inventory getInventory() {
        return this.inv;
    }

    public void open() {
        int rows = this.pl.cfg().rows("confirm", 3);
        this.inv = Bukkit.createInventory((InventoryHolder) this, (int) (rows * 9),
                (String) this.pl.cfg().title("confirm", "&#44b3ffOrders -> Confirm Delivery"));
        int cancelSlot = 11;
        int summarySlot = 13;
        int confirmSlot = 15;
        String ownerName = Bukkit.getOfflinePlayer((UUID) this.order.owner).getName();
        if (ownerName == null) {
            ownerName = "Unknown";
        }
        HashMap<String, String> ph = new HashMap<String, String>();
        ph.put("player", ownerName);
        ph.put("item", this.order.key.displayName());
        ph.put("amount", Utils.abbr(this.acceptedAmount));
        ph.put("price_each", Utils.abbr(this.order.priceEach));
        ph.put("receive", Utils.abbr((double) this.acceptedAmount * this.order.priceEach));
        ArrayList<String> summaryLore = new ArrayList<String>(
                List.of("{amount} {item}", "${price_each} &7each", "", "&7You're delivering {amount} {item}"));
        List<String> ench = this.order.key.enchantLoreLines("&7");
        if (!ench.isEmpty()) {
            summaryLore.add("&7");
            summaryLore.addAll(ench);
        }
        ItemStack summary = this.pl.cfg().dynamicItem(this.order.key.material, "gui.confirm.items.summary",
                "&f{player} Order", summaryLore, ph);
        summary = GuiVariant.merge(summary, this.order.key.buildIcon());
        this.inv.setItem(summarySlot, summary);
        this.inv.setItem(cancelSlot, this.pl.cfg().button("gui.confirm.items.cancel", "RED_STAINED_GLASS_PANE",
                "&cCANCEL", List.of("&fClick to go back")));
        this.inv.setItem(confirmSlot, this.pl.cfg().dynamicItem(Material.LIME_STAINED_GLASS_PANE,
                "gui.confirm.items.confirm", "&aCONFIRM", List.of("&fClick to deliver items", "({receive})"), ph));
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
        if (now - lastClickTime < CLICK_COOLDOWN_MS) {
            return;
        }
        lastClickTime = now;
        int slot = e.getSlot();
        if (slot == 11) {
            if (this.finalized)
                return;
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            this.finalized = true;
            for (ItemStack is : this.accepted) {
                this.giveBackOrDrop(is);
            }
            TaskUtil.runEntityLater((Plugin) this.pl, (Entity) this.p,
                    () -> new DeliverItemsMenu(this.pl, this.p, this.order).open(), 1L);
            return;
        }
        if (slot == 15) {
            if (this.finalized)
                return;

            // Anti-exploit: Check if order is still valid before delivery
            if (this.order.completed || this.order.canceled) {
                this.p.sendMessage(me.clanify.donutOrder.Utils.formatColors("&cThis order is no longer available."));
                this.finalized = true;
                for (ItemStack is : this.accepted) {
                    this.giveBackOrDrop(is);
                }
                TaskUtil.runEntityLater((Plugin) this.pl, (Entity) this.p,
                        () -> new OrdersMainMenu(this.pl, this.p).open(), 1L);
                return;
            }

            this.pl.cfg().play(this.p, "sounds.confirm", "ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f, 1.2f);
            this.finalized = true;
            this.pl.orders().applyDelivery(this.order, this.accepted, this.acceptedAmount, this.p.getUniqueId());
            if (this.order.remainingAmount() > 0) {
                TaskUtil.runEntityLater((Plugin) this.pl, (Entity) this.p,
                        () -> new DeliverItemsMenu(this.pl, this.p, this.order).open(), 1L);
            } else {
                TaskUtil.runEntityLater((Plugin) this.pl, (Entity) this.p,
                        () -> new OrdersMainMenu(this.pl, this.p).open(), 1L);
            }
        }
    }

    @Override
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() != this) {
            return;
        }
        if (!this.finalized) {
            for (ItemStack is : this.accepted) {
                this.giveBackOrDrop(is);
            }
            TaskUtil.runEntityLater((Plugin) this.pl, (Entity) this.p, () -> new OrdersMainMenu(this.pl, this.p).open(),
                    1L);
        }
    }

    private void giveBackOrDrop(ItemStack is) {
        HashMap<Integer, ItemStack> leftovers = this.p.getInventory().addItem(new ItemStack[] { is });
        leftovers.values().forEach(rem -> this.p.getWorld().dropItemNaturally(this.p.getLocation(), rem));
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
