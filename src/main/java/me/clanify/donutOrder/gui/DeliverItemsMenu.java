/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.block.BlockState
 *  org.bukkit.block.ShulkerBox
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.BlockStateMeta
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.plugin.Plugin
 */
package me.clanify.donutOrder.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.data.ItemKey;
import me.clanify.donutOrder.data.Order;
import me.clanify.donutOrder.store.OrderManager;
import me.clanify.donutOrder.gui.ConfirmDeliveryMenu;
import me.clanify.donutOrder.gui.MenuOwner;
import me.clanify.donutOrder.gui.OrdersMainMenu;
import me.clanify.donutOrder.util.TaskUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public class DeliverItemsMenu
        implements InventoryHolder,
        MenuOwner {
    private final DonutOrder pl;
    private final Player p;
    private final Order order;
    private Inventory inv;
    private boolean finalized = false;

    public DeliverItemsMenu(DonutOrder pl, Player p, Order order) {
        this.pl = pl;
        this.p = p;
        this.order = order;
    }

    public Inventory getInventory() {
        return this.inv;
    }

    public void open() {
        if (this.order.remainingAmount() <= 0) {
            new OrdersMainMenu(this.pl, this.p).open();
            return;
        }
        int rows = this.pl.cfg().rows("deliver", 4);
        int size = rows * 9;
        this.inv = Bukkit.createInventory((InventoryHolder) this, (int) size,
                (String) this.pl.cfg().title("deliver", "&#44b3ffOrders -> Deliver Items"));

        // Setup confirmation button at the last slot or customized slot
        ItemStack confirmBtn = this.pl.cfg().button("gui.deliver.items.confirm", "LIME_STAINED_GLASS_PANE",
                "&aCONFIRM", List.of("&fClick to deliver items"));

        // Find best slot (last slot usually)
        int confirmSlot = size - 1; // Default to last slot
        // If config has slot override (custom handling inside button reading might not
        // support dynamic slot well without extra code,
        // but let's try to stick to a convention or read raw config if needed,
        // but here we just put it at the end to avoid conflict with storage area)
        this.inv.setItem(confirmSlot, confirmBtn);

        this.p.openInventory(this.inv);
        this.pl.cfg().play(this.p, "sounds.open", "BLOCK_CHEST_OPEN", 0.7f, 1.0f);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() != this) {
            return;
        }

        int slot = e.getSlot();
        if (e.getClickedInventory() == this.inv) {
            // Example: Confirm button logic
            int rows = this.pl.cfg().rows("deliver", 4);
            int size = rows * 9;
            int confirmSlot = size - 1;

            if (slot == confirmSlot) {
                e.setCancelled(true);
                this.processConfirmation();
                return;
            }
        }

        e.setCancelled(false);
    }

    private void processConfirmation() {
        if (this.finalized)
            return;
        this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);

        ItemKey key = this.order.key;
        int need = this.order.remainingAmount();
        ArrayList<ItemStack> accepted = new ArrayList<ItemStack>();
        ArrayList<ItemStack> returns = new ArrayList<ItemStack>();
        int acceptedAmount = 0;

        int rows = this.pl.cfg().rows("deliver", 4);
        int size = rows * 9;
        int confirmSlot = size - 1;

        for (int i = 0; i < this.inv.getSize(); ++i) {
            if (this.order.storage.size() + acceptedAmount >= OrderManager.MAX_STORAGE_SIZE) {
                this.p.sendMessage(me.clanify.donutOrder.Utils
                        .formatColors("&cOrder storage is full! Cannot deliver more items."));
                break;
            }

            if (i == confirmSlot)
                continue; // Skip button

            ItemStack it = this.inv.getItem(i);
            if (it == null || it.getType() == Material.AIR)
                continue;

            // CRITICAL FIX: Clear the item from the inventory IMMEDIATELY.
            // This prevents "race conditions" where a player/client tries to
            // drop or move the item in the same tick as confirmation processing.
            // By clearing it here, the item is strictly under our control in the 'it'
            // variable.
            this.inv.setItem(i, null);

            if (key.matches(it)) {
                int can = Math.min(need - acceptedAmount, it.getAmount());
                if (can > 0) {
                    ItemStack clone = it.clone();
                    clone.setAmount(can);
                    accepted.add(clone);
                    acceptedAmount += can;
                    if (it.getAmount() > can) { // Fixed condition: if we took PART of the stack, return the rest
                        ItemStack left = it.clone();
                        left.setAmount(it.getAmount() - can);
                        returns.add(left);
                    }
                    continue;
                }
                returns.add(it);
                continue;
            }
            if (DeliverItemsMenu.isShulker(it)) {
                ItemStack[] cont;
                BlockStateMeta meta = (BlockStateMeta) it.getItemMeta();
                ShulkerBox box = (ShulkerBox) meta.getBlockState();
                boolean changed = false;
                cont = box.getInventory().getContents();

                for (int sIdx = 0; sIdx < cont.length; sIdx++) {
                    ItemStack s = cont[sIdx];
                    if (s == null || s.getType() == Material.AIR || !key.matches(s))
                        continue;

                    int can = Math.min(need - acceptedAmount, s.getAmount());
                    if (can <= 0)
                        break;

                    ItemStack clone = s.clone();
                    clone.setAmount(can);
                    accepted.add(clone);

                    s.setAmount(s.getAmount() - can);
                    changed = true; // Mark that we modified the shulker contents

                    acceptedAmount += can;
                    if (acceptedAmount >= need)
                        break;
                }

                if (changed) {
                    box.getInventory().setContents(cont);
                    meta.setBlockState((BlockState) box);
                    it.setItemMeta((ItemMeta) meta);
                }
                returns.add(it);
                continue;
            }
            returns.add(it);
        }

        // Return non-accepted items to player (or drop)
        for (ItemStack r : returns) {
            this.giveBackOrDrop(this.p, r);
        }

        this.finalized = true;

        if (acceptedAmount <= 0) {
            this.p.sendMessage(me.clanify.donutOrder.Utils.formatColors("&cNo valid items provided."));
            TaskUtil.runEntityLater((Plugin) this.pl, (Entity) this.p, () -> new OrdersMainMenu(this.pl, this.p).open(),
                    1L);
            return;
        }

        int acceptedAmountFinal = acceptedAmount;
        ArrayList<ItemStack> acceptedFinal = new ArrayList<>(accepted);
        TaskUtil.runEntityLater((Plugin) this.pl, (Entity) this.p,
                () -> new ConfirmDeliveryMenu(this.pl, this.p, this.order, acceptedFinal, acceptedAmountFinal).open(),
                1L);
    }

    @Override
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() != this) {
            return;
        }
        // SAFETY: If not finalized (confirmed), force return EVERYTHING in the GUI.
        if (!this.finalized) {
            // We do not filter for valid items here. JUST RETURN EVERYTHING.
            // This prevents accidental loss if player crashes or closes GUI.
            int rows = this.pl.cfg().rows("deliver", 4);
            int size = rows * 9;
            int confirmSlot = size - 1;

            for (int i = 0; i < this.inv.getSize(); ++i) {
                if (i == confirmSlot)
                    continue; // Skip button
                ItemStack it = this.inv.getItem(i);
                if (it != null && it.getType() != Material.AIR) {
                    this.giveBackOrDrop(this.p, it);
                }
            }

            TaskUtil.runEntityLater((Plugin) this.pl, (Entity) this.p, () -> new OrdersMainMenu(this.pl, this.p).open(),
                    1L);
        }
    }

    private static boolean isShulker(ItemStack it) {
        Material m = it.getType();
        return m.name().endsWith("SHULKER_BOX") && it.getItemMeta() instanceof BlockStateMeta;
    }

    @Override
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (e.getInventory().getHolder() != this) {
            return;
        }
        // Disable dragging items into the delivery menu entirely for safety.
        // Players must click to place items.
        e.setCancelled(true);
    }

    private void giveBackOrDrop(Player p, ItemStack is) {
        HashMap<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack[] { is });
        left.values().forEach(rem -> p.getWorld().dropItemNaturally(p.getLocation(), rem));
    }
}
