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
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.metadata.FixedMetadataValue
 *  org.bukkit.metadata.MetadataValue
 *  org.bukkit.plugin.Plugin
 */
package me.clanify.donutOrder.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.Utils;
import me.clanify.donutOrder.data.Order;
import me.clanify.donutOrder.gui.EditOrderMenu;
import me.clanify.donutOrder.gui.GuiVariant;
import me.clanify.donutOrder.gui.MenuOwner;
import me.clanify.donutOrder.gui.NewOrderMenu;
import me.clanify.donutOrder.gui.OrdersMainMenu;
import me.clanify.donutOrder.util.TaskUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

public class YourOrdersMenu
        implements InventoryHolder,
        MenuOwner {
    private static final String META_SUPPRESS_CLOSE = "donutorder.suppressClose";
    private final DonutOrder pl;
    private final Player p;
    private Inventory inv;
    // Anti-exploit: Click cooldown
    private long lastClickTime = 0;
    private static final long CLICK_COOLDOWN_MS = 300;

    public YourOrdersMenu(DonutOrder pl, Player p) {
        this.pl = pl;
        this.p = p;
    }

    public Inventory getInventory() {
        return this.inv;
    }

    public void open() {
        if (this.pl.bedrock().isBedrockPlayer(this.p)) {
            this.pl.bedrock().sendYourOrdersMenu(this.p);
            return;
        }
        int rows = this.pl.cfg().rows("your", 3);
        this.inv = Bukkit.createInventory((InventoryHolder) this, (int) (rows * 9),
                (String) this.pl.cfg().title("your", "&#44b3ffOrders -> Your Orders"));
        List<Order> mine = this.pl.orders().all().stream().filter(o -> o.owner.equals(this.p.getUniqueId()))
                .filter(o -> !o.canceled).filter(o -> !o.completed || !o.storage.isEmpty())
                .collect(Collectors.toList());
        int slot = 0;
        for (Order o2 : mine) {
            HashMap<String, String> ph = new HashMap<String, String>();
            ph.put("player", this.p.getName());
            ph.put("item", o2.key.displayName());
            ph.put("requested", Utils.abbr(o2.requested));
            ph.put("delivered", Utils.abbr(o2.delivered));
            ph.put("price_each", Utils.abbr(o2.priceEach));
            ph.put("paid", Utils.abbr((double) o2.delivered * o2.priceEach));
            ph.put("total", Utils.abbr(o2.totalPrice()));
            ArrayList<String> lore = new ArrayList<String>(List.of("&a{requested} &f{item}", "&a${price_each} &7each",
                    "", "{delivered}/{requested} &7Delivered", "${paid}/${total}"));
            List<String> enchLore = o2.key.enchantLoreLines("&7");
            if (!enchLore.isEmpty()) {
                lore.add("&7");
                lore.addAll(enchLore);
            }
            ItemStack ui = this.pl.cfg().dynamicItem(o2.key.material, "gui.your.items.order-item",
                    this.pl.lang().get("gui.your.items.order-item.displayname", "&f{player}'s Order"), lore, ph);
            ui = GuiVariant.merge(ui, o2.key.buildIcon());
            this.inv.setItem(slot++, ui);
        }
        this.inv.setItem(Math.min(slot, this.inv.getSize() - 1), this.pl.cfg().button("gui.your.items.new", "MAP",
                "&fNew Order", List.of("&fClick to create new order")));
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

        List<Order> mine = this.pl.orders().all().stream().filter(o -> o.owner.equals(this.p.getUniqueId()))
                .filter(o -> !o.canceled).filter(o -> !o.completed || !o.storage.isEmpty()).toList();
        int slot = e.getSlot();
        if (slot == mine.size()) {
            this.p.setMetadata(META_SUPPRESS_CLOSE,
                    (MetadataValue) new FixedMetadataValue((Plugin) this.pl, (Object) true));
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            new NewOrderMenu(this.pl, this.p).open();
            return;
        }
        if (slot >= 0 && slot < mine.size()) {
            this.p.setMetadata(META_SUPPRESS_CLOSE,
                    (MetadataValue) new FixedMetadataValue((Plugin) this.pl, (Object) true));
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            new EditOrderMenu(this.pl, this.p, mine.get(slot)).open();
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
        TaskUtil.runEntityLater((Plugin) this.pl, (Entity) this.p, () -> new OrdersMainMenu(this.pl, this.p).open(),
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
