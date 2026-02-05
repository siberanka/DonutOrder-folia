/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.inventory.ItemStack
 */
package me.clanify.donutOrder.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import me.clanify.donutOrder.data.ItemKey;
import org.bukkit.inventory.ItemStack;

public class Order {
    public UUID id;
    public UUID owner;
    public ItemKey key;
    public int requested;
    public int delivered;
    public double priceEach;
    public double paid;
    public boolean canceled;
    public volatile boolean completed;
    public final List<ItemStack> storage = new ArrayList<ItemStack>();

    public int remainingAmount() {
        return Math.max(0, this.requested - this.delivered);
    }

    public double totalPrice() {
        return (double) this.requested * this.priceEach;
    }
}
