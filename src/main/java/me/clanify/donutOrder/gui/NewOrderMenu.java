/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.Sound
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

import java.util.HashMap;
import java.util.List;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.Utils;
import me.clanify.donutOrder.data.ItemKey;
import me.clanify.donutOrder.gui.EnchantSelectMenu;
import me.clanify.donutOrder.gui.GuiVariant;
import me.clanify.donutOrder.gui.MenuOwner;
import me.clanify.donutOrder.gui.SelectItemMenu;
import me.clanify.donutOrder.gui.YourOrdersMenu;
import me.clanify.donutOrder.input.ChatInputManager;
import me.clanify.donutOrder.store.OrderManager;
import me.clanify.donutOrder.util.TaskUtil;
import me.clanify.donutOrder.utils.SignInputUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
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

public class NewOrderMenu
        implements InventoryHolder,
        MenuOwner {
    private static final String META_CHOSEN = "donutorder.tmpChosenStack";
    private static final String META_SUPPRESS_CLOSE = "donutorder.suppressClose";
    private final DonutOrder pl;
    private final Player p;
    private Inventory inv;
    // Anti-exploit: Prevent double-spend
    private volatile boolean finalized = false;
    private long lastClickTime = 0;
    private static final long CLICK_COOLDOWN_MS = 300;

    public NewOrderMenu(DonutOrder pl, Player p) {
        this.pl = pl;
        this.p = p;
    }

    public Inventory getInventory() {
        return this.inv;
    }

    private static boolean hasAnyEnchants(ItemStack is) {
        if (is == null) {
            return false;
        }
        ItemMeta im = is.getItemMeta();
        return im != null && im.hasEnchants();
    }

    public void open() {
        boolean skipOnce;
        Material mat;
        ChatInputManager.NewOrderSession s = this.pl.chat().session(this.p.getUniqueId());
        if (s.chosenItem == null) {
            s.chosenItem = Material.STONE.name();
        }
        if (s.amount == null) {
            s.amount = 1;
        }
        if (s.priceEach == null) {
            s.priceEach = 1.0;
        }
        ItemStack chosenStack = null;
        List<org.bukkit.metadata.MetadataValue> metaList = this.p.getMetadata(META_CHOSEN);
        if (!metaList.isEmpty()) {
            Object obj = metaList.get(0).value();
            if (obj instanceof ItemStack) {
                chosenStack = ((ItemStack) obj).clone();
            }
        }
        Material material = mat = chosenStack != null ? chosenStack.getType()
                : Material.matchMaterial((String) s.chosenItem);
        if (mat == null) {
            mat = Material.STONE;
        }
        if (skipOnce = this.p.hasMetadata("donutorder.skipEnchantOnce")) {
            this.p.removeMetadata("donutorder.skipEnchantOnce", (Plugin) this.pl);
        }
        if (!skipOnce && this.pl.ench().hasOptionsFor(mat) && !NewOrderMenu.hasAnyEnchants(chosenStack)) {
            new EnchantSelectMenu(this.pl, this.p, new ItemStack(mat)).open();
            return;
        }
        String label = chosenStack != null && chosenStack.getItemMeta() != null
                && chosenStack.getItemMeta().hasDisplayName()
                        ? chosenStack.getItemMeta().getDisplayName().replace("\u00a7", "&")
                        : OrderManager.nice(mat);
        int rows = this.pl.cfg().rows("new", 3);
        this.inv = Bukkit.createInventory((InventoryHolder) this, (int) (rows * 9),
                (String) this.pl.cfg().title("new", "&#44b3ffOrders -> New Order"));
        this.inv.setItem(10, this.pl.cfg().button("gui.new.items.cancel", "RED_STAINED_GLASS_PANE", "&cCancel",
                List.of("&fClick to return")));
        HashMap<String, String> ph = new HashMap<String, String>();
        ph.put("item", label);
        ph.put("amount", Utils.abbr(s.amount.intValue()));
        ph.put("price_each", Utils.abbr(s.priceEach));
        ph.put("total", Utils.abbr((double) s.amount.intValue() * s.priceEach));
        ItemStack itemTile = this.pl.cfg().dynamicItem(mat, "gui.new.items.item", "&fITEM",
                List.of("&fClick to choose item", "&7({item})"), ph);
        if (chosenStack != null) {
            itemTile = GuiVariant.merge(itemTile, chosenStack);
        }
        this.inv.setItem(12, itemTile);
        this.inv.setItem(13, this.pl.cfg().dynamicItem(Material.CHEST, "gui.new.items.amount", "&fAMOUNT",
                List.of("&fClick to type number of items", "&7({amount})"), ph));
        this.inv.setItem(14, this.pl.cfg().dynamicItem(Material.EMERALD, "gui.new.items.price", "&fPRICE",
                List.of("&fClick to type the price per item", "&7(${price_each})"), ph));
        this.inv.setItem(16, this.pl.cfg().dynamicItem(Material.LIME_STAINED_GLASS_PANE, "gui.new.items.confirm",
                "&aCONFIRM", List.of("&fClick to confirm order", "&7(Total: ${total})"), ph));
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
        ChatInputManager.NewOrderSession s = this.pl.chat().session(this.p.getUniqueId());
        if (slot == 10) {
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            this.p.setMetadata(META_SUPPRESS_CLOSE,
                    (MetadataValue) new FixedMetadataValue((Plugin) this.pl, (Object) true));
            new YourOrdersMenu(this.pl, this.p).open();
            return;
        }
        if (slot == 12) {
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            this.p.setMetadata(META_SUPPRESS_CLOSE,
                    (MetadataValue) new FixedMetadataValue((Plugin) this.pl, (Object) true));
            new SelectItemMenu(this.pl, this.p).open();
            return;
        }
        if (slot == 13) {
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            this.p.setMetadata(META_SUPPRESS_CLOSE,
                    (MetadataValue) new FixedMetadataValue((Plugin) this.pl, (Object) true));
            this.p.closeInventory();
            ConfigurationSection sec = this.pl.cfg().cfg().getConfigurationSection("amount-sign");
            SignInputUtil.openFromConfig(this.pl, this.p, sec, input -> {
                int amt;
                String t;
                if (!this.p.isOnline()) {
                    return;
                }
                String string = t = input == null ? "" : input.trim();
                if (t.equals("-")) {
                    t = "";
                }
                try {
                    t = t.replace(" ", "");
                    amt = Integer.parseInt(t);
                } catch (Exception ex) {
                    this.p.sendMessage(this.pl.cfg().msg("messages.amount_invalid",
                            "&cInvalid amount. Please enter a whole number (e.g. 64)."));
                    new NewOrderMenu(this.pl, this.p).open();
                    return;
                }
                if (amt <= 0) {
                    this.p.sendMessage(this.pl.cfg().msg("messages.amount_min", "&cAmount must be at least 1."));
                    new NewOrderMenu(this.pl, this.p).open();
                    return;
                }
                int maxItems = this.pl.cfg().getMaxItemsPerOrder();
                if (amt > maxItems) {
                    this.p.sendMessage(
                            this.pl.cfg().msg("messages.amount_max", "&cAmount limit is {max} items per order.")
                                    .replace("{max}", Utils.abbr(maxItems)));
                    new NewOrderMenu(this.pl, this.p).open();
                    return;
                }
                ChatInputManager.NewOrderSession sess = this.pl.chat().session(this.p.getUniqueId());
                sess.amount = amt;
                new NewOrderMenu(this.pl, this.p).open();
            });
            return;
        }
        if (slot == 14) {
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            this.p.setMetadata(META_SUPPRESS_CLOSE,
                    (MetadataValue) new FixedMetadataValue((Plugin) this.pl, (Object) true));
            this.p.closeInventory();
            ConfigurationSection sec = this.pl.cfg().cfg().getConfigurationSection("price-sign");
            SignInputUtil.openFromConfig(this.pl, this.p, sec, input -> {
                double price;
                String t;
                if (!this.p.isOnline()) {
                    return;
                }
                String string = t = input == null ? "" : input.trim();
                if (t.equals("-")) {
                    t = "";
                }
                try {
                    t = t.replace(" ", "").replace(",", ".");
                    price = Double.parseDouble(t);
                } catch (Exception ex) {
                    this.p.sendMessage(this.pl.cfg().msg("messages.amount_invalid",
                            "&cInvalid price. Please enter a number (e.g. 2.5)."));
                    new NewOrderMenu(this.pl, this.p).open();
                    return;
                }
                if (price <= 0.0 || Double.isNaN(price) || Double.isInfinite(price)) {
                    this.p.sendMessage(
                            this.pl.cfg().msg("messages.price_invalid", "&cPrice must be positive and valid."));
                    new NewOrderMenu(this.pl, this.p).open();
                    return;
                }
                double minPrice = this.pl.cfg().getMinPricePerItem();
                if (price < minPrice) {
                    this.p.sendMessage(this.pl.cfg().msg("messages.price_min", "&cMinimum price per item is ${min}.")
                            .replace("{min}", Utils.abbr(minPrice)));
                    new NewOrderMenu(this.pl, this.p).open();
                    return;
                }
                double maxPrice = this.pl.cfg().getMaxPricePerItem();
                if (price > maxPrice) {
                    this.p.sendMessage(this.pl.cfg().msg("messages.price_max", "&cMaximum price per item is ${max}.")
                            .replace("{max}", Utils.abbr(maxPrice)));
                    new NewOrderMenu(this.pl, this.p).open();
                    return;
                }
                ChatInputManager.NewOrderSession sess = this.pl.chat().session(this.p.getUniqueId());
                sess.priceEach = price;
                new NewOrderMenu(this.pl, this.p).open();
            });
            return;
        }
        if (slot == 16) {
            // Anti-exploit: Prevent double-click/double-spend
            if (this.finalized)
                return;
            long now = System.currentTimeMillis();
            if (now - lastClickTime < CLICK_COOLDOWN_MS)
                return;
            lastClickTime = now;

            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            if (s.chosenItem == null || s.amount == null || s.priceEach == null) {
                this.p.sendMessage(
                        this.pl.cfg().msg("messages.order_incomplete", "&cPlease set item, amount, and price first."));
                return;
            }
            double total = (double) s.amount.intValue() * s.priceEach;
            // Set finalized BEFORE vault.take() to prevent race condition
            this.finalized = true;
            if (!this.pl.vault().take((OfflinePlayer) this.p, total)) {
                this.finalized = false; // Reset on failure
                this.p.sendMessage(
                        this.pl.cfg().msg("messages.cannot_afford", "&cYou cannot afford this (${total}).")
                                .replace("${total}", Utils.abbr(total)));
                return;
            }
            ItemStack chosen = null;
            List<org.bukkit.metadata.MetadataValue> metaList2 = this.p.getMetadata(META_CHOSEN);
            if (!metaList2.isEmpty()) {
                Object obj2 = metaList2.get(0).value();
                if (obj2 instanceof ItemStack) {
                    chosen = (ItemStack) obj2;
                }
            }
            ItemKey key = chosen != null ? ItemKey.fromStack(chosen)
                    : ItemKey.of(Material.valueOf((String) s.chosenItem));
            try {
                this.pl.orders().create(this.p.getUniqueId(), key, (int) s.amount, (double) s.priceEach);
            } catch (Exception ex) {
                // PARANOID SECURITY: Transaction Rollback
                // If create() fails (limit reached, DB error), refund immediately.
                this.pl.vault().give((OfflinePlayer) this.p, total);
                this.finalized = false;
                this.p.sendMessage(Utils.formatColors("&cOrder creation failed: " + ex.getMessage()));
                this.p.sendMessage(Utils.formatColors("&e" + Utils.abbr(total) + " has been refunded."));
                ex.printStackTrace();
                return;
            }
            this.p.playSound(this.p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            if (this.p.hasMetadata(META_CHOSEN)) {
                this.p.removeMetadata(META_CHOSEN, (Plugin) this.pl);
            }
            this.pl.chat().clearSession(this.p.getUniqueId());
            this.p.setMetadata(META_SUPPRESS_CLOSE,
                    (MetadataValue) new FixedMetadataValue((Plugin) this.pl, (Object) true));
            new YourOrdersMenu(this.pl, this.p).open();
        }
    }

    @Override
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() != this) {
            return;
        }
        if (this.p.hasMetadata("donutorder-sign-input")) {
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
        for (int slot : e.getRawSlots()) {
            if (slot < e.getView().getTopInventory().getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
