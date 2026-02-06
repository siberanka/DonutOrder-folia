package me.clanify.donutOrder.bedrock;

import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.data.Order;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class BedrockManager {
    private final DonutOrder pl;
    private final boolean enabled;
    private Object handler; // FloodgateForms instance

    public BedrockManager(DonutOrder pl) {
        this.pl = pl;
        // Check if Floodgate is present
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            this.enabled = true;
            try {
                this.handler = new FloodgateForms(pl);
                pl.getLogger().info("Floodgate found! Bedrock forms enabled.");
            } catch (Throwable t) {
                pl.getLogger().warning("Floodgate found but failed to initialize forms: " + t.getMessage());
                t.printStackTrace();
                // Fallback to disabled if init fails
            }
        } else {
            this.enabled = false;
        }
    }

    public boolean isBedrockPlayer(Player p) {
        if (!enabled || handler == null)
            return false;
        return ((FloodgateForms) handler).isBedrockPlayer(p);
    }

    public void sendOrdersMenu(Player p) {
        if (!enabled || handler == null)
            return;
        ((FloodgateForms) handler).sendOrdersMenu(p);
    }

    public void sendNewOrderMenu(Player p) {
        if (!enabled || handler == null)
            return;
        ((FloodgateForms) handler).sendNewOrderMenu(p);
    }

    public void sendDeliverMenu(Player p, Order order) {
        if (!enabled || handler == null)
            return;
        ((FloodgateForms) handler).sendDeliverMenu(p, order);
    }

    public void sendYourOrdersMenu(Player p) {
        if (!enabled || handler == null)
            return;
        ((FloodgateForms) handler).sendYourOrdersMenu(p);
    }

    public void sendOrderActionMenu(Player p, Order order) {
        if (!enabled || handler == null)
            return;
        ((FloodgateForms) handler).sendOrderActionMenu(p, order);
    }

    public void sendOrderDetailsForm(Player p, me.clanify.donutOrder.data.ItemKey key, boolean fromHand) {
        if (!enabled || handler == null)
            return;
        ((FloodgateForms) handler).sendOrderDetailsForm(p, key, fromHand);
    }

    public void closeAll() {
        if (!enabled || handler == null)
            return;
        ((FloodgateForms) handler).closeAll();
    }

    public boolean isViewingOrders(Player p) {
        if (!enabled || handler == null)
            return false;
        return ((FloodgateForms) handler).isViewingOrders(p);
    }

    /**
     * Clean up Bedrock player state when they disconnect.
     * Called from PlayerQuitEvent listener.
     */
    public void onPlayerQuit(java.util.UUID uuid) {
        if (!enabled || handler == null)
            return;
        ((FloodgateForms) handler).onPlayerQuit(uuid);
    }
}
