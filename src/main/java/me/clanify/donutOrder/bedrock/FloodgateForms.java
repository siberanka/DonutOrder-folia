package me.clanify.donutOrder.bedrock;

import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.Utils;
import me.clanify.donutOrder.data.ItemKey;
import me.clanify.donutOrder.data.Order;
import me.clanify.donutOrder.data.SortType;
import me.clanify.donutOrder.store.OrderManager;
import me.clanify.donutOrder.store.PlayerStateManager;
import me.clanify.donutOrder.util.TaskUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class FloodgateForms {
    private final DonutOrder pl;
    private final Set<java.util.UUID> activeForms = ConcurrentHashMap.newKeySet();
    private final Set<java.util.UUID> viewingOrders = ConcurrentHashMap.newKeySet();
    // Rate limiter: prevent rapid clicks (500ms cooldown)
    private final Map<java.util.UUID, Long> lastActionTime = new ConcurrentHashMap<>();
    private static final long ACTION_COOLDOWN_MS = 500;

    public FloodgateForms(DonutOrder pl) {
        this.pl = pl;
    }

    /**
     * Rate limiting check - prevents rapid click exploits
     * 
     * @return true if action should be blocked (too fast)
     */
    private boolean isRateLimited(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastActionTime.get(p.getUniqueId());
        if (last != null && (now - last) < ACTION_COOLDOWN_MS) {
            return true;
        }
        lastActionTime.put(p.getUniqueId(), now);
        return false;
    }

    public boolean isBedrockPlayer(Player p) {
        return FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
    }

    public boolean isViewingOrders(Player p) {
        return viewingOrders.contains(p.getUniqueId());
    }

    private String msg(String key, String def, Map<String, String> placeholders) {
        String s = pl.lang().get(key, def);
        if (placeholders != null && !placeholders.isEmpty()) {
            s = Utils.applyPlaceholders(s, placeholders);
        }
        return Utils.formatColors(s);
    }

    private String msg(String key, String def) {
        return msg(key, def, null);
    }

    public void sendOrdersMenu(Player p) {
        if (!pl.isEnabled())
            return;
        FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(p.getUniqueId());
        if (fp == null)
            return;

        SimpleForm.Builder form = SimpleForm.builder()
                .title(pl.cfg().title("orders", "Orders"))
                .content("");

        // Buttons
        Map<String, String> ph = new HashMap<>();
        ph.put("value", getSortName(p));
        form.button(msg("bedrock.orders.sort_btn", "Sort: {value}", ph));

        ph.put("value", getFilterName(p));
        form.button(msg("bedrock.orders.filter_btn", "Filter: {value}", ph));

        form.button(msg("bedrock.orders.search_btn", "Search"));
        form.button(msg("bedrock.orders.new_btn", "Create New Order"));
        form.button(msg("bedrock.orders.your_orders_btn", "Your Orders"));
        form.button(msg("bedrock.orders.refresh_btn", "Refresh"));

        // Logic to get orders
        PlayerStateManager.View st = pl.state().main(p.getUniqueId());
        if (st.sort == null)
            st.sort = SortType.MOST_PAID;
        if (st.filter == null || st.filter.isBlank())
            st.filter = "All";

        List<Order> list = pl.orders().all().stream()
                .filter(o -> !o.canceled && !o.completed)
                .collect(Collectors.toList());

        applySorting(list, st.sort);

        if (!"All".equalsIgnoreCase(st.filter)) {
            var allow = pl.filters().resolve(st.filter);
            if (allow != null) {
                list.removeIf(o -> !allow.contains(o.key.material));
            }
        }

        if (st.search != null && !st.search.isBlank()) {
            String s = st.search.toLowerCase();
            list.removeIf(o -> {
                String disp = o.key.displayName().toLowerCase();
                String mat = o.key.material.name().toLowerCase();
                return !disp.contains(s) && !mat.contains(s);
            });
        }

        for (Order o : list) {
            Map<String, String> oph = new HashMap<>();
            oph.put("name", o.key.displayName());
            oph.put("amount", Utils.abbr(o.requested));
            oph.put("price", Utils.abbr(o.priceEach));
            OfflinePlayer op = Bukkit.getOfflinePlayer(o.owner);
            oph.put("owner", op != null && op.getName() != null ? op.getName() : "Unknown");

            form.button(msg("bedrock.orders.item_format", "{name} (x{amount}) - ${price} - {owner}", oph));
        }

        form.validResultHandler(response -> {
            activeForms.remove(p.getUniqueId());
            if (isRateLimited(p))
                return;
            TaskUtil.runEntity(pl, p, () -> {
                int index = response.clickedButtonId();
                if (index == 0) {
                    st.sort = nextSort(st.sort);
                    pl.state().saveAllPrefs();
                    sendOrdersMenu(p);
                } else if (index == 1) {
                    cycleFilter(st);
                    pl.state().saveAllPrefs();
                    sendOrdersMenu(p);
                } else if (index == 2) {
                    sendSearchInput(p);
                } else if (index == 3) {
                    sendNewOrderMenu(p);
                } else if (index == 4) {
                    sendYourOrdersMenu(p);
                } else if (index == 5) {
                    st.search = null;
                    pl.state().saveAllPrefs();
                    sendOrdersMenu(p);
                } else {
                    int orderIndex = index - 6;
                    if (orderIndex >= 0 && orderIndex < list.size()) {
                        Order target = list.get(orderIndex);
                        if (target.owner.equals(p.getUniqueId())) {
                            p.sendMessage(pl.cfg().msg("messages.own_order", "&cYou cannot complete your own order."));
                            return;
                        }
                        sendDeliverMenu(p, target);
                    }
                }
            });
        });

        form.closedOrInvalidResultHandler(response -> {
            activeForms.remove(p.getUniqueId());
            viewingOrders.remove(p.getUniqueId());
        });

        activeForms.add(p.getUniqueId());
        viewingOrders.add(p.getUniqueId());
        fp.sendForm(form.build());
    }

    public void sendNewOrderMenu(Player p) {
        if (!pl.isEnabled())
            return;
        FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(p.getUniqueId());
        if (fp == null)
            return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        boolean hasItem = hand != null && hand.getType() != Material.AIR;

        if (!hasItem) {
            // Keep old flow: open Java item catalog menu from Bedrock
            new me.clanify.donutOrder.gui.SelectItemMenu(pl, p).open();
            return;
        }

        // Hand has an item: let player choose between catalog selection and hand item.
        SimpleForm.Builder form = SimpleForm.builder()
                .title(msg("bedrock.new_order.choose_title", "New Order"))
                .content(msg("bedrock.new_order.choose_content", "How do you want to create the order?"));

        form.button(msg("bedrock.new_order.choose_item_btn", "Select Item"));
        form.button(msg("bedrock.new_order.from_hand_btn", "Use Item In Hand"));

        form.validResultHandler(response -> {
            activeForms.remove(p.getUniqueId());
            if (isRateLimited(p))
                return;
            TaskUtil.runEntity(pl, p, () -> {
                int idx = response.clickedButtonId();
                if (idx == 0) {
                    new me.clanify.donutOrder.gui.SelectItemMenu(pl, p).open();
                    return;
                }
                ItemStack current = p.getInventory().getItemInMainHand();
                if (current == null || current.getType() == Material.AIR) {
                    p.sendMessage(msg("bedrock.new_order.error_hand_missing", "&cItem missing from hand!"));
                    return;
                }
                sendOrderDetailsForm(p, ItemKey.fromStack(current), true);
            });
        });

        form.closedOrInvalidResultHandler(response -> {
            activeForms.remove(p.getUniqueId());
        });

        activeForms.add(p.getUniqueId());
        fp.sendForm(form.build());
    }

    public void sendOrderDetailsForm(Player p, ItemKey key, boolean fromHand) {
        if (!pl.isEnabled())
            return;
        FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(p.getUniqueId());
        if (fp == null)
            return;

        CustomForm.Builder form = CustomForm.builder();
        Map<String, String> ph = new HashMap<>();
        ph.put("item", key.displayName());

        if (fromHand) {
            form.title(msg("bedrock.new_order_bedrock.from_hand_title", "Sell {item}", ph));
        } else {
            form.title(msg("bedrock.new_order_bedrock.title", "New Order"));
        }

        form.label(Utils.formatColors("&7Item: &f" + key.displayName()));

        String defaultAmount = "1";
        if (fromHand) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand != null)
                defaultAmount = String.valueOf(hand.getAmount());
        }

        form.input(msg("bedrock.new_order.amount_input", "Amount"), "1", defaultAmount);
        form.input(msg("bedrock.new_order.price_input", "Price Per Item"), "10.0", "10.0");

        form.validResultHandler(response -> {
            activeForms.remove(p.getUniqueId());
            if (isRateLimited(p))
                return;
            TaskUtil.runEntity(pl, p, () -> {
                // Re-validate hand state if fromHand
                if (fromHand) {
                    ItemStack currentHand = p.getInventory().getItemInMainHand();
                    boolean currentHas = currentHand != null && currentHand.getType() != Material.AIR;
                    if (!currentHas) {
                        p.sendMessage(msg("bedrock.new_order.error_hand_missing", "&cItem missing from hand!"));
                        return;
                    }
                }

                String amountInput = response.next();
                String priceInput = response.next();

                int amount;
                try {
                    amount = Integer.parseInt(amountInput);
                } catch (NumberFormatException e) {
                    p.sendMessage(msg("bedrock.new_order.error_amount", "&cInvalid amount."));
                    return;
                }

                double price;
                try {
                    price = Double.parseDouble(priceInput);
                } catch (NumberFormatException e) {
                    p.sendMessage(msg("bedrock.new_order.error_price", "&cInvalid price."));
                    return;
                }

                if (amount <= 0) {
                    p.sendMessage(pl.cfg().msg("messages.amount_min", "&cAmount must be at least 1."));
                    return;
                }

                int maxItems = pl.cfg().getMaxItemsPerOrder();
                if (amount > maxItems) {
                    p.sendMessage(pl.cfg().msg("messages.amount_max", "&cAmount limit is {max} items per order.")
                            .replace("{max}", Utils.abbr(maxItems)));
                    return;
                }

                if (Double.isNaN(price) || Double.isInfinite(price) || price <= 0) {
                    p.sendMessage(pl.cfg().msg("messages.price_invalid", "&cPrice must be positive and valid."));
                    return;
                }

                double minPrice = pl.cfg().getMinPricePerItem();
                if (price < minPrice) {
                    p.sendMessage(pl.cfg().msg("messages.price_min", "&cMinimum price per item is ${min}.")
                            .replace("{min}", Utils.abbr(minPrice)));
                    return;
                }

                double maxPrice = pl.cfg().getMaxPricePerItem();
                if (price > maxPrice) {
                    p.sendMessage(pl.cfg().msg("messages.price_max", "&cMaximum price per item is ${max}.")
                            .replace("{max}", Utils.abbr(maxPrice)));
                    return;
                }

                double total = (double) amount * price;
                if (!pl.vault().take(p, total)) {
                    p.sendMessage(
                            pl.cfg().msg("messages.cannot_afford", "&cYou cannot afford this (${total}).")
                                    .replace("${total}", Utils.abbr(total)));
                    return;
                }

                try {
                    // If fromHand, refresh key from hand to catch enchanges/durability
                    ItemKey finalKey = key;
                    if (fromHand) {
                        ItemStack h = p.getInventory().getItemInMainHand();
                        if (h != null)
                            finalKey = ItemKey.fromStack(h);
                    }

                    pl.orders().create(p.getUniqueId(), finalKey, amount, price);
                    p.sendMessage(msg("bedrock.new_order.success", "&aOrder created!"));
                } catch (Exception ex) {
                    pl.vault().give(p, total);
                    p.sendMessage(msg("bedrock.new_order.error_generic", "&cError: {error}",
                            Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Unknown")));
                }
            });
        });

        form.closedOrInvalidResultHandler(response -> {
            activeForms.remove(p.getUniqueId());
        });

        activeForms.add(p.getUniqueId());
        fp.sendForm(form.build());
    }

    public void sendDeliverMenu(Player p, Order order) {
        if (!pl.isEnabled())
            return;
        FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(p.getUniqueId());
        if (fp == null)
            return;

        Order fresh = pl.orders().byId(order.id);
        if (fresh == null || fresh.canceled || fresh.completed || fresh.remainingAmount() <= 0) {
            p.sendMessage(pl.cfg().msg("messages.order_complete", "&aOrder complete!"));
            return;
        }

        int deliverable = countDeliverableFromInventory(p, fresh, fresh.remainingAmount());
        if (deliverable <= 0) {
            p.sendMessage(msg("bedrock.deliver.no_items", "&cYou do not have this item in your inventory."));
            return;
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("item", fresh.key.displayName());
        ph.put("price", Utils.abbr(fresh.priceEach));
        ph.put("remaining", Utils.abbr(fresh.remainingAmount()));
        ph.put("deliverable", Utils.abbr(deliverable));

        CustomForm.Builder form = CustomForm.builder()
                .title(msg("bedrock.deliver.title", "Deliver Items"))
                .label(msg("bedrock.deliver.content",
                        "Item: {item}\nPrice: {price}\nRemaining: {remaining}\nYou can deliver now: {deliverable}", ph))
                .input(
                        msg("bedrock.deliver.amount_input", "Amount To Deliver"),
                        msg("bedrock.deliver.amount_placeholder", "Enter amount"),
                        String.valueOf(Math.max(0, deliverable)));

        form.validResultHandler(response -> {
            activeForms.remove(p.getUniqueId());
            if (isRateLimited(p))
                return;
            TaskUtil.runEntity(pl, p, () -> {
                String raw = response.next();
                int requested;
                try {
                    requested = Integer.parseInt(raw);
                } catch (NumberFormatException ex) {
                    p.sendMessage(msg("bedrock.deliver.error_amount", "&cInvalid amount."));
                    return;
                }

                if (requested <= 0) {
                    p.sendMessage(msg("bedrock.deliver.error_amount", "&cInvalid amount."));
                    return;
                }

                if (requested > fresh.remainingAmount()) {
                    p.sendMessage(msg("bedrock.deliver.error_exceeds_remaining",
                            "&cYou can deliver at most {max} items for this order.",
                            Map.of("max", Utils.abbr(fresh.remainingAmount()))));
                    return;
                }

                if (requested > deliverable) {
                    p.sendMessage(msg("bedrock.deliver.error_exceeds_inventory",
                            "&cYou only have {max} deliverable items in your inventory.",
                            Map.of("max", Utils.abbr(deliverable))));
                    return;
                }

                processDelivery(p, fresh, requested);
            });
        });

        form.closedOrInvalidResultHandler(response -> {
            activeForms.remove(p.getUniqueId());
        });

        activeForms.add(p.getUniqueId());
        fp.sendForm(form.build());
    }

    private void processDelivery(Player p, Order order, int requestedAmount) {
        // Anti-exploit: Synchronized block to prevent race conditions
        synchronized (pl.orders().getLock(order.id)) {
            // Anti-exploit: Fresh order retrieval
            Order fresh = pl.orders().byId(order.id);
            if (fresh == null || fresh.canceled || fresh.completed) {
                p.sendMessage(pl.cfg().msg("messages.order_complete", "&aOrder complete!"));
                return;
            }

            if (fresh.remainingAmount() <= 0) {
                p.sendMessage(pl.cfg().msg("messages.order_complete", "&aOrder complete!"));
                return;
            }

            int remainingNeed = fresh.remainingAmount();
            if (requestedAmount <= 0) {
                p.sendMessage(msg("bedrock.deliver.error_amount", "&cInvalid amount."));
                return;
            }

            if (requestedAmount > remainingNeed) {
                p.sendMessage(msg("bedrock.deliver.error_exceeds_remaining",
                        "&cYou can deliver at most {max} items for this order.",
                        Map.of("max", Utils.abbr(remainingNeed))));
                return;
            }

            int deliverableNow = countDeliverableFromInventory(p, fresh, requestedAmount);
            if (deliverableNow < requestedAmount) {
                p.sendMessage(msg("bedrock.deliver.error_exceeds_inventory",
                        "&cYou only have {max} deliverable items in your inventory.",
                        Map.of("max", Utils.abbr(deliverableNow))));
                return;
            }

            List<ItemStack> toDeliver = new ArrayList<>();
            int collectedAmount = 0;

            ItemStack[] contents = p.getInventory().getContents();

            // --- PREPARATION PHASE ---
            // Collect items to deliver, BUT DO NOT REMOVE THEM YET if possible
            // Actually, Spigot API setItem is immediate.
            // To be safe, we must capture which slots we took from, to restore them if
            // transaction fails.
            // OR better: clone items to list, clear them, then if fail -> give them back.

            for (int i = 0; i < contents.length; i++) {
                ItemStack it = contents[i];
                if (it == null || it.getType() == Material.AIR)
                    continue;

                if (fresh.key.matches(it)) {
                    int canTake = Math.min(requestedAmount - collectedAmount, it.getAmount());
                    if (canTake > 0) {
                        ItemStack take = it.clone();
                        take.setAmount(canTake);
                        toDeliver.add(take);
                        collectedAmount += canTake;

                        if (it.getAmount() > canTake) {
                            it.setAmount(it.getAmount() - canTake);
                        } else {
                            p.getInventory().setItem(i, null);
                        }
                    }
                    if (collectedAmount >= requestedAmount)
                        break;
                }
            }

            if (collectedAmount != requestedAmount) {
                for (ItemStack item : toDeliver) {
                    HashMap<Integer, ItemStack> left = p.getInventory().addItem(item);
                    for (ItemStack drop : left.values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), drop);
                    }
                }
                p.sendMessage(msg("bedrock.deliver.error_inventory_changed",
                        "&cYour inventory changed during delivery. Please try again."));
                return;
            }

            if (collectedAmount > 0) {
                OrderManager.TransactionResult result = pl.orders().applyDelivery(fresh, toDeliver, collectedAmount,
                        p.getUniqueId());

                if (result == OrderManager.TransactionResult.SUCCESS) {
                    double totalEarned = collectedAmount * fresh.priceEach;
                    Map<String, String> ph = new HashMap<>();
                    ph.put("amount", String.valueOf(collectedAmount));
                    ph.put("total", Utils.abbr(totalEarned));
                    p.sendMessage(msg("bedrock.deliver.success", "&aDelivered!", ph));
                } else {
                    // FAILURE - ROLLBACK
                    // Restore items to player
                    // Since we modified inventory in-place, we might have partial stacks or nulls
                    // where items were.
                    // Safest way: Just give back the `toDeliver` list.
                    // The player logic above modified the inventory.
                    // We should add back the items we took.

                    for (ItemStack item : toDeliver) {
                        HashMap<Integer, ItemStack> left = p.getInventory().addItem(item);
                        for (ItemStack drop : left.values()) {
                            p.getWorld().dropItemNaturally(p.getLocation(), drop);
                        }
                    }

                    String reason;
                    if (result == OrderManager.TransactionResult.FAILED_ORDER_CLOSED)
                        reason = msg("bedrock.deliver.failed_closed", "&cOrder is no longer active.");
                    else if (result == OrderManager.TransactionResult.FAILED_ORDER_IN_USE)
                        reason = msg("bedrock.deliver.failed_in_use", "&cOrder is currently in use.");
                    else if (result == OrderManager.TransactionResult.FAILED_STORAGE_FULL)
                        reason = msg("bedrock.deliver.failed_storage_full", "&cOrder storage is full.");
                    else
                        reason = msg("bedrock.deliver.failed_generic", "&cDelivery failed: {reason}",
                                Map.of("reason", result.name()));

                    p.sendMessage(reason);
                }
            } else {
                p.sendMessage(msg("bedrock.deliver.no_items", "&cYou do not have this item in your inventory."));
            }
        } // End synchronized block
    }

    private int countDeliverableFromInventory(Player p, Order order, int cap) {
        int count = 0;
        int limit = Math.max(0, cap);
        ItemStack[] contents = p.getInventory().getContents();
        for (ItemStack it : contents) {
            if (it == null || it.getType() == Material.AIR) {
                continue;
            }
            if (!order.key.matches(it)) {
                continue;
            }
            count += it.getAmount();
            if (count >= limit) {
                return limit;
            }
        }
        return count;
    }

    public void sendYourOrdersMenu(Player p) {
        if (!pl.isEnabled())
            return;
        FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(p.getUniqueId());
        if (fp == null)
            return;

        SimpleForm.Builder form = SimpleForm.builder()
                .title(msg("bedrock.your_orders.title", "Your Orders"));

        List<Order> list = pl.orders().all().stream()
                .filter(o -> o.owner.equals(p.getUniqueId()))
                .filter(o -> !o.canceled)
                .filter(o -> !o.completed || !o.storage.isEmpty())
                .collect(Collectors.toList());

        for (Order o : list) {
            Map<String, String> ph = new HashMap<>();
            ph.put("item", o.key.displayName());
            ph.put("delivered", Utils.abbr(o.delivered));
            ph.put("requested", Utils.abbr(o.requested));
            form.button(msg("bedrock.your_orders.item_format", "{item} ({delivered}/{requested})", ph));
        }

        form.validResultHandler(response -> {
            activeForms.remove(p.getUniqueId());
            if (isRateLimited(p))
                return;
            TaskUtil.runEntity(pl, p, () -> {
                int idx = response.clickedButtonId();
                if (idx >= 0 && idx < list.size()) {
                    sendOrderActionMenu(p, list.get(idx));
                }
            });
        });

        form.closedOrInvalidResultHandler(response -> {
            activeForms.remove(p.getUniqueId());
        });

        activeForms.add(p.getUniqueId());
        fp.sendForm(form.build());
    }

    public void sendOrderActionMenu(Player p, Order o) {
        if (!pl.isEnabled())
            return;
        FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(p.getUniqueId());
        if (fp == null)
            return;

        Map<String, String> ph = new HashMap<>();
        ph.put("item", o.key.displayName());
        ph.put("delivered", Utils.abbr(o.delivered));
        ph.put("requested", Utils.abbr(o.requested));
        ph.put("paid", Utils.abbr(o.paid));

        SimpleForm.Builder form = SimpleForm.builder()
                .title(msg("bedrock.action.title", "Manage Order"))
                .content(msg("bedrock.action.content", "{item}", ph));

        // Build buttons dynamically based on order state
        // Button order determines index in handler:
        // For COMPLETED orders: [Collect Items (if storage), Collect Money, Back]
        // For ACTIVE orders: [Collect Money, Cancel Order, Back]

        boolean isCompleted = o.completed;
        boolean hasStorage = !o.storage.isEmpty();

        // Track button indices for handler
        int collectItemsIdx = -1;
        int collectMoneyIdx = -1;
        int cancelIdx = -1;
        int backIdx = -1;
        int btnCount = 0;

        if (isCompleted && hasStorage) {
            form.button(msg("bedrock.action.collect_items_btn", "Collect Items"));
            collectItemsIdx = btnCount++;
        }

        form.button(msg("bedrock.action.collect_btn", "Collect Money"));
        collectMoneyIdx = btnCount++;

        if (!isCompleted) {
            form.button(msg("bedrock.action.cancel_btn", "Cancel Order"));
            cancelIdx = btnCount++;
        }

        form.button(msg("bedrock.action.back_btn", "Back"));
        backIdx = btnCount;

        // Capture final values for lambda
        final int fCollectItemsIdx = collectItemsIdx;
        final int fCollectMoneyIdx = collectMoneyIdx;
        final int fCancelIdx = cancelIdx;
        final int fBackIdx = backIdx;

        form.validResultHandler(response -> {
            activeForms.remove(p.getUniqueId());
            TaskUtil.runEntity(pl, p, () -> {
                // Anti-exploit: Rate limiting
                if (isRateLimited(p)) {
                    return;
                }

                int idx = response.clickedButtonId();

                // Anti-exploit: Use synchronized block with order-specific lock
                synchronized (pl.orders().getLock(o.id)) {
                    // Anti-exploit: Fresh order retrieval to prevent stale data exploits
                    Order fresh = pl.orders().byId(o.id);
                    if (fresh == null) {
                        p.sendMessage(msg("bedrock.action.error_not_found", "&cOrder no longer exists."));
                        sendYourOrdersMenu(p);
                        return;
                    }

                    // Anti-exploit: Validate order is still owned by this player
                    if (!fresh.owner.equals(p.getUniqueId())) {
                        p.sendMessage(msg("bedrock.action.error_not_owner", "&cThis is not your order."));
                        sendYourOrdersMenu(p);
                        return;
                    }

                    // Anti-exploit: Check if order is already canceled
                    if (fresh.canceled) {
                        p.sendMessage(msg("bedrock.action.error_canceled", "&cOrder is already canceled."));
                        sendYourOrdersMenu(p);
                        return;
                    }

                    if (idx == fCollectItemsIdx && fCollectItemsIdx >= 0) {
                        // Collect Items - open the Java collect menu
                        new me.clanify.donutOrder.gui.CollectItemsMenu(pl, p, fresh).open();
                    } else if (idx == fCollectMoneyIdx) {
                        // Collect Money
                        double earned = fresh.paid;
                        if (earned > 0) {
                            // Anti-exploit: Set paid to 0 BEFORE giving money and saving
                            fresh.paid = 0;
                            pl.orders().saveOrder(fresh);
                            pl.vault().give(p, earned);
                            p.sendMessage(msg("bedrock.action.collected", "&aCollected {amount}",
                                    Map.of("amount", Utils.abbr(earned))));

                            // If order is now empty and completed, go back to main list
                            if (fresh.completed && fresh.storage.isEmpty()) {
                                sendYourOrdersMenu(p);
                            } else {
                                sendOrderActionMenu(p, fresh);
                            }
                        } else {
                            p.sendMessage(msg("bedrock.action.no_money", "&cNo money."));
                            sendOrderActionMenu(p, fresh);
                        }
                    } else if (idx == fCancelIdx && fCancelIdx >= 0) {
                        // Cancel Order - only available for non-completed orders
                        if (!fresh.canceled && fresh.delivered < fresh.requested) {
                            double refund = (fresh.requested - fresh.delivered) * fresh.priceEach;
                            // Anti-exploit: Mark as canceled BEFORE giving refund and saving
                            fresh.canceled = true;
                            pl.orders().saveOrder(fresh);
                            pl.vault().give(p, refund);
                            p.sendMessage(
                                    msg("bedrock.action.cancelled", "&aCancelled.",
                                            Map.of("amount", Utils.abbr(refund))));
                            sendYourOrdersMenu(p);
                        } else {
                            p.sendMessage(msg("bedrock.action.error_cannot_cancel", "&cCannot cancel this order."));
                            sendYourOrdersMenu(p);
                        }
                    } else if (idx == fBackIdx) {
                        sendYourOrdersMenu(p);
                    } else {
                        sendYourOrdersMenu(p);
                    }
                }
            });
        });

        form.closedOrInvalidResultHandler(response -> {
            activeForms.remove(p.getUniqueId());
        });

        activeForms.add(p.getUniqueId());
        fp.sendForm(form.build());
    }

    public void sendSearchInput(Player p) {
        if (!pl.isEnabled())
            return;
        FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(p.getUniqueId());
        if (fp == null)
            return;

        CustomForm.Builder form = CustomForm.builder()
                .title(msg("bedrock.search.title", "Search"))
                .input(msg("bedrock.search.input_label", "Search Query"),
                        msg("bedrock.search.placeholder", "e.g. Diamond"));

        form.validResultHandler(res -> {
            activeForms.remove(p.getUniqueId());
            if (isRateLimited(p))
                return;
            TaskUtil.runEntity(pl, p, () -> {
                String q = Utils.sanitizeSearch(res.next());
                pl.state().main(p.getUniqueId()).search = q;
                pl.state().saveAllPrefs();
                sendOrdersMenu(p);
            });
        });

        form.closedOrInvalidResultHandler(response -> {
            activeForms.remove(p.getUniqueId());
        });

        activeForms.add(p.getUniqueId());
        fp.sendForm(form.build());
    }

    public void closeAll() {
        if (!activeForms.isEmpty()) {
            SimpleForm.Builder form = SimpleForm.builder()
                    .title("Plugin Reloading")
                    .content("The plugin is reloading. Menus are closing.")
                    .button("Close");

            for (java.util.UUID uid : activeForms) {
                FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(uid);
                if (fp != null) {
                    fp.sendForm(form.build());
                }
            }
            activeForms.clear();
        }
        viewingOrders.clear();
        // Clear rate limiter data as well
        lastActionTime.clear();
    }

    // Helper methods
    private void applySorting(List<Order> list, SortType type) {
        switch (type) {
            case MOST_PAID -> list.sort(Comparator.comparingDouble(o -> -((double) o.delivered * o.priceEach)));
            case MOST_DELIVERED -> list.sort(Comparator.comparingInt(o -> -o.delivered));
            case RECENTLY_LISTED -> java.util.Collections.reverse(list);
            case MOST_MONEY_PER_ITEM -> list.sort(Comparator.comparingDouble(o -> -o.priceEach));
        }
    }

    private SortType nextSort(SortType cur) {
        return switch (cur) {
            case MOST_PAID -> SortType.MOST_DELIVERED;
            case MOST_DELIVERED -> SortType.RECENTLY_LISTED;
            case RECENTLY_LISTED -> SortType.MOST_MONEY_PER_ITEM;
            case MOST_MONEY_PER_ITEM -> SortType.MOST_PAID;
        };
    }

    private String getSortName(Player p) {
        String key = pl.state().main(p.getUniqueId()).sort.name();
        return pl.lang().get("sort-names." + key, key);
    }

    private String getFilterName(Player p) {
        String key = pl.state().main(p.getUniqueId()).filter;
        return pl.lang().get("filters." + key, key);
    }

    private void cycleFilter(PlayerStateManager.View st) {
        var cats = pl.filters().categoryNames();
        cats.add(0, "All");
        // Ensure accurate cycling logic
        int i = 0;
        for (int k = 0; k < cats.size(); k++) {
            if (cats.get(k).equalsIgnoreCase(st.filter)) {
                i = k;
                break;
            }
        }
        st.filter = cats.get((i + 1) % cats.size());
    }

    /**
     * Clean up Bedrock-specific state when a player disconnects.
     */
    public void onPlayerQuit(java.util.UUID uuid) {
        viewingOrders.remove(uuid);
        activeForms.remove(uuid);
        lastActionTime.remove(uuid);
        // Also ensure any storage locks are released
        pl.orders().unlockAll(uuid);
    }
}
