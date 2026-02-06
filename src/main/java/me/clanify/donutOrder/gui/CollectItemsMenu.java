/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Item
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.InventoryAction
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.event.inventory.InventoryDragEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.plugin.Plugin
 */
package me.clanify.donutOrder.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.data.Order;
import me.clanify.donutOrder.gui.EditOrderMenu;
import me.clanify.donutOrder.gui.MenuOwner;
import me.clanify.donutOrder.gui.YourOrdersMenu;
import me.clanify.donutOrder.util.TaskUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public class CollectItemsMenu
        implements InventoryHolder,
        MenuOwner {
    private final DonutOrder pl;
    private final Player p;
    private final Order order;
    private Inventory inv;
    private final int requestedPage;
    private int currentPage = 0;
    private boolean internalPageSwitch = false;
    private long lastClickTime = 0;
    private static final long CLICK_COOLDOWN_MS = 200; // 200ms cooldown between clicks

    public CollectItemsMenu(DonutOrder pl, Player p, Order order) {
        this(pl, p, order, 0);
    }

    public CollectItemsMenu(DonutOrder pl, Player p, Order order, int page) {
        this.pl = pl;
        this.p = p;
        this.order = order;
        this.requestedPage = Math.max(0, page);
    }

    public Inventory getInventory() {
        return this.inv;
    }

    private int rows() {
        return this.pl.cfg().rows("collect", 6);
    }

    private int perPage() {
        return (this.rows() - 1) * 9;
    }

    private int maxPage() {
        int per = this.perPage();
        return Math.max(0, (this.order.storage.size() - 1) / Math.max(1, per));
    }

    public void open() {
        if (this.order.storage.isEmpty() && this.order.completed) {
            new YourOrdersMenu(this.pl, this.p).open();
            return;
        }

        // Security: Prevent multiple players (or same player glitch) from opening the
        // same storage using central manager lock
        if (!this.pl.orders().tryLockStorage(this.order.id, this.p.getUniqueId())) {
            this.p.sendMessage(this.pl.cfg().msg("messages.order_in_use",
                    "&cThis order is currently being viewed by someone else."));
            this.p.closeInventory();
            return;
        }

        int rows = this.rows();
        int per = this.perPage();
        int max = this.maxPage();
        this.currentPage = Math.max(0, Math.min(this.requestedPage, max));
        this.inv = Bukkit.createInventory((InventoryHolder) this, (int) (rows * 9),
                (String) this.pl.cfg().title("collect", "&#44b3ffOrders -> Collect Items"));
        int from = Math.max(0, Math.min(this.order.storage.size(), this.currentPage * per));
        int to = Math.min(this.order.storage.size(), from + per);
        for (int i = from; i < to; ++i) {
            ItemStack st = this.order.storage.get(i);
            if (st == null || st.getType() == Material.AIR)
                continue;
            this.inv.setItem(i - from, st.clone());
        }
        int prev = (rows - 1) * 9;
        int next = rows * 9 - 1;
        int drop = rows * 9 - 2;
        this.inv.setItem(prev, this.pl.cfg().button("gui.collect.items.prev", "ARROW", "&fPrevious Page", List.of()));
        this.inv.setItem(next, this.pl.cfg().button("gui.collect.items.next", "ARROW", "&fNext Page", List.of()));
        this.inv.setItem(drop, this.pl.cfg().button("gui.collect.items.drop", "DROPPER", "&fDROP LOOT",
                List.of("&fClick to drop all loot on the page")));
        ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = fill.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("\u00a77 ");
            fill.setItemMeta(meta);
        }
        for (int s = (rows - 1) * 9; s < rows * 9; ++s) {
            if (this.inv.getItem(s) != null)
                continue;
            this.inv.setItem(s, fill);
        }
        this.p.openInventory(this.inv);
        this.pl.cfg().play(this.p, "sounds.open", "BLOCK_CHEST_OPEN", 0.7f, 1.0f);
    }

    // ... (onClick remains same) ...

    @Override
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() != this) {
            return;
        }
        int rows = this.rows();
        int prev = (rows - 1) * 9;
        int next = rows * 9 - 1;
        int drop = rows * 9 - 2;
        Inventory top = e.getView().getTopInventory();
        boolean clickedTop = e.getClickedInventory() != null && e.getClickedInventory().equals((Object) top);
        boolean clickedPlayer = e.getClickedInventory() != null && e.getClickedInventory().getHolder() == this.p;
        int slot = e.getSlot();
        if (clickedTop && slot >= (rows - 1) * 9) {
            e.setCancelled(true);
            long now = System.currentTimeMillis();
            if (now - lastClickTime < CLICK_COOLDOWN_MS) {
                return;
            }
            lastClickTime = now;

            if (slot == prev) {
                int prevPage = Math.max(0, this.currentPage - 1);
                this.pl.cfg().play(this.p, "sounds.page", "UI_BUTTON_CLICK", 1.0f, 1.1f);
                this.flushCurrentPageToStorage();
                this.pl.orders().saveOrder(this.order);
                this.internalPageSwitch = true;
                TaskUtil.runEntityLater((Plugin) this.pl, (Entity) this.p,
                        () -> new CollectItemsMenu(this.pl, this.p, this.order, prevPage).open(), 1L);
                return;
            }
            if (slot == next) {
                int nextPage = Math.min(this.maxPage(), this.currentPage + 1);
                this.pl.cfg().play(this.p, "sounds.page", "UI_BUTTON_CLICK", 1.0f, 1.1f);
                this.flushCurrentPageToStorage();
                this.pl.orders().saveOrder(this.order);
                this.internalPageSwitch = true;
                TaskUtil.runEntityLater((Plugin) this.pl, (Entity) this.p,
                        () -> new CollectItemsMenu(this.pl, this.p, this.order, nextPage).open(), 1L);
                return;
            }
            if (slot == drop) {
                this.dropCurrentPageInGui();
                // dropCurrentPageInGui now handles saving/flushing inside itself to ensure safe
                // ordering
                // re-open triggers refresh
                this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
                this.internalPageSwitch = true;
                TaskUtil.runEntityLater((Plugin) this.pl, (Entity) this.p,
                        () -> new CollectItemsMenu(this.pl, this.p, this.order, this.currentPage).open(), 1L);
                return;
            }
            return;
        }
        if (clickedPlayer) {
            if (e.isShiftClick()) {
                e.setCancelled(true);
            } else {
                // ALLOW standard interaction in player inventory (Place, Swap, Pickup)
                e.setCancelled(false);
            }
            return;
        }
        if (clickedTop) {
            if (e.getHotbarButton() != -1) {
                e.setCancelled(true);
                return;
            }
            InventoryAction a = e.getAction();
            switch (a) {
                case PLACE_ALL:
                case PLACE_SOME:
                case PLACE_ONE:
                case SWAP_WITH_CURSOR:
                case HOTBAR_SWAP:
                case HOTBAR_MOVE_AND_READD: {
                    e.setCancelled(true);
                    return;
                }
                case MOVE_TO_OTHER_INVENTORY: {
                    e.setCancelled(false);
                    return;
                }
            }
            e.setCancelled(false);
            return;
        }

        // Allow dropping items outside inventory (clicking border/nothing)
        if (e.getClickedInventory() == null) {
            e.setCancelled(false);
            return;
        }

        e.setCancelled(true);
    }

    // ... (onDrag, onClose, flushCurrentPageToStorage remain same) ...
    @Override
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() != this) {
            return;
        }
        int rows = this.rows();
        int topSize = rows * 9;
        int controlStart = (rows - 1) * 9;

        boolean safe = true;
        for (int raw : e.getRawSlots()) {
            if (raw >= 0 && raw < controlStart) {
                // Dragging over item slots in GUI -> Block
                safe = false;
                break;
            }
            // If dragging over button slots (controlStart..topSize), strictly block or
            // allow?
            // Usually buttons shouldn't be dragged over.
            if (raw >= controlStart && raw < topSize) {
                safe = false;
                break;
            }
        }

        if (safe) {
            // If we are here, it means we are only dragging in player inventory
            e.setCancelled(false);
        } else {
            e.setCancelled(true);
        }
    }

    @Override
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() != this) {
            return;
        }

        // Ensure we release the storage lock
        this.pl.orders().unlockStorage(this.order.id, this.p.getUniqueId());

        if (this.internalPageSwitch) {
            this.internalPageSwitch = false;
            return;
        }
        this.flushCurrentPageToStorage();
        this.pl.orders().saveOrder(this.order);
        if (this.order.completed && this.order.storage.isEmpty()) {
            TaskUtil.runEntityLater((Plugin) this.pl, (Entity) this.p, () -> new YourOrdersMenu(this.pl, this.p).open(),
                    1L);
        } else {
            TaskUtil.runEntityLater((Plugin) this.pl, (Entity) this.p,
                    () -> new EditOrderMenu(this.pl, this.p, this.order).open(), 1L);
        }
    }

    private void flushCurrentPageToStorage() {
        if (this.inv == null) {
            return;
        }
        synchronized (this.pl.orders().getLock(this.order.id)) {
            int per = this.perPage();
            int from = this.currentPage * per;

            // Rebuild a local version of storage to ensure atomicity
            List<ItemStack> newStorage = new ArrayList<>(this.order.storage);

            // 1. Remove items that were on this page in storage
            int maxRem = Math.min(per, Math.max(0, newStorage.size() - from));
            for (int i = 0; i < maxRem; ++i) {
                if (from < newStorage.size()) {
                    newStorage.remove(from);
                }
            }

            // 2. Add current items from GUI back to the local list
            for (int i = 0; i < per && i < this.inv.getSize(); ++i) {
                ItemStack cur = this.inv.getItem(i);
                if (cur == null || cur.getType() == Material.AIR || cur.getAmount() <= 0)
                    continue;
                newStorage.add(Math.min(from + i, newStorage.size()), cur.clone());
            }

            // 3. Cleanup and Commit
            newStorage.removeIf(it -> it == null || it.getType() == Material.AIR || it.getAmount() <= 0);

            this.order.storage.clear();
            this.order.storage.addAll(newStorage);
        }
    }

    private void dropCurrentPageInGui() {
        int per = this.perPage();
        Location eye = this.p.getEyeLocation();
        List<ItemStack> toDrop = new ArrayList<>();

        // 1. Collect items to drop and clear GUI
        for (int i = 0; i < per; i++) {
            ItemStack cur = this.inv.getItem(i);
            if (cur != null && cur.getType() != Material.AIR && cur.getAmount() > 0) {
                toDrop.add(cur.clone());
                this.inv.setItem(i, null); // Clear GUI immediately
            }
        }

        // 2. Flush empty GUI to storage (removing items from storage)
        this.flushCurrentPageToStorage();

        // 3. Trigger Save (Async) - Committing the removal (loss)
        this.pl.orders().saveOrder(this.order);

        // 4. Drop items (Give)
        // If crash happens between 3 and 4 -> Items deleted (Safety over Dupe)
        for (ItemStack item : toDrop) {
            Item drop = this.p.getWorld().dropItem(eye, item);
            drop.setVelocity(eye.getDirection().multiply(0.25));
        }
    }
}
