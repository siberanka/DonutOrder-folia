/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 */
package me.clanify.donutOrder.cmd;

import java.util.ArrayList;
import java.util.List;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.Utils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class DonutOrderCommand
        implements CommandExecutor,
        TabCompleter {
    private final DonutOrder pl;

    public DonutOrderCommand(DonutOrder pl) {
        this.pl = pl;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Utils.formatColors(pl.cfg().cfg().getString("messages.usage", "&cUsage: /orders")));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("donutorder.admin")) {
                sender.sendMessage(
                        Utils.formatColors(pl.cfg().cfg().getString("messages.no_permission", "&cNo permission.")));
                return true;
            }
            this.pl.cfg().reload();
            this.pl.filters().reload();
            if (this.pl.ench() != null) {
                this.pl.ench().reload();
            }
            sender.sendMessage(
                    Utils.formatColors(pl.cfg().cfg().getString("messages.reloaded", "&aDonutOrder reloaded.")));
            return true;
        }
        sender.sendMessage(
                Utils.formatColors(pl.cfg().cfg().getString("messages.unknown_command", "&cUnknown subcommand.")));
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        ArrayList<String> out = new ArrayList<String>();
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase())) {
            out.add("reload");
        }
        return out;
    }
}
