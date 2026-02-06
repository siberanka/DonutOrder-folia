package me.clanify.donutOrder.gui;

import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.gui.MenuOwner;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MenuManager {
    private final DonutOrder plugin;

    public MenuManager(DonutOrder plugin) {
        this.plugin = plugin;
    }

    /**
     * Forcefully close all open menus that belong to this plugin.
     * Use this during onDisable or severe error states.
     */
    /**
     * Refresh the orders menu for all players currently viewing it.
     * Works for both Java (Chest GUI) and Bedrock (Geyser forms).
     */
    public void refreshAllOrders() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            InventoryView open = p.getOpenInventory();
            if (open != null && open.getTopInventory().getHolder() instanceof OrdersMainMenu) {
                // Java Player viewing the menu
                new OrdersMainMenu(plugin, p).open();
            } else if (plugin.bedrock().isViewingOrders(p)) {
                // Bedrock Player viewing the form
                new OrdersMainMenu(plugin, p).open(); // This calls bedrock().sendOrdersMenu() internally
            }
        }
    }

    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            InventoryView open = p.getOpenInventory();
            if (open != null && open.getTopInventory().getHolder() instanceof MenuOwner) {
                p.closeInventory();
                plugin.chat().clearSession(p.getUniqueId());
            }
        }
    }
}
