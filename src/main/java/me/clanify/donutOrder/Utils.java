/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.block.Block
 *  org.bukkit.block.Sign
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.SignChangeEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.bukkit.util.Vector
 */
package me.clanify.donutOrder;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import me.clanify.donutOrder.util.TaskUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class Utils {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    public static final DecimalFormat ONE_DECIMAL = new DecimalFormat("#.#");

    private Utils() {
    }

    public static String formatColors(String input) {
        if (input == null) {
            return null;
        }
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer(input.length() + 32);
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder repl = new StringBuilder("\u00a7x");
            for (char c : hex.toCharArray()) {
                repl.append('\u00a7').append(c);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(repl.toString()));
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes((char) '&', (String) buffer.toString());
    }

    public static List<String> formatColors(List<String> lines) {
        return lines.stream().map(Utils::formatColors).collect(Collectors.toList());
    }

    /**
     * Sanitizes search input to prevent exploits and ensure reasonable length.
     * Strips all colors and limits to 32 characters.
     */
    public static String sanitizeSearch(String input) {
        if (input == null)
            return null;
        String s = ChatColor.stripColor(input).trim();
        if (s.length() > 32) {
            s = s.substring(0, 32);
        }
        return s.isEmpty() ? null : s;
    }

    public static String applyPlaceholders(String s, Map<String, String> ph) {
        if (s == null) {
            return null;
        }
        String out = s;
        for (Map.Entry<String, String> e : ph.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return Utils.formatColors(out);
    }

    public static List<String> applyPlaceholders(List<String> list, Map<String, String> ph) {
        return list.stream().map(s -> Utils.applyPlaceholders(s, ph)).collect(Collectors.toList());
    }

    public static String abbr(double v) {
        int i;
        boolean neg = v < 0.0;
        double n = Math.abs(v);
        String[] u = new String[] { "", "K", "M", "B", "T" };
        for (i = 0; n >= 1000.0 && i < u.length - 1; n /= 1000.0, ++i) {
        }
        String s = ONE_DECIMAL.format(n);
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        return (neg ? "-" : "") + s + u[i];
    }

    public static final class SignInputUtil {
        private static final Map<UUID, Session> sessions = new ConcurrentHashMap<UUID, Session>();

        public static void open(JavaPlugin plugin, Player player, List<String> lines, int inputLine,
                boolean hideFromOthers, ResultHandler handler) {
            List l;
            SignInputUtil.cleanupAndRemove(player, plugin, true);
            Location loc = SignInputUtil.findNearbyAir(player);
            if (loc == null) {
                player.sendMessage(SignInputUtil.signMsg(plugin, "messages.sign_input_no_space",
                        "&#ff4444No space to open sign input."));
                return;
            }
            Block block = loc.getBlock();
            BlockData original = block.getBlockData();
            int idx = Math.max(1, Math.min(4, inputLine)) - 1;
            List<String> list = l = lines == null ? new ArrayList<String>() : new ArrayList<String>(lines);
            while (l.size() < 4) {
                l.add("");
            }
            if (l.size() > 4) {
                l = l.subList(0, 4);
            }
            String[] plain = new String[4];
            for (int i = 0; i < 4; ++i) {
                String colored = Utils.formatColors((String) l.get(i));
                String stripped = ChatColor.stripColor((String) colored);
                plain[i] = stripped == null ? "" : stripped;
            }
            Session session = new Session(player.getUniqueId(), loc.clone(), original, idx, handler);
            sessions.put(player.getUniqueId(), session);
            Location finalLoc = loc.clone();
            String[] finalLines = plain;
            TaskUtil.runAtLocation((Plugin) plugin, finalLoc, () -> {
                Block b = finalLoc.getBlock();
                b.setType(Material.OAK_SIGN, false);
                if (!(b.getState() instanceof Sign)) {
                    SignInputUtil.cleanupAndRemove(player, plugin, true);
                    player.sendMessage(SignInputUtil.signMsg(plugin, "messages.sign_input_create_failed",
                            "&#ff4444Failed to create sign."));
                    return;
                }
                Sign sign = (Sign) b.getState();
                SignInputUtil.applyLines(sign, finalLines);
                SignInputUtil.setEditable(sign, player.getUniqueId());
                sign.update(true, false);
                TaskUtil.runEntity((Plugin) plugin, (Entity) player,
                        () -> player.sendBlockChange(finalLoc, sign.getBlockData()));
                if (hideFromOthers) {
                    SignInputUtil.startHideTask(plugin, player, session);
                }
                TaskUtil.runEntityLater((Plugin) plugin, (Entity) player, () -> {
                    Block now = finalLoc.getBlock();
                    if (!(now.getState() instanceof Sign)) {
                        SignInputUtil.cleanupAndRemove(player, plugin, true);
                        player.sendMessage(SignInputUtil.signMsg(plugin, "messages.sign_input_disappeared",
                                "&#ff4444Sign disappeared."));
                        return;
                    }
                    try {
                        player.openSign((Sign) now.getState());
                    } catch (Throwable t) {
                        SignInputUtil.cleanupAndRemove(player, plugin, true);
                        player.sendMessage(SignInputUtil.signMsg(plugin, "messages.sign_input_open_failed",
                                "&#ff4444Failed to open sign input."));
                    }
                }, 1L);
            });
        }

        private static String signMsg(JavaPlugin plugin, String path, String def) {
            if (plugin instanceof DonutOrder donut) {
                return donut.cfg().msg(path, def);
            }
            return Utils.formatColors(def);
        }

        public static void openFromConfig(JavaPlugin plugin, Player player, ConfigurationSection section,
                boolean hideFromOthers, ResultHandler handler) {
            List lines = section == null ? Collections.emptyList() : section.getStringList("lines");
            int inputLine = section == null ? 2 : section.getInt("input-line", 2);
            SignInputUtil.open(plugin, player, lines, inputLine, hideFromOthers, handler);
        }

        public static void forceClose(JavaPlugin plugin, Player player) {
            SignInputUtil.cleanupAndRemove(player, plugin, true);
        }

        private static void startHideTask(JavaPlugin plugin, Player owner, Session session) {
            SignInputUtil.sendOriginalToOthers(plugin, owner, session.loc, session.originalData);
            session.hideTask = TaskUtil.runGlobalTimer((Plugin) plugin, () -> {
                if (!owner.isOnline()) {
                    return;
                }
                SignInputUtil.sendOriginalToOthers(plugin, owner, session.loc, session.originalData);
            }, 1L, 1L);
            TaskUtil.runGlobalLater((Plugin) plugin, () -> {
                if (session.hideTask != null) {
                    session.hideTask.cancel();
                    session.hideTask = null;
                }
            }, 10L);
        }

        private static void sendOriginalToOthers(JavaPlugin plugin, Player owner, Location loc,
                BlockData originalData) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals((Object) owner) || !other.getWorld().equals((Object) loc.getWorld())
                        || other.getLocation().distanceSquared(loc) > 9216.0)
                    continue;
                TaskUtil.runEntity((Plugin) plugin, (Entity) other, () -> other.sendBlockChange(loc, originalData));
            }
        }

        private static Session cleanupAndRemove(Player player, JavaPlugin plugin, boolean restoreBlock) {
            Session s = sessions.remove(player.getUniqueId());
            if (s == null) {
                return null;
            }
            if (s.hideTask != null) {
                s.hideTask.cancel();
                s.hideTask = null;
            }
            if (restoreBlock) {
                TaskUtil.runAtLocation((Plugin) plugin, s.loc, () -> {
                    Block b = s.loc.getBlock();
                    b.setBlockData(s.originalData, false);
                    TaskUtil.runEntity((Plugin) plugin, (Entity) player,
                            () -> player.sendBlockChange(s.loc, s.originalData));
                });
            }
            return s;
        }

        private static boolean sameBlock(Location a, Location b) {
            if (a == null || b == null) {
                return false;
            }
            if (a.getWorld() == null || b.getWorld() == null) {
                return false;
            }
            if (!a.getWorld().equals((Object) b.getWorld())) {
                return false;
            }
            return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
        }

        private static Location findNearbyAir(Player p) {
            Location base = p.getLocation();
            ArrayList<Location> candidates = new ArrayList<Location>();
            candidates.add(base.clone().add(0.0, 2.0, 0.0));
            Vector dir = base.getDirection();
            dir.setY(0);
            if (dir.lengthSquared() > 1.0E-4) {
                dir.normalize();
                candidates.add(base.clone().add(dir).add(0.0, 1.0, 0.0));
                candidates.add(base.clone().add(dir.multiply(2)).add(0.0, 1.0, 0.0));
            }
            candidates.add(base.clone().add(0.0, 1.0, 0.0));
            for (Location cand : candidates) {
                Block b = cand.getBlock();
                Material t = b.getType();
                if (t != Material.AIR && t != Material.CAVE_AIR && t != Material.VOID_AIR)
                    continue;
                return b.getLocation();
            }
            return null;
        }

        private static void applyLines(Sign sign, String[] lines) {
            try {
                Class<?> sideClass = Class.forName("org.bukkit.block.sign.Side");
                Method getSide = sign.getClass().getMethod("getSide", sideClass);
                Object[] enums = sideClass.getEnumConstants();
                Object front = enums != null && enums.length > 0 ? enums[0] : null;
                Object signSide = getSide.invoke((Object) sign, front);
                Method setLine = signSide.getClass().getMethod("setLine", Integer.TYPE, String.class);
                for (int i = 0; i < 4; ++i) {
                    setLine.invoke(signSide, i, lines[i]);
                }
                return;
            } catch (Throwable sideClass) {
                for (int i = 0; i < 4; ++i) {
                    try {
                        sign.setLine(i, lines[i]);
                        continue;
                    } catch (Throwable throwable) {
                        // empty catch block
                    }
                }
                return;
            }
        }

        private static void setEditable(Sign sign, UUID uuid) {
            try {
                Method wax = sign.getClass().getMethod("setWaxed", Boolean.TYPE);
                wax.invoke((Object) sign, false);
            } catch (Throwable wax) {
                // empty catch block
            }
            try {
                Method editable = sign.getClass().getMethod("setEditable", Boolean.TYPE);
                editable.invoke((Object) sign, true);
            } catch (Throwable editable) {
                // empty catch block
            }
            try {
                Method allowed = sign.getClass().getMethod("setAllowedEditorUniqueId", UUID.class);
                allowed.invoke((Object) sign, uuid);
            } catch (Throwable throwable) {
                // empty catch block
            }
        }

        private static final class Session {
            final UUID uuid;
            final Location loc;
            final BlockData originalData;
            final int inputLineIndex;
            final ResultHandler handler;
            TaskUtil.Handle hideTask;

            Session(UUID uuid, Location loc, BlockData originalData, int inputLineIndex, ResultHandler handler) {
                this.uuid = uuid;
                this.loc = loc;
                this.originalData = originalData;
                this.inputLineIndex = inputLineIndex;
                this.handler = handler;
            }
        }

        @FunctionalInterface
        public static interface ResultHandler {
            public void onResult(Player var1, String var2);
        }

        public static final class SignListener
                implements Listener {
            private final JavaPlugin plugin;

            public SignListener(JavaPlugin plugin) {
                this.plugin = plugin;
            }

            @EventHandler
            public void onSignChange(SignChangeEvent event) {
                Player player = event.getPlayer();
                UUID uuid = player.getUniqueId();
                Session current = sessions.get(uuid);
                if (current == null) {
                    return;
                }
                if (!SignInputUtil.sameBlock(event.getBlock().getLocation(), current.loc)) {
                    return;
                }
                Session s = SignInputUtil.cleanupAndRemove(player, this.plugin, true);
                if (s == null) {
                    return;
                }
                String raw = "";
                try {
                    raw = event.getLine(s.inputLineIndex);
                } catch (Throwable throwable) {
                    // empty catch block
                }
                if (raw == null) {
                    raw = "";
                }
                String input = (raw = ChatColor.stripColor((String) raw).trim()).equals("-") ? "" : raw;
                TaskUtil.runEntity((Plugin) this.plugin, (Entity) player, () -> {
                    try {
                        if (s.handler != null) {
                            s.handler.onResult(player, input);
                        }
                    } catch (Throwable t) {
                        this.plugin.getLogger().warning("SignInputUtil handler error: " + t.getMessage());
                    }
                });
            }

            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                SignInputUtil.cleanupAndRemove(event.getPlayer(), this.plugin, true);
            }
        }
    }
}
