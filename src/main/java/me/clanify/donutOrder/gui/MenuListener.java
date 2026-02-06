
/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.event.inventory.InventoryDragEvent
 *  org.bukkit.inventory.InventoryHolder
 */
package me.clanify.donutOrder.gui;

import java.util.logging.Level;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.Utils;
import me.clanify.donutOrder.gui.MenuOwner;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class MenuListener implements Listener {
    private final DonutOrder plugin;

    public MenuListener(DonutOrder pl) {
        this.plugin = pl;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST) // Handle early to set defaults
    public void onClick(InventoryClickEvent e) {
        try {
            InventoryHolder inventoryHolder = e.getInventory().getHolder();
            if (!(inventoryHolder instanceof MenuOwner)) {
                return;
            }

            // SECURITY 1: Default to cancelled
            e.setCancelled(true);

            // SECURITY 2: Anti-Dupe / Global Block List
            // COLLECT_TO_CURSOR (Double Click) is extremely risky for custom GUIs.
            // Block it globally unless we ever find a really good reason to allow it.
            if (e.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
                return;
            }

            // SECURITY 3: Unknown actions
            if (e.getAction() == org.bukkit.event.inventory.InventoryAction.UNKNOWN) {
                e.setCancelled(true);
                return;
            }

            MenuOwner owner = (MenuOwner) inventoryHolder;
            owner.onClick(e);
        } catch (Throwable t) {
            this.handleException(e.getWhoClicked(), e, t);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        try {
            InventoryHolder inventoryHolder = e.getInventory().getHolder();
            if (inventoryHolder instanceof MenuOwner) {
                MenuOwner owner = (MenuOwner) inventoryHolder;
                owner.onClose(e);
            }
        } catch (Throwable t) {
            this.handleException(e.getPlayer(), e, t);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        try {
            InventoryHolder inventoryHolder = e.getInventory().getHolder();
            if (!(inventoryHolder instanceof MenuOwner)) {
                return;
            }

            // SECURITY: Default to cancelled
            e.setCancelled(true);

            // Check if dragging involves the top inventory (the GUI)
            boolean involvesGui = false;
            for (int slot : e.getRawSlots()) {
                if (slot < e.getView().getTopInventory().getSize()) {
                    involvesGui = true;
                    break;
                }
            }

            // If it touches the GUI, strict adherence to cancel unless owner overrides
            if (involvesGui) {
                e.setCancelled(true);
            }

            MenuOwner owner = (MenuOwner) inventoryHolder;
            owner.onDrag(e);
        } catch (Throwable t) {
            this.handleException(e.getWhoClicked(), e, t);
        }
    }

    private void handleException(HumanEntity who, Event event, Throwable t) {
        try {
            if (event instanceof Cancellable) {
                ((Cancellable) event).setCancelled(true);
            }
            who.closeInventory();
            who.sendMessage(Utils.formatColors("&cAn internal error occurred. Menu closed for safety."));
            this.plugin.getLogger().log(Level.SEVERE, "Exception in GUI event for " + who.getName(), t);
        } catch (Throwable secondary) {
            this.plugin.getLogger().severe("Fail-safe error: " + secondary.getMessage());
            secondary.printStackTrace();
        }
    }
}
