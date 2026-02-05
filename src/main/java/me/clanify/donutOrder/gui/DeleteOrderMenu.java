/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.plugin.Plugin
 */
package me.clanify.donutOrder.gui;

import java.util.List;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.data.Order;
import me.clanify.donutOrder.gui.EditOrderMenu;
import me.clanify.donutOrder.gui.MenuOwner;
import me.clanify.donutOrder.gui.YourOrdersMenu;
import me.clanify.donutOrder.util.TaskUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

public class DeleteOrderMenu
        implements InventoryHolder,
        MenuOwner {
    private final DonutOrder pl;
    private final Player p;
    private final Order order;
    private Inventory inv;

    public DeleteOrderMenu(DonutOrder pl, Player p, Order order) {
        this.pl = pl;
        this.p = p;
        this.order = order;
    }

    public Inventory getInventory() {
        return this.inv;
    }

    public void open() {
        int rows = this.pl.cfg().rows("delete", 3);
        this.inv = Bukkit.createInventory((InventoryHolder) this, (int) (rows * 9),
                (String) this.pl.cfg().title("delete", "&#44b3ffOrders -> Delete Order"));
        this.inv.setItem(this.pl.cfg().slot("gui.delete.items.back", 10), this.pl.cfg().button("gui.delete.items.back",
                "RED_STAINED_GLASS_PANE", "&cBack", List.of("&fClick to go back")));
        this.inv.setItem(this.pl.cfg().slot("gui.delete.items.confirm", 16),
                this.pl.cfg().button("gui.delete.items.confirm", "LIME_STAINED_GLASS_PANE", "&aCONFIRM",
                        List.of("&fClick to delete this order!")));
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
        int slot = e.getSlot();
        int back = this.pl.cfg().slot("gui.delete.items.back", 10);
        int confirm = this.pl.cfg().slot("gui.delete.items.confirm", 16);
        if (slot == back) {
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            new EditOrderMenu(this.pl, this.p, this.order).open();
            return;
        }
        if (slot == confirm) {
            this.pl.cfg().play(this.p, "sounds.confirm", "ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f, 1.2f);
            this.pl.orders().cancel(this.order);
            new YourOrdersMenu(this.pl, this.p).open();
        }
    }

    @Override
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() != this) {
            return;
        }
        Player pp = (Player) e.getPlayer();
        TaskUtil.runEntityLater((Plugin) this.pl, (Entity) pp, () -> {
            InventoryHolder holder = pp.getOpenInventory().getTopInventory().getHolder();
            if (!(holder instanceof MenuOwner)) {
                new YourOrdersMenu(this.pl, pp).open();
            }
        }, 1L);
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
