/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 */
package me.clanify.donutOrder.cmd;

import java.util.Collections;
import java.util.List;
import me.clanify.donutOrder.DonutOrder;
import me.clanify.donutOrder.Utils;
import me.clanify.donutOrder.gui.OrdersMainMenu;
import me.clanify.donutOrder.store.PlayerStateManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class OrdersCommand
implements CommandExecutor,
TabCompleter {
    private final DonutOrder plugin;

    public OrdersCommand(DonutOrder plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.cfg().msg("messages.players_only", "&cThis command can only be used by players."));
            return true;
        }
        Player p = (Player)sender;
        PlayerStateManager.View state = this.plugin.state().main(p.getUniqueId());
        if (args.length > 0) {
            String query = String.join((CharSequence)" ", args).trim();
            state.search = Utils.sanitizeSearch(query);
            state.page = 0;
        }
        new OrdersMainMenu(this.plugin, p).open();
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}

