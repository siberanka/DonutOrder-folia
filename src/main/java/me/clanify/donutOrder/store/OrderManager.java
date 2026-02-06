package me.clanify.donutOrder.store;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.Utils;
import me.clanify.donutOrder.data.ItemKey;
import me.clanify.donutOrder.data.Order;
import me.clanify.donutOrder.util.TaskUtil;
import me.clanify.donutOrder.util.AtomicFileUtil;
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
    private final Map<UUID, Order> orders = new LinkedHashMap<>();
    private final java.util.concurrent.ExecutorService ioExecutor = java.util.concurrent.Executors
            .newSingleThreadExecutor();
    private final File ordersDir;
    // Anti-exploit: Per-order locks for race condition protection
    private final java.util.concurrent.ConcurrentHashMap<UUID, Object> orderLocks = new java.util.concurrent.ConcurrentHashMap<>();
    // Anti-exploit: Track which player is currently editing which order storage to
    // prevent race conditions
    private final java.util.concurrent.ConcurrentHashMap<UUID, UUID> editingOrders = new java.util.concurrent.ConcurrentHashMap<>();

    // Enum for transaction results
    public enum TransactionResult {
        SUCCESS,
        FAILED_ORDER_CLOSED,
        FAILED_STORAGE_FULL,
        FAILED_ECONOMY,
        FAILED_INVALID_ITEMS,
        FAILED_ORDER_IN_USE
    }

    public Object getLock(java.util.UUID orderId) {
        return orderLocks.computeIfAbsent(orderId, k -> new Object());
    }

    public boolean tryLockStorage(UUID orderId, UUID playerId) {
        UUID current = editingOrders.putIfAbsent(orderId, playerId);
        return current == null || current.equals(playerId);
    }

    public void unlockStorage(UUID orderId, UUID playerId) {
        editingOrders.remove(orderId, playerId);
    }

    public boolean isStorageLockedByOther(UUID orderId, UUID playerId) {
        UUID current = editingOrders.get(orderId);
        return current != null && !current.equals(playerId);
    }

    /**
     * Clean up all storage locks held by a player.
     * Prevents orphaned locks if a player crashes or disconnects mid-edit.
     */
    public void unlockAll(UUID playerId) {
        editingOrders.values().removeIf(id -> id.equals(playerId));
    }

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
            if (!this.ioExecutor.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS)) {
                this.ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.ioExecutor.shutdownNow();
        }
    }

    public Collection<Order> all() {
        return this.orders.values();
    }

    public Order byId(java.util.UUID orderId) {
        return this.orders.get(orderId);
    }

    private final Object globalLock = new Object();

    public Order create(UUID owner, ItemKey key, int amount, double priceEach) {
        if (amount <= 0)
            throw new IllegalArgumentException("Amount must be positive");
        if (priceEach <= 0)
            throw new IllegalArgumentException("Price must be positive");
        if (Double.isNaN(priceEach) || Double.isInfinite(priceEach))
            throw new IllegalArgumentException("Invalid price");

        synchronized (globalLock) {
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
            this.logAudit("CREATE", "Order:" + o.id + " Owner:" + owner + " Item:" + key.toString() + " Amount:"
                    + amount + " Price:" + priceEach);
            this.pl.menus().refreshAllOrders();
            return o;
        }
    }

    public void cancel(Order o) {
        synchronized (getLock(o.id)) {
            o.canceled = true;
            int remaining = o.remainingAmount();
            double refund = (double) remaining * o.priceEach;
            OfflinePlayer ownerOp = Bukkit.getOfflinePlayer((UUID) o.owner);
            this.pl.vault().give(ownerOp, refund);

            // Refund storage items to owner
            if (!o.storage.isEmpty()) {
                Player onlineOwner = Bukkit.getPlayer((UUID) o.owner);
                if (onlineOwner != null) {
                    for (ItemStack item : o.storage) {
                        HashMap<Integer, ItemStack> left = onlineOwner.getInventory().addItem(item);
                        for (ItemStack drop : left.values()) {
                            onlineOwner.getWorld().dropItemNaturally(onlineOwner.getLocation(), drop);
                        }
                    }
                }
                o.storage.clear();
            }

            o.requested = o.delivered;
            o.completed = true;
            this.saveOrder(o);
            this.logAudit("CANCEL", "Order:" + o.id + " Refund:" + refund);
            this.pl.menus().refreshAllOrders();
        }
    }

    public void delete(Order o) {
        synchronized (globalLock) {
            // Ensure refund happens if not already completed/canceled
            if (!o.completed && !o.canceled) {
                this.cancel(o); // Use cancel logic for refund
            }

            // Remove from memory
            this.orders.remove(o.id);
            this.pl.menus().refreshAllOrders();
        }

        // Remove from disk (Async I/O)
        final UUID oid = o.id;
        this.ioExecutor.submit(() -> {
            File f = new File(ordersDir, oid.toString() + ".yml");
            if (f.exists()) {
                f.delete();
            }
        });
    }

    public TransactionResult applyDelivery(Order o, List<ItemStack> accepted, int acceptedAmount, UUID deliverer) {
        if (acceptedAmount <= 0) {
            return TransactionResult.FAILED_INVALID_ITEMS;
        }
        // Anti-exploit: Synchronize on per-order lock to prevent race conditions
        synchronized (getLock(o.id)) {
            if (isStorageLockedByOther(o.id, deliverer)) {
                return TransactionResult.FAILED_ORDER_IN_USE;
            }
            // Check if order is still valid
            if (o.completed || o.canceled) {
                pl.getLogger().warning("Attempted delivery to completed/canceled order: " + o.id);
                return TransactionResult.FAILED_ORDER_CLOSED;
            }

            // Check storage limit
            if (o.storage.size() + accepted.size() > MAX_STORAGE_SIZE) {
                return TransactionResult.FAILED_STORAGE_FULL;
            }

            // --- PHASE 1: VALIDATION & SANITIZATION ---
            List<ItemStack> safeStorageToAdd = new ArrayList<>();
            int sanitizedAmount = 0;
            for (ItemStack it : accepted) {
                if (it == null || it.getType() == Material.AIR || it.getAmount() <= 0)
                    continue;

                if (o.key.isVariant()) {
                    // CONSTRUCTIVE SANITIZATION (Paranoid Mode):
                    // Do not trust the incoming item 'it'. Rebuild it from scratch.
                    ItemStack safe = new ItemStack(it.getType(), it.getAmount());
                    ItemMeta safeMeta = safe.getItemMeta();
                    ItemMeta origMeta = it.getItemMeta();

                    if (safeMeta instanceof PotionMeta && origMeta instanceof PotionMeta) {
                        PotionMeta pm = (PotionMeta) safeMeta;
                        PotionMeta originalPm = (PotionMeta) origMeta;
                        pm.setBasePotionData(originalPm.getBasePotionData());
                        safe.setItemMeta(pm);
                    } else if (safeMeta instanceof EnchantmentStorageMeta
                            && origMeta instanceof EnchantmentStorageMeta) {
                        EnchantmentStorageMeta em = (EnchantmentStorageMeta) safeMeta;
                        EnchantmentStorageMeta originalEm = (EnchantmentStorageMeta) origMeta;
                        originalEm.getStoredEnchants().forEach((ench, lvl) -> {
                            em.addStoredEnchant(ench, lvl, true);
                        });
                        safe.setItemMeta(em);
                    } else {
                        // Fallback: Treat as standard
                        safeStorageToAdd.add(new ItemStack(it.getType(), it.getAmount()));
                        sanitizedAmount += it.getAmount();
                        continue;
                    }
                    safeStorageToAdd.add(safe);
                    sanitizedAmount += safe.getAmount();
                } else {
                    // Non-variant: Strip everything (Vanilla)
                    ItemStack clean = new ItemStack(it.getType(), it.getAmount());
                    safeStorageToAdd.add(clean);
                    sanitizedAmount += clean.getAmount();
                }
            }

            if (sanitizedAmount != acceptedAmount || sanitizedAmount <= 0) {
                this.pl.getLogger().warning("Rejected delivery due to amount mismatch. order=" + o.id
                        + " acceptedAmount=" + acceptedAmount + " sanitizedAmount=" + sanitizedAmount);
                return TransactionResult.FAILED_INVALID_ITEMS;
            }

            // --- PHASE 2: TRANSACTION EXECUTION ---
            double receive = (double) acceptedAmount * o.priceEach;
            Player delivererPlayer = Bukkit.getPlayer((UUID) deliverer);

            // ATOMIC STEP 1: MONEY
            boolean moneyGiven = false;
            // Only give money if deliverer is online, otherwise vault might fail?
            // Vault usually supports offline players.
            OfflinePlayer delivererOp = delivererPlayer != null ? delivererPlayer : Bukkit.getOfflinePlayer(deliverer);
            moneyGiven = this.pl.vault().give(delivererOp, receive);

            if (!moneyGiven) {
                this.pl.getLogger().severe("Vault transaction failed for order " + o.id);
                return TransactionResult.FAILED_ECONOMY;
            }

            // ATOMIC STEP 2: STATE UPDATE (Commit)
            // Money is given, so we MUST commit items now.
            o.storage.addAll(safeStorageToAdd);

            o.delivered += acceptedAmount;
            if (o.delivered >= o.requested) {
                o.completed = true;
                // Notify owner of completion
                this.sendCompletedActionbar(o);
            }
            o.paid = Math.max(0.0, o.totalPrice() - (double) o.delivered * o.priceEach);

            // ATOMIC STEP 3: PERSISTENCE
            this.saveOrder(o);
            this.pl.menus().refreshAllOrders();
        }
        this.logAudit("DELIVERY",
                "Order:" + o.id + " Deliverer:" + deliverer + " Amount:" + acceptedAmount + " Price:" + o.priceEach);
        this.sendReceiverActionbar(o, deliverer, acceptedAmount);
        return TransactionResult.SUCCESS;
    }

    private void logAudit(String type, String details) {
        // Simple append-only log
        final String logEntry = String.format("[%s] [%s] %s%n",
                java.time.Instant.now().toString(), type, details);

        ioExecutor.submit(() -> {
            File logFile = new File(pl.getDataFolder(), "audit.log");

            // Log Rotation: If > 1MB, rename to .old and start fresh
            if (logFile.exists() && logFile.length() > 1024 * 1024) {
                File oldLogFile = new File(pl.getDataFolder(), "audit.log.old");
                if (oldLogFile.exists())
                    oldLogFile.delete();
                logFile.renameTo(oldLogFile);
            }

            try (java.io.FileWriter fw = new java.io.FileWriter(logFile, true)) {
                fw.write(logEntry);
            } catch (IOException e) {
                pl.getLogger().warning("Failed to write audit log: " + e.getMessage());
            }
        });
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

            // Use LangManager via msg() or direct lang() access
            // pl.cfg().msg() looks up in lang file first
            String msg = this.pl.cfg().msg("messages.received_actionbar",
                    "&a{player} has delivered you {amount} {item}!");
            msg = Utils.applyPlaceholders(msg, ph);
            receiver.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText((String) msg));
        });
    }

    private void sendCompletedActionbar(Order o) {
        Player receiver = Bukkit.getPlayer((UUID) o.owner);
        if (receiver == null) {
            return;
        }
        TaskUtil.runEntity((Plugin) this.pl, (Entity) receiver, () -> {
            HashMap<String, String> ph = new HashMap<String, String>();
            ph.put("item", o.key.displayName());

            String msg = this.pl.cfg().msg("messages.completed_actionbar", "&a{item} order is now COMPLETED!");
            msg = Utils.applyPlaceholders(msg, ph);
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

            // ATOMIC STEP 0: BACKUP (Durability)
            if (f.exists()) {
                File bak = new File(ordersDir, orderId.toString() + ".bak");
                try {
                    Files.copy(f.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    pl.getLogger().warning("Failed to create backup for order " + orderId + ": " + e.getMessage());
                }
            }

            final YamlConfiguration cfg = new YamlConfiguration();

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
                // Use AtomicFileUtil for safe writing
                AtomicFileUtil.write(f, (fos) -> {
                    try {
                        // We need a way to write YamlConfiguration to stream
                        // YamlConfiguration.save(File) is standard but not stream based.
                        // We can use saveToString() and write bytes.
                        String yamlData = cfg.saveToString();
                        fos.write(yamlData.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
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
