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
        Object obj;
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
        if (this.p.hasMetadata(META_CHOSEN)
                && (obj = ((MetadataValue) this.p.getMetadata(META_CHOSEN).get(0)).value()) instanceof ItemStack) {
            ItemStack is = (ItemStack) obj;
            chosenStack = is.clone();
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
                    this.p.sendMessage(Utils.formatColors("&cInvalid amount. Please enter a whole number (e.g. 64)."));
                    new NewOrderMenu(this.pl, this.p).open();
                    return;
                }
                if (amt <= 0) {
                    this.p.sendMessage(Utils.formatColors("&cAmount must be at least 1."));
                    new NewOrderMenu(this.pl, this.p).open();
                    return;
                }
                if (amt > 1000000) { // HARD CAP 1,000,000
                    this.p.sendMessage(
                            Utils.formatColors("&cAmount limit is 1,000,000 items per order to prevent lag."));
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
                    this.p.sendMessage(Utils.formatColors("&cInvalid price. Please enter a number (e.g. 2.5)."));
                    new NewOrderMenu(this.pl, this.p).open();
                    return;
                }
                if (price <= 0.0 || Double.isNaN(price) || Double.isInfinite(price)) {
                    this.p.sendMessage(Utils.formatColors("&cPrice must be positive and valid."));
                    new NewOrderMenu(this.pl, this.p).open();
                    return;
                }
                if (price > 1_000_000_000_000.0) { // HARD CAP 1 Trillion
                    this.p.sendMessage(Utils.formatColors("&cPrice exceeds the maximum limit of 1 Trillion."));
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
            Object obj;
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            if (s.chosenItem == null || s.amount == null || s.priceEach == null) {
                this.p.sendMessage("\u00a7cPlease set item, amount, and price first.");
                return;
            }
            double total = (double) s.amount.intValue() * s.priceEach;
            if (!this.pl.vault().take((OfflinePlayer) this.p, total)) {
                this.p.sendMessage(Utils.formatColors(
                        this.pl.cfg().msg("messages.cannot_afford", "&cYou cannot afford this (&f${total}&c).")
                                .replace("${total}", Utils.abbr(total))));
                return;
            }
            ItemStack chosen = null;
            if (this.p.hasMetadata(META_CHOSEN)
                    && (obj = ((MetadataValue) this.p.getMetadata(META_CHOSEN).get(0)).value()) instanceof ItemStack) {
                ItemStack is;
                chosen = is = (ItemStack) obj;
            }
            ItemKey key = chosen != null ? ItemKey.fromStack(chosen)
                    : ItemKey.of(Material.valueOf((String) s.chosenItem));
            this.pl.orders().create(this.p.getUniqueId(), key, (int) s.amount, (double) s.priceEach);
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
