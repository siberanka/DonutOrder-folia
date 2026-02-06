/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.AsyncPlayerChatEvent
 *  org.bukkit.plugin.Plugin
 */
package me.clanify.donutOrder.input;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.gui.NewOrderMenu;
import me.clanify.donutOrder.gui.OrdersMainMenu;
import me.clanify.donutOrder.gui.SelectItemMenu;
import me.clanify.donutOrder.util.TaskUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

public class ChatInputManager implements Listener {
    private final DonutOrder plugin;
    // Thread-safe maps for Concurrent access (Async Chat vs Main Thread)
    private final Map<UUID, Prompt> prompts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, NewOrderSession> sessions = new java.util.concurrent.ConcurrentHashMap<>();

    public ChatInputManager(DonutOrder plugin) {
        this.plugin = plugin;
    }

    public void prompt(Player p, Kind kind, String messageToPlayer) {
        this.prompts.put(p.getUniqueId(), new Prompt(kind));
        p.closeInventory();
        p.sendMessage(messageToPlayer);
    }

    public NewOrderSession session(UUID u) {
        return this.sessions.computeIfAbsent(u, k -> new NewOrderSession());
    }

    public void clearSession(UUID u) {
        this.sessions.remove(u);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();

        // Check if player has a pending prompt
        if (!this.prompts.containsKey(u)) {
            return;
        }

        // Consume event immediately
        e.setCancelled(true);

        final String msg = e.getMessage().trim();

        // Removing the prompt can happen here safely due to ConcurrentHashMap
        final Prompt pr = this.prompts.remove(u);
        if (pr == null)
            return; // Race condition check

        // SCHEDULE LOGIC TO RUN ON MAIN SERVER THREAD
        // This prevents Async Chat from corrupting plugin state or triggering async
        // exceptions
        TaskUtil.runEntity(this.plugin, p, () -> {
            if (!p.isOnline())
                return;
            this.handleSyncInput(p, pr, msg);
        });
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        // CLEANUP MEMORY LEAKS
        UUID u = e.getPlayer().getUniqueId();
        this.prompts.remove(u);
        this.sessions.remove(u);
    }

    private void handleSyncInput(Player p, Prompt pr, String msg) {
        UUID u = p.getUniqueId();
        switch (pr.kind) {
            case SEARCH_MAIN: {
                this.plugin.state().main(u).search = msg;
                p.sendMessage(this.plugin.cfg().msg("chat.search_set", "&aSearch set: &f" + msg));
                new OrdersMainMenu(this.plugin, p).open();
                break;
            }
            case SEARCH_SELECT: {
                this.plugin.state().items(u).search = msg;
                p.sendMessage(this.plugin.cfg().msg("chat.search_set", "&aSearch set: &f" + msg));
                new SelectItemMenu(this.plugin, p).open();
                break;
            }
            case AMOUNT: {
                try {
                    int amt = Integer.parseInt(msg);
                    if (amt <= 0) {
                        throw new NumberFormatException();
                    }
                    this.session(u).amount = amt;
                    p.sendMessage(this.plugin.cfg().msg("chat.amount_ok", "&aAmount set: &f" + amt));
                    new NewOrderMenu(this.plugin, p).open();
                } catch (NumberFormatException ex) {
                    p.sendMessage(ChatColor.RED + "Invalid amount.");
                    new NewOrderMenu(this.plugin, p).open(); // Re-open menu on failure
                }
                break;
            }
            case PRICE: {
                try {
                    double price = Double.parseDouble(msg);
                    if (price <= 0.0 || Double.isNaN(price) || Double.isInfinite(price)) {
                        throw new NumberFormatException();
                    }
                    this.session(u).priceEach = price;
                    p.sendMessage(this.plugin.cfg().msg("chat.price_ok", "&aPrice set: &f$" + price));
                    new NewOrderMenu(this.plugin, p).open();
                } catch (NumberFormatException ex) {
                    p.sendMessage(ChatColor.RED + "Invalid price.");
                    new NewOrderMenu(this.plugin, p).open(); // Re-open menu on failure
                }
                break;
            }
        }
    }

    public static class Prompt {
        public final Kind kind;

        public Prompt(Kind k) {
            this.kind = k;
        }
    }

    public enum Kind {
        SEARCH_MAIN,
        SEARCH_SELECT,
        AMOUNT,
        PRICE;
    }

    public static class NewOrderSession {
        public String chosenItem;
        public Integer amount;
        public Double priceEach;
    }
}
