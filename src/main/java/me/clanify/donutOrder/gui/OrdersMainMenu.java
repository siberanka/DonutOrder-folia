/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 */
package me.clanify.donutOrder.gui;

import java.lang.invoke.CallSite;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.Utils;
import me.clanify.donutOrder.data.Order;
import me.clanify.donutOrder.data.SortType;
import me.clanify.donutOrder.gui.DeliverItemsMenu;
import me.clanify.donutOrder.gui.GuiVariant;
import me.clanify.donutOrder.gui.MenuOwner;
import me.clanify.donutOrder.gui.YourOrdersMenu;
import me.clanify.donutOrder.store.PlayerStateManager;
import me.clanify.donutOrder.utils.SignInputUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class OrdersMainMenu
        implements InventoryHolder,
        MenuOwner {
    private final DonutOrder pl;
    private final Player p;
    private Inventory inv;

    public OrdersMainMenu(DonutOrder pl, Player p) {
        this.pl = pl;
        this.p = p;
    }

    public Inventory getInventory() {
        return this.inv;
    }

    private int rows() {
        return this.pl.cfg().rows("orders", 6);
    }

    private int perPage() {
        return (this.rows() - 1) * 9;
    }

    public void open() {
        Set<Material> allow;
        int rows = this.rows();
        this.inv = Bukkit.createInventory((InventoryHolder) this, (int) (rows * 9),
                (String) this.pl.cfg().title("orders", "&#44b3ffOrders"));
        int prev = this.pl.cfg().slot("gui.orders.items.prev", 45);
        int sort = this.pl.cfg().slot("gui.orders.items.sort", 47);
        int filt = this.pl.cfg().slot("gui.orders.items.filter", 48);
        int ref = this.pl.cfg().slot("gui.orders.items.refresh", 49);
        int srch = this.pl.cfg().slot("gui.orders.items.search", 50);
        int your = this.pl.cfg().slot("gui.orders.items.your", 51);
        int next = this.pl.cfg().slot("gui.orders.items.next", 53);
        this.inv.setItem(prev, this.pl.cfg().button("gui.orders.items.prev", "ARROW", "&fPrevious Page", List.of()));
        this.inv.setItem(next, this.pl.cfg().button("gui.orders.items.next", "ARROW", "&fNext Page", List.of()));
        this.inv.setItem(ref,
                this.pl.cfg().button("gui.orders.items.refresh", "MAP", "&fRefresh", List.of("&fClick to refresh")));
        this.inv.setItem(srch,
                this.pl.cfg().button("gui.orders.items.search", "OAK_SIGN", "&fSearch", List.of("&fClick to search")));
        this.inv.setItem(your, this.pl.cfg().button("gui.orders.items.your", "CHEST", "&fYour Orders",
                List.of("&fClick to view your orders")));
        PlayerStateManager.View st = this.pl.state().main(this.p.getUniqueId());
        if (st.sort == null) {
            st.sort = SortType.MOST_PAID;
        }
        if (st.filter == null || st.filter.isBlank()) {
            st.filter = "All";
        }
        if (st.page < 0) {
            st.page = 0;
        }
        String sel = this.pl.cfg().selectedPrefix("orders");
        String uns = this.pl.cfg().unselectedPrefix("orders");
        List<String> sortLore = List
                .of((st.sort == SortType.MOST_PAID ? sel : uns) + this.nameFor(SortType.MOST_PAID),
                        (st.sort == SortType.MOST_DELIVERED ? sel : uns) + this.nameFor(SortType.MOST_DELIVERED),
                        (st.sort == SortType.RECENTLY_LISTED ? sel : uns) + this.nameFor(SortType.RECENTLY_LISTED),
                        (st.sort == SortType.MOST_MONEY_PER_ITEM ? sel : uns)
                                + this.nameFor(SortType.MOST_MONEY_PER_ITEM))
                .stream().map(Utils::formatColors).toList();
        this.inv.setItem(sort, this.pl.cfg().button("gui.orders.items.sort", "CAULDRON", "&fSort", sortLore));
        ArrayList<String> cats = new ArrayList<String>();
        cats.add("All");
        cats.addAll(this.pl.filters().categoryNames());
        ArrayList<String> filtLore = new ArrayList<>();
        for (String c : cats) {
            filtLore.add((c.equalsIgnoreCase(st.filter) ? sel : uns) + c);
        }
        this.inv.setItem(filt, this.pl.cfg().button("gui.orders.items.filter", "HOPPER", "&fFilter",
                filtLore.stream().map(Utils::formatColors).toList()));
        List<Order> list = this.pl.orders().all().stream().filter(o -> !o.canceled).filter(o -> !o.completed)
                .collect(Collectors.toList());
        if (st.search != null && !st.search.isBlank()) {
            String s = st.search.toLowerCase(Locale.ENGLISH);
            list.removeIf(o -> {
                String disp = o.key.displayName().toLowerCase(Locale.ENGLISH);
                String mat = o.key.material.name().toLowerCase(Locale.ENGLISH);
                return !disp.contains(s) && !mat.contains(s);
            });
        }
        if (!"All".equalsIgnoreCase(st.filter) && (allow = this.pl.filters().resolve(st.filter)) != null
                && !allow.isEmpty()) {
            list.removeIf(o -> !allow.contains(o.key.material));
        }
        switch (st.sort) {
            case MOST_PAID: {
                list.sort(Comparator.comparingDouble(o -> -((double) o.delivered * o.priceEach)));
                break;
            }
            case MOST_DELIVERED: {
                list.sort(Comparator.comparingInt(o -> -o.delivered));
                break;
            }
            case RECENTLY_LISTED: {
                Collections.reverse(list);
                break;
            }
            case MOST_MONEY_PER_ITEM: {
                list.sort(Comparator.comparingDouble(o -> -o.priceEach));
            }
        }
        int per = this.perPage();
        int max = Math.max(0, (list.size() - 1) / Math.max(1, per));
        if (st.page > max) {
            st.page = max;
        }
        int from = st.page * per;
        int to = Math.min(from + per, list.size());
        int idx = 0;
        for (int i = from; i < to; ++i) {
            Order o2 = list.get(i);
            OfflinePlayer op = Bukkit.getOfflinePlayer((UUID) o2.owner);
            String owner = op != null && op.getName() != null ? op.getName() : "Unknown";
            HashMap<String, String> ph = new HashMap<String, String>();
            ph.put("player", owner);
            ph.put("item", o2.key.displayName());
            ph.put("price_each", Utils.abbr(o2.priceEach));
            ph.put("delivered", Utils.abbr(o2.delivered));
            ph.put("requested", Utils.abbr(o2.requested));
            ph.put("paid", Utils.abbr((double) o2.delivered * o2.priceEach));
            ph.put("total", Utils.abbr(o2.totalPrice()));
            ArrayList<String> lore = new ArrayList<String>(
                    List.of("&a${price_each} &7each", "", "{delivered}/{requested} &7Delivered", "${paid}/${total}", "",
                            "&fClick to deliver {player} {item}"));
            List<String> enchLore = o2.key.enchantLoreLines("&7");
            if (!enchLore.isEmpty()) {
                lore.add("&7");
                lore.addAll(enchLore);
            }
            ItemStack ui = this.pl.cfg().dynamicItem(o2.key.material, "gui.orders.items.order-item",
                    "&f{player}'s Order", lore, ph);
            ui = GuiVariant.merge(ui, o2.key.buildIcon());
            this.inv.setItem(idx++, ui);
        }
        this.p.openInventory(this.inv);
    }

    private String nameFor(SortType which) {
        return switch (which) {
            case MOST_PAID -> "Most Paid";
            case MOST_DELIVERED -> "Most Delivered";
            case RECENTLY_LISTED -> "Recently Listed";
            case MOST_MONEY_PER_ITEM -> "Most Money Per Item";
            default -> throw new IllegalStateException("Unexpected value: " + which);
        };
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Set<Material> allow;
        if (e.getClickedInventory() == null) {
            return;
        }
        if (e.getClickedInventory().getHolder() != this) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        PlayerStateManager.View st = this.pl.state().main(this.p.getUniqueId());
        int prev = this.pl.cfg().slot("gui.orders.items.prev", 45);
        int sort = this.pl.cfg().slot("gui.orders.items.sort", 47);
        int filt = this.pl.cfg().slot("gui.orders.items.filter", 48);
        int ref = this.pl.cfg().slot("gui.orders.items.refresh", 49);
        int srch = this.pl.cfg().slot("gui.orders.items.search", 50);
        int your = this.pl.cfg().slot("gui.orders.items.your", 51);
        int next = this.pl.cfg().slot("gui.orders.items.next", 53);
        int slot = e.getSlot();
        if (slot == prev) {
            st.page = Math.max(0, st.page - 1);
            this.pl.cfg().play(this.p, "sounds.page", "UI_BUTTON_CLICK", 1.0f, 1.1f);
            this.open();
            return;
        }
        if (slot == next) {
            ++st.page;
            this.pl.cfg().play(this.p, "sounds.page", "UI_BUTTON_CLICK", 1.0f, 1.1f);
            this.open();
            return;
        }
        if (slot == sort) {
            st.sort = this.nextSort(st.sort);
            this.pl.state().saveAllPrefs();
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            st.page = 0;
            this.open();
            return;
        }
        if (slot == filt) {
            ArrayList<String> cats = new ArrayList<String>();
            cats.add("All");
            cats.addAll(this.pl.filters().categoryNames());
            int i = Math.max(0, cats.indexOf(st.filter));
            st.filter = (String) cats.get((i + 1) % cats.size());
            this.pl.state().saveAllPrefs();
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            st.page = 0;
            this.open();
            return;
        }
        if (slot == ref) {
            st.search = null;
            st.page = 0;
            this.pl.state().saveAllPrefs();
            this.pl.cfg().play(this.p, "sounds.refresh", "BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.3f);
            this.open();
            return;
        }
        if (slot == srch) {
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            this.p.closeInventory();
            ConfigurationSection sec = this.pl.cfg().cfg().getConfigurationSection("search-sign");
            SignInputUtil.openFromConfig(this.pl, this.p, sec, input -> {
                String trimmed;
                PlayerStateManager.View st2 = this.pl.state().main(this.p.getUniqueId());
                String string = trimmed = input == null ? "" : input.trim();
                if (trimmed.equals("-")) {
                    trimmed = "";
                }
                st2.search = trimmed.isEmpty() ? null : trimmed;
                st2.page = 0;
                this.pl.state().saveAllPrefs();
                new OrdersMainMenu(this.pl, this.p).open();
            });
            return;
        }
        if (slot == your) {
            this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
            new YourOrdersMenu(this.pl, this.p).open();
            return;
        }
        int per = this.perPage();
        int index = st.page * per + slot;
        List<Order> list = this.pl.orders().all().stream().filter(o -> !o.canceled).filter(o -> !o.completed)
                .collect(Collectors.toList());
        if (st.search != null && !st.search.isBlank()) {
            String s2 = st.search.toLowerCase(Locale.ENGLISH);
            list.removeIf(o -> {
                String disp = o.key.displayName().toLowerCase(Locale.ENGLISH);
                String mat = o.key.material.name().toLowerCase(Locale.ENGLISH);
                return !disp.contains(s2) && !mat.contains(s2);
            });
        }
        if (!"All".equalsIgnoreCase(st.filter) && (allow = this.pl.filters().resolve(st.filter)) != null
                && !allow.isEmpty()) {
            list.removeIf(o -> !allow.contains(o.key.material));
        }
        switch (st.sort) {
            case MOST_PAID: {
                list.sort(Comparator.comparingDouble(o -> -((double) o.delivered * o.priceEach)));
                break;
            }
            case MOST_DELIVERED: {
                list.sort(Comparator.comparingInt(o -> -o.delivered));
                break;
            }
            case RECENTLY_LISTED: {
                Collections.reverse(list);
                break;
            }
            case MOST_MONEY_PER_ITEM: {
                list.sort(Comparator.comparingDouble(o -> -o.priceEach));
            }
        }
        if (index >= 0 && index < list.size()) {
            Order target = list.get(index);
            if (target.owner.equals(this.p.getUniqueId())) {
                this.pl.cfg().play(this.p, "sounds.click", "UI_BUTTON_CLICK", 1.0f, 1.0f);
                return;
            }
            new DeliverItemsMenu(this.pl, this.p, target).open();
        }
    }

    private SortType nextSort(SortType cur) {
        return switch (cur) {
            case MOST_PAID -> SortType.MOST_DELIVERED;
            case MOST_DELIVERED -> SortType.RECENTLY_LISTED;
            case RECENTLY_LISTED -> SortType.MOST_MONEY_PER_ITEM;
            case MOST_MONEY_PER_ITEM -> SortType.MOST_PAID;
            default -> throw new IllegalStateException("Unexpected value: " + cur);
        };
    }

    @Override
    public void onClose(InventoryCloseEvent e) {
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
