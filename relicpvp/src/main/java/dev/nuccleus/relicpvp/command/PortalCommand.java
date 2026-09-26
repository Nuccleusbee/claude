package dev.nuccleus.relicpvp.command;

import dev.nuccleus.relicpvp.RelicPvP;
import dev.nuccleus.relicpvp.portal.PortalManager;
import dev.nuccleus.relicpvp.portal.PortalManager.Portal;
import dev.nuccleus.relicpvp.util.LocUtil;
import dev.nuccleus.relicpvp.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /portal: admin tool for walk-through portals. */
public final class PortalCommand implements TabExecutor {

    private final RelicPvP plugin;
    private final Map<UUID, Block> pos1 = new HashMap<>();
    private final Map<UUID, Block> pos2 = new HashMap<>();

    public PortalCommand(RelicPvP plugin) {
        this.plugin = plugin;
    }

    private PortalManager portals() {
        return plugin.portals();
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "pos1", "pos2" -> {
                Player p = player(s);
                if (p == null) return true;
                Block b = pick(p);
                (sub.equals("pos1") ? pos1 : pos2).put(p.getUniqueId(), b);
                plugin.msg(s, "<green>" + sub + " set to " + b.getX() + ", " + b.getY() + ", " + b.getZ() + ".");
            }
            case "create" -> {
                Player p = player(s);
                if (p == null) return true;
                if (a.length < 2) {
                    plugin.msg(s, "<red>Usage: /portal create <id> [fill: none|nether_portal|<block>]");
                    return true;
                }
                Block from = pos1.get(p.getUniqueId());
                Block to = pos2.get(p.getUniqueId());
                if (from == null || to == null || from.getWorld() != to.getWorld()) {
                    plugin.msg(s, "<red>Set /portal pos1 and /portal pos2 first (look at the inside of the frame's corners).");
                    return true;
                }
                long size = (long) (Math.abs(from.getX() - to.getX()) + 1) * (Math.abs(from.getY() - to.getY()) + 1) * (Math.abs(from.getZ() - to.getZ()) + 1);
                if (size > PortalManager.MAX_BLOCKS) {
                    plugin.msg(s, "<red>That's " + size + " blocks; portals are limited to " + PortalManager.MAX_BLOCKS + ".");
                    return true;
                }
                String fill = a.length > 2 ? a[2] : "nether_portal";
                if (!fill.equalsIgnoreCase("none")) {
                    Material m = Material.matchMaterial(fill);
                    if (m == null || !m.isBlock()) {
                        plugin.msg(s, "<red>Not a block: " + Text.esc(fill));
                        return true;
                    }
                }
                Portal portal = portals().create(a[1], from, to, fill);
                plugin.msg(s, "<green>Portal <white>" + portal.id + "</white> created (" + portal.size() + " blocks). Now: /portal setdest "
                        + portal.id + " [here|spawn|boss <id>]");
            }
            case "setdest" -> {
                if (a.length < 2) {
                    plugin.msg(s, "<red>Usage: /portal setdest <id> [here|spawn|boss <bossId>]");
                    return true;
                }
                Portal portal = find(s, a[1]);
                if (portal == null) return true;
                String mode = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "here";
                String dest;
                switch (mode) {
                    case "spawn" -> dest = "spawn";
                    case "boss" -> {
                        if (a.length < 4 || !plugin.bosses().exists(a[3])) {
                            plugin.msg(s, "<red>Usage: /portal setdest " + portal.id + " boss <id>  <gray>(ids: " + String.join(", ", plugin.bosses().ids()) + ")");
                            return true;
                        }
                        dest = "boss:" + a[3].toLowerCase(Locale.ROOT);
                        if (plugin.bosses().spawnOf(a[3]) == null) {
                            plugin.msg(s, "<yellow>Heads up: that boss has no spawn yet. Set it with /relic boss setspawn " + a[3]);
                        }
                    }
                    default -> {
                        Player p = player(s);
                        if (p == null) return true;
                        dest = LocUtil.serialize(p.getLocation());
                    }
                }
                portals().setDestination(portal, dest);
                plugin.msg(s, "<green>Portal " + portal.id + " now goes to " + portals().describe(portal) + ".");
            }
            case "remove", "delete" -> {
                if (a.length < 2) {
                    plugin.msg(s, "<red>Usage: /portal remove <id>");
                    return true;
                }
                plugin.msg(s, portals().remove(a[1]) ? "<green>Removed." : "<red>No portal called " + Text.esc(a[1]));
            }
            case "tp" -> {
                Player p = player(s);
                if (p == null || a.length < 2) return true;
                Portal portal = find(s, a[1]);
                if (portal == null) return true;
                Location dest = portals().resolve(portal);
                if (dest == null) plugin.msg(s, "<red>That portal has no destination.");
                else p.teleport(dest);
            }
            case "list" -> {
                plugin.msg(s, "<gold>Portals (" + portals().all().size() + "):");
                for (Portal portal : portals().all()) {
                    s.sendMessage(Text.mm("<white>" + portal.id + " <gray>→ " + Text.esc(portals().describe(portal))
                            + " @ " + portal.world + " " + portal.minX + "," + portal.minY + "," + portal.minZ));
                }
            }
            default -> help(s);
        }
        return true;
    }

    /** The air block in front of the face you're looking at, or the block you're standing in. */
    private static Block pick(Player p) {
        Block target = p.getTargetBlockExact(8);
        BlockFace face = p.getTargetBlockFace(8);
        if (target != null && face != null) return target.getRelative(face);
        return p.getLocation().getBlock();
    }

    private void help(CommandSender s) {
        plugin.msg(s, "<gold><bold>Portals");
        String[] lines = {
                "/portal pos1 | pos2 <gray>- look at the inside face of two opposite corners of the frame",
                "/portal create <id> [none|nether_portal|<block>] <gray>- default: nether_portal look",
                "/portal setdest <id> <gray>- sends people to where you're standing",
                "/portal setdest <id> spawn | boss <bossId>",
                "/portal tp <id> | remove <id> | list",
        };
        for (String line : lines) s.sendMessage(Text.mm("<yellow>" + line));
    }

    private Portal find(CommandSender s, String id) {
        Portal p = portals().get(id);
        if (p == null) plugin.msg(s, "<red>No portal called " + Text.esc(id));
        return p;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        plugin.msg(s, "<red>Only players can do that.");
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] a) {
        List<String> options = switch (a.length) {
            case 1 -> List.of("pos1", "pos2", "create", "setdest", "tp", "remove", "list");
            case 2 -> List.of("setdest", "tp", "remove", "delete").contains(a[0].toLowerCase(Locale.ROOT))
                    ? portals().all().stream().map(p -> p.id).toList() : List.of();
            case 3 -> switch (a[0].toLowerCase(Locale.ROOT)) {
                case "setdest" -> List.of("here", "spawn", "boss");
                case "create" -> List.of("nether_portal", "none", "end_gateway", "light_blue_stained_glass_pane");
                default -> List.of();
            };
            case 4 -> a[0].equalsIgnoreCase("setdest") && a[2].equalsIgnoreCase("boss") ? new ArrayList<>(plugin.bosses().ids()) : List.of();
            default -> List.of();
        };
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(prefix)).toList();
    }
}
