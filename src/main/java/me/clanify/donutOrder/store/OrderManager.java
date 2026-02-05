/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.md_5.bungee.api.ChatMessageType
 *  net.md_5.bungee.api.chat.TextComponent
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.plugin.Plugin
 */
package me.clanify.donutOrder.store;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.Utils;
import me.clanify.donutOrder.data.ItemKey;
import me.clanify.donutOrder.data.Order;
import me.clanify.donutOrder.util.TaskUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public class OrderManager {
    private final DonutOrder pl;
    public static final int MAX_STORAGE_SIZE = 3500; // DoS Protection Limit
    private final Map<UUID, Order> orders = new LinkedHashMap<UUID, Order>();
    private final java.util.concurrent.ExecutorService ioExecutor = java.util.concurrent.Executors
            .newSingleThreadExecutor();
    private final File ordersDir;

    public OrderManager(DonutOrder pl) {
        this.pl = pl;
        if (!pl.getDataFolder().exists()) {
            pl.getDataFolder().mkdirs();
        }

        // Individual order files directory
        this.ordersDir = new File(pl.getDataFolder(), "orders");
        if (!this.ordersDir.exists()) {
            this.ordersDir.mkdirs();
        }

        this.loadAll();
    }

    public void shutdown() {
        this.ioExecutor.shutdown();
        try {
            if (!this.ioExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                this.ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            this.ioExecutor.shutdownNow();
        }
    }

    public Collection<Order> all() {
        return this.orders.values();
    }

    public Order create(UUID owner, Material chosenMaterial, int amount, double priceEach) {
        return this.create(owner, ItemKey.of(chosenMaterial), amount, priceEach);
    }

    public Order create(UUID owner, ItemKey key, int amount, double priceEach) {
        if (amount <= 0)
            throw new IllegalArgumentException("Amount must be positive");
        if (priceEach <= 0)
            throw new IllegalArgumentException("Price must be positive");
        if (Double.isNaN(priceEach) || Double.isInfinite(priceEach))
            throw new IllegalArgumentException("Invalid price");

        // DoS Protection: Limit total orders per player
        long count = this.orders.values().stream().filter(o -> o.owner.equals(owner) && !o.completed).count();
        int limit = 50; // Hard limit for safety
        if (count >= limit) {
            throw new IllegalStateException("You have too many active orders! (Max: " + limit + ")");
        }

        Order o = new Order();
        o.id = UUID.randomUUID();
        o.owner = owner;
        o.key = key;
        o.requested = Math.max(1, amount);
        o.delivered = 0;
        o.priceEach = priceEach;
        o.paid = o.totalPrice();
        o.canceled = false;
        o.completed = false;
        this.orders.put(o.id, o);
        this.saveOrder(o);
        return o;
    }

    public void cancel(Order o) {
        o.canceled = true;
        int remaining = o.remainingAmount();
        double refund = (double) remaining * o.priceEach;
        OfflinePlayer owner = Bukkit.getOfflinePlayer((UUID) o.owner);
        if (owner.isOnline()) {
            this.pl.vault().give((OfflinePlayer) owner.getPlayer(), refund);
        }
        o.requested = o.delivered;
        o.completed = true;
        this.saveOrder(o);
    }

    public void delete(Order o) {
        // Ensure refund happens if not already completed/canceled
        if (!o.completed && !o.canceled) {
            this.cancel(o); // Use cancel logic for refund
        }

        // Remove from memory
        this.orders.remove(o.id);

        // Remove from disk (Async I/O)
        final UUID oid = o.id;
        this.ioExecutor.submit(() -> {
            File f = new File(ordersDir, oid.toString() + ".yml");
            if (f.exists()) {
                f.delete();
            }
        });
    }

    public void applyDelivery(Order o, List<ItemStack> accepted, int acceptedAmount, UUID deliverer) {
        if (acceptedAmount <= 0) {
            return;
        }
        for (ItemStack it : accepted) {
            if (it == null || it.getType() == Material.AIR || it.getAmount() <= 0)
                continue;
            o.storage.add(it);
        }
        double receive = (double) acceptedAmount * o.priceEach;
        Player delivererPlayer = Bukkit.getPlayer((UUID) deliverer);
        if (delivererPlayer != null) {
            this.pl.vault().give((OfflinePlayer) delivererPlayer, receive);
        }
        o.delivered += acceptedAmount;
        if (o.delivered >= o.requested) {
            o.completed = true;
        }
        o.paid = Math.max(0.0, o.totalPrice() - (double) o.delivered * o.priceEach);
        this.saveOrder(o);
        this.sendReceiverActionbar(o, deliverer, acceptedAmount);
    }

    private void sendReceiverActionbar(Order o, UUID deliverer, int acceptedAmount) {
        Player receiver = Bukkit.getPlayer((UUID) o.owner);
        if (receiver == null) {
            return;
        }
        TaskUtil.runEntity((Plugin) this.pl, (Entity) receiver, () -> {
            String delivererName = "Someone";
            Player d = Bukkit.getPlayer((UUID) deliverer);
            if (d != null && d.getName() != null) {
                delivererName = d.getName();
            } else {
                OfflinePlayer op = Bukkit.getOfflinePlayer((UUID) deliverer);
                if (op != null && op.getName() != null) {
                    delivererName = op.getName();
                }
            }
            HashMap<String, String> ph = new HashMap<String, String>();
            ph.put("player", delivererName);
            ph.put("amount", String.valueOf(acceptedAmount));
            ph.put("item", o.key.displayName());
            String raw = this.pl.cfg().cfg().getString("messages.received_actionbar",
                    "&a{player} has delivered you {amount} {item}!");
            String msg = Utils.applyPlaceholders(raw, ph);
            msg = Utils.formatColors(msg);
            receiver.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText((String) msg));
        });
    }

    private void loadAll() {
        this.orders.clear();
        if (!ordersDir.exists())
            return;

        File[] files = ordersDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null)
            return;

        for (File f : files) {
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);

                // The root of the file is the order itself now, not key-nested
                // However, preserving the simpler key-value structure inside might be easier?
                // Actually, cleaner is just to set keys at root.
                // Let's assume the saveOrder method saves at root.

                String idStr = f.getName().replace(".yml", "");
                UUID id;
                try {
                    id = UUID.fromString(idStr);
                } catch (IllegalArgumentException e) {
                    // Try to read 'id' from inside if filename is weird, or skip
                    String internalId = cfg.getString("id");
                    if (internalId != null) {
                        id = UUID.fromString(internalId);
                    } else {
                        pl.getLogger().warning("Skipping invalid order file: " + f.getName());
                        continue;
                    }
                }

                String ownerStr = cfg.getString("owner");
                String itemStr = cfg.getString("item");

                if (ownerStr == null || itemStr == null) {
                    // Attempt legacy migration for format if it was saved strangely?
                    // No, this is fresh load of new files.
                    continue;
                }

                Order o = new Order();
                o.id = id;
                o.owner = UUID.fromString(ownerStr);
                o.key = ItemKey.deserialize(itemStr);
                o.requested = cfg.getInt("requested");
                o.delivered = cfg.getInt("delivered");
                o.priceEach = cfg.getDouble("priceEach");
                o.paid = cfg.getDouble("paid");
                o.canceled = cfg.getBoolean("canceled");
                o.completed = cfg.getBoolean("completed");

                // Load storage - support both legacy ItemStack format and new Base64 format
                List<?> raw = cfg.getList("storage");
                if (raw != null) {
                    for (Object ois : raw) {
                        if (ois instanceof String) {
                            // New Base64 format
                            ItemStack item = itemFromBase64((String) ois);
                            if (item != null) {
                                o.storage.add(item);
                            }
                        } else if (ois instanceof ItemStack) {
                            // Legacy format
                            o.storage.add((ItemStack) ois);
                        }
                    }
                }
                this.orders.put(o.id, o);
            } catch (Exception ex) {
                this.pl.getLogger().warning("Skipping corrupt order file '" + f.getName() + "': " + ex.getMessage());
            }
        }
    }

    public void saveOrder(Order o) {
        // Clone items on main thread (fast) for thread safety, then serialize async
        List<ItemStack> storageClone = new ArrayList<>();
        for (ItemStack item : o.storage) {
            if (item != null && item.getType() != Material.AIR) {
                storageClone.add(item.clone());
            }
        }

        // Capture all order data for async save
        final UUID orderId = o.id;
        final String ownerStr = o.owner.toString();
        final String itemStr = o.key.serialize();
        final int requested = o.requested;
        final int delivered = o.delivered;
        final double priceEach = o.priceEach;
        final double paid = o.paid;
        final boolean canceled = o.canceled;
        final boolean completed = o.completed;

        // Run EVERYTHING async - strictly sequential via single-thread executor to
        // prevent corruptions
        this.ioExecutor.submit(() -> {
            // Serialize items to Base64 on async thread
            List<String> storageBase64 = new ArrayList<>();
            for (ItemStack item : storageClone) {
                String encoded = itemToBase64(item);
                if (encoded != null) {
                    storageBase64.add(encoded);
                }
            }

            File f = new File(ordersDir, orderId.toString() + ".yml");
            YamlConfiguration cfg = new YamlConfiguration();

            cfg.set("id", orderId.toString());
            cfg.set("owner", ownerStr);
            cfg.set("item", itemStr);
            cfg.set("requested", requested);
            cfg.set("delivered", delivered);
            cfg.set("priceEach", priceEach);
            cfg.set("paid", paid);
            cfg.set("canceled", canceled);
            cfg.set("completed", completed);
            cfg.set("storage", storageBase64);

            try {
                cfg.save(f);
            } catch (IOException ex) {
                pl.getLogger().severe("Failed to save order " + orderId + ": " + ex.getMessage());
            }
        });
    }

    public void saveAll() {
        for (Order o : this.orders.values()) {
            this.saveOrder(o);
        }
    }

    private void saveRoot() {
        // Deprecated/Unused in new system
    }

    /**
     * Serialize an ItemStack to a Base64 string.
     */
    private static String itemToBase64(ItemStack item) {
        if (item == null)
            return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos);
            oos.writeObject(item);
            oos.close();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Deserialize an ItemStack from a Base64 string.
     */
    private static ItemStack itemFromBase64(String base64) {
        if (base64 == null || base64.isEmpty())
            return null;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
            BukkitObjectInputStream ois = new BukkitObjectInputStream(bais);
            ItemStack item = (ItemStack) ois.readObject();
            ois.close();
            return item;
        } catch (Exception e) {
            return null;
        }
    }

    public static String nice(Material m) {
        String s = m.name().toLowerCase(Locale.ENGLISH).replace('_', ' ');
        String[] parts = s.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty())
                continue;
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return out.toString().trim();
    }
}
