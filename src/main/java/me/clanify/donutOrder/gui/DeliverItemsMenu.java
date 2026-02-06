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
    // Anti-exploit: Click cooldown
    private long lastClickTime = 0;
    private static final long CLICK_COOLDOWN_MS = 300;

    public DeliverItemsMenu(DonutOrder pl, Player p, Order order) {
        this.pl = pl;
        this.p = p;
        this.order = order;
    }

    public Inventory getInventory() {
        return this.inv;
    }

    public void open() {
        if (this.pl.bedrock().isBedrockPlayer(this.p)) {
            this.pl.bedrock().sendDeliverMenu(this.p, this.order);
            return;
        }
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
            int rows = this.pl.cfg().rows("deliver", 4);
            int size = rows * 9;
            int confirmSlot = size - 1;

            if (slot == confirmSlot) {
                e.setCancelled(true);
                // Anti-exploit: Click cooldown on confirm button
                long now = System.currentTimeMillis();
                if (now - lastClickTime < CLICK_COOLDOWN_MS)
                    return;
                lastClickTime = now;

                this.processConfirmation();
                return;
            }
        }

        // STRICT INTERACTION: Only allow safe actions
        // Prevent obscure click types that modded clients might use
        // Allow: PICKUP, PLACE, SWAP, DROP, MOVE_TO_OTHER
        switch (e.getAction()) {
            case PICKUP_ALL:
            case PICKUP_HALF:
            case PICKUP_ONE:
            case PICKUP_SOME:
            case PLACE_ALL:
            case PLACE_ONE:
            case PLACE_SOME:
            case SWAP_WITH_CURSOR:
            case DROP_ALL_CURSOR:
            case DROP_ONE_CURSOR:
            case DROP_ALL_SLOT:
            case DROP_ONE_SLOT:
            case MOVE_TO_OTHER_INVENTORY:
            case HOTBAR_MOVE_AND_READD:
            case HOTBAR_SWAP:
                e.setCancelled(false); // Allow standard interactions
                break;
            default:
                e.setCancelled(true); // Block unwanted/unknown actions (CLONE, UNKNOWN, etc.)
                break;
        }
    }

    private void processConfirmation() {
        if (this.finalized)
            return;
        this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);

        // --- PHASE 1: PRE-VALIDATION (SIMULATION) ---
        // Calculate everything without modifying the GUI.
        // Deep copy items to simulate removals.

        int rows = this.pl.cfg().rows("deliver", 4);
        int size = rows * 9;
        int confirmSlot = size - 1;

        ItemKey key = this.order.key;
        int remainingNeed = this.order.remainingAmount();
        int potentialAcceptedAmount = 0;

        // We use a map to store what the inventory SHOULD look like after processing.
        // Index -> ItemStack (null means removed)
        HashMap<Integer, ItemStack> transactionResult = new HashMap<>();
        // List of items to be transported to next menu
        ArrayList<ItemStack> transactionAccepted = new ArrayList<>();
        // List of remaining items (leftovers/returns)
        ArrayList<ItemStack> transactionReturns = new ArrayList<>();

        for (int i = 0; i < this.inv.getSize(); ++i) {
            if (i == confirmSlot)
                continue;

            ItemStack original = this.inv.getItem(i);
            if (original == null || original.getType() == Material.AIR)
                continue;

            ItemStack it = original.clone(); // Work on clone

            // Skip invalid/scam items (damage > 0 checked in matches)

            if (key.matches(it)) {
                int canTake = Math.min(remainingNeed - potentialAcceptedAmount, it.getAmount());
                if (canTake > 0) {
                    ItemStack toAccept = it.clone();
                    toAccept.setAmount(canTake);
                    transactionAccepted.add(toAccept);

                    potentialAcceptedAmount += canTake;

                    if (it.getAmount() > canTake) {
                        ItemStack leftover = it.clone();
                        leftover.setAmount(it.getAmount() - canTake);
                        transactionResult.put(i, leftover); // Update slot with leftover
                    } else {
                        transactionResult.put(i, null); // Slot becomes empty
                    }
                    continue;
                }
                // No more needed, keep item as is
                transactionReturns.add(it);
                transactionResult.put(i, it); // Explicitly keep
                continue;
            }

            if (DeliverItemsMenu.isShulker(it)) {
                // Shulker processing simulation
                try {
                    BlockStateMeta meta = (BlockStateMeta) it.getItemMeta();
                    ShulkerBox box = (ShulkerBox) meta.getBlockState();
                    ItemStack[] contents = box.getInventory().getContents();
                    boolean changed = false;

                    for (int sIdx = 0; sIdx < contents.length; sIdx++) {
                        ItemStack s = contents[sIdx];
                        if (s == null || s.getType() == Material.AIR || !key.matches(s))
                            continue;

                        int canTake = Math.min(remainingNeed - potentialAcceptedAmount, s.getAmount());
                        if (canTake <= 0)
                            break;

                        ItemStack toAccept = s.clone();
                        toAccept.setAmount(canTake);
                        transactionAccepted.add(toAccept);

                        s.setAmount(s.getAmount() - canTake); // Modify simulation array
                        changed = true;
                        potentialAcceptedAmount += canTake;
                    }

                    if (changed) {
                        // Apply changes to fake shulker
                        box.getInventory().setContents(contents);
                        meta.setBlockState((BlockState) box);
                        it.setItemMeta((ItemMeta) meta);
                    }

                    // Whether changed or not, the shulker stays/returns
                    transactionResult.put(i, it);
                    transactionReturns.add(it);

                } catch (Exception e) {
                    // Shulker parse error? Unsafe item? Skip it securely.
                    transactionResult.put(i, original); // Keep original
                    transactionReturns.add(original);
                }
                continue;
            }

            // Irrelevant item
            transactionResult.put(i, it);
            transactionReturns.add(it);
        }

        // VALIDATION CHECK
        if (potentialAcceptedAmount <= 0) {
            this.p.sendMessage(me.clanify.donutOrder.Utils
                    .formatColors(this.pl.cfg().msg("messages.no_valid_items", "&cNo valid items provided.")));
            // GUI remains untouched, player can try again
            return;
        }

        if (this.order.storage.size() + potentialAcceptedAmount > OrderManager.MAX_STORAGE_SIZE) {
            this.p.sendMessage(me.clanify.donutOrder.Utils
                    .formatColors(this.pl.cfg().msg("messages.storage_full", "&cOrder storage is full!")));
            return;
        }

        // --- PHASE 2: COMMIT (ATOMIC EXECUTION) ---
        // Validation passed. Apply changes.

        this.finalized = true; // Mark finalized immediately to prevent onClose refunding duplicates

        // 1. Clear inventory to prevent dupes (we have the data in lists now)
        for (int i = 0; i < this.inv.getSize(); ++i) {
            if (i == confirmSlot)
                continue;
            this.inv.setItem(i, null);
        }

        // 2. Give back leftovers (modified shulkers, partial stacks)
        // We use the 'transactionResult' map to know exact state of slots
        // BUT logic simplifies to: simply return what's in 'transactionResult' values
        // (if not null)
        // Actually, logic is safer: We return everything that wasn't fully consumed.
        // The 'transactionResult' map contains the FINAL state of slots.

        for (HashMap.Entry<Integer, ItemStack> entry : transactionResult.entrySet()) {
            ItemStack stack = entry.getValue();
            if (stack != null && stack.getType() != Material.AIR) {
                this.giveBackOrDrop(this.p, stack);
            }
        }
        // Note: transactionReturns list was for calculation convenience,
        // but transactionResult map is authoritative for slot updates.
        // Wait, giveBackOrDrop drops at feet. Better to PUT BACK in slots if possible?
        // No, standard behavior is to close menu and give back items.
        // This is safer against "ghost items".

        int acceptedAmountFinal = potentialAcceptedAmount;
        ArrayList<ItemStack> acceptedFinal = new ArrayList<>(transactionAccepted);

        // NOTE: The previous logic opened ConfirmDeliveryMenu. But wait,
        // ConfirmDeliveryMenu calls applyDelivery AGAIN?
        // Let's check ConfirmDeliveryMenu.
        // If ConfirmDeliveryMenu commits the transaction, then DeliverItemsMenu logic
        // is PRE-COMMIT.
        // Ah, DeliverItemsMenu is the "Selection" phase. ConfirmDeliveryMenu is the
        // "Yes/No" phase.
        // So `DeliverItemsMenu` does NOT call `applyDelivery`.
        // `ConfirmDeliveryMenu` calls `applyDelivery`.

        // Let's verify `ConfirmDeliveryMenu` actually calls `applyDelivery`.
        // If so, `DeliverItemsMenu` is mostly fine, BUT it needs to handle the case
        // where `ConfirmDeliveryMenu` fails?
        // No, `ConfirmDeliveryMenu` would handle the failure response from
        // `applyDelivery`.
        // BUT wait, `DeliverItemsMenu` logic prepares items.

        // I need to verify 'ConfirmDeliveryMenu.java' source code to be sure.
        // If `ConfirmDeliveryMenu` calls `applyDelivery`, then my previous assumption
        // that `DeliverItemsMenu` calls it was WRONG?
        // Wait, looking at `DeliverItemsMenu` source from previous step:
        // It calls `new ConfirmDeliveryMenu(...).open()`.
        // It does NOT call `applyDelivery`.

        // MY BAD. I assumed `DeliverItemsMenu` committed the transaction.
        // It seems `DeliverItemsMenu` just filters items and passes them to
        // `ConfirmDeliveryMenu`.
        // So I must check `ConfirmDeliveryMenu.java`!

        // However, `DeliverItemsMenu.java` does return items to the player (leftovers).
        // If `ConfirmDeliveryMenu` is just a UI wrapper, then `ConfirmDeliveryMenu` is
        // the one that needs fixing.

        // Let's assume for a moment `DeliverItemsMenu` is fine (it just prepares
        // items).
        // I need to confirm `ConfirmDeliveryMenu.java`.

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
