/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.event.Listener
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package me.clanify.donutOrder;

import me.clanify.donutOrder.Utils;
import me.clanify.donutOrder.cmd.DonutOrderCommand;
import me.clanify.donutOrder.cmd.OrdersCommand;
import me.clanify.donutOrder.gui.MenuListener;
import me.clanify.donutOrder.input.ChatInputManager;
import me.clanify.donutOrder.store.ConfigManager;
import me.clanify.donutOrder.store.EnchantmentsManager;
import me.clanify.donutOrder.store.FilterManager;
import me.clanify.donutOrder.store.OrderManager;
import me.clanify.donutOrder.store.PlayerStateManager;
import me.clanify.donutOrder.store.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class DonutOrder
        extends JavaPlugin {
    private static DonutOrder inst;
    private VaultHook vault;
    private ConfigManager configManager;
    private FilterManager filterManager;
    private EnchantmentsManager enchantmentsManager;
    private OrderManager orderManager;
    private PlayerStateManager stateManager;
    private ChatInputManager chatInputManager;

    public static DonutOrder inst() {
        return inst;
    }

    public void onEnable() {
        inst = this;
        this.configManager = new ConfigManager(this);
        this.filterManager = new FilterManager(this);
        this.enchantmentsManager = new EnchantmentsManager(this);
        this.vault = new VaultHook(this);
        if (!this.vault.hooked()) {
            this.getLogger().severe("Vault/Economy not found. Disabling.");
            Bukkit.getPluginManager().disablePlugin((Plugin) this);
            return;
        }
        this.orderManager = new OrderManager(this);
        this.stateManager = new PlayerStateManager(this);
        this.chatInputManager = new ChatInputManager(this);
        Bukkit.getPluginManager().registerEvents((Listener) new MenuListener(this), (Plugin) this);
        Bukkit.getPluginManager().registerEvents((Listener) this.chatInputManager, (Plugin) this);
        Bukkit.getPluginManager().registerEvents((Listener) new Utils.SignInputUtil.SignListener(this), (Plugin) this);
        this.getCommand("orders").setExecutor((CommandExecutor) new OrdersCommand(this));
        DonutOrderCommand adminCmd = new DonutOrderCommand(this);
        this.getCommand("donutorder").setExecutor((CommandExecutor) adminCmd);
        this.getCommand("donutorder").setTabCompleter((TabCompleter) adminCmd);
        this.getLogger().info("DonutOrder enabled.");
    }

    public void onDisable() {
        if (this.orderManager != null) {
            this.orderManager.saveAll();
            this.orderManager.shutdown();
        }
        if (this.stateManager != null) {
            this.stateManager.saveAllPrefs();
        }
    }

    public VaultHook vault() {
        return this.vault;
    }

    public ConfigManager cfg() {
        return this.configManager;
    }

    public FilterManager filters() {
        return this.filterManager;
    }

    public EnchantmentsManager ench() {
        return this.enchantmentsManager;
    }

    public OrderManager orders() {
        return this.orderManager;
    }

    public PlayerStateManager state() {
        return this.stateManager;
    }

    public ChatInputManager chat() {
        return this.chatInputManager;
    }
}
