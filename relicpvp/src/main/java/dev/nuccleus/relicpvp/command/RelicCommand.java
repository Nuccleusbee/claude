package dev.nuccleus.relicpvp.command;

import dev.nuccleus.relicpvp.RelicPvP;
import dev.nuccleus.relicpvp.generator.GeneratorManager.Generator;
import dev.nuccleus.relicpvp.util.LocUtil;
import dev.nuccleus.relicpvp.util.Text;
import dev.nuccleus.relicpvp.zone.ZoneManager.Zone;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RelicCommand implements TabExecutor {

    private final RelicPvP plugin;

    public RelicCommand(RelicPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadSettings();
                plugin.msg(sender, "<green>Config reloaded.");
            }
            case "gen", "generator" -> generator(sender, args);
            case "zone" -> zone(sender, args);
            case "boss" -> boss(sender, args);
            case "rare" -> rare(sender, args);
            case "give" -> give(sender, args);
            case "givekey" -> giveKey(sender, args);
            case "setlevel" -> setLevel(sender, args);
            case "kit" -> kit(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        plugin.msg(s, "<gold><bold>RelicPvP commands");
        String[] lines = {
                "/relic gen create <id> <item> [amount] [seconds] <gray>- generator on the block you look at",
                "/relic gen remove <id> | list",
                "/relic zone pos1 | pos2 <gray>- mark corners where you stand",
                "/relic zone create <id> <level> [pvp true/false] [build true/false]",
                "/relic zone setlevel <id> <level> | setpvp|setbuild <id> <true/false> | remove <id> | list",
                "/relic boss setspawn <id> | clearspawn <id> | spawn <id> | kill <id> | list",
                "/relic rare addpoint | removepoint | clearpoints | list | spawnnow",
                "/relic give <player> <item> [amount]",
                "/relic givekey <player> <tier>",
                "/relic setlevel <player> <level>",
                "/relic kit [player]",
                "/relic reload",
        };
        for (String line : lines) s.sendMessage(Text.mm("<yellow>" + line));
    }

    // ------------------------------------------------------------------ generators

    private void generator(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "create" -> {
                Player p = player(s);
                if (p == null) return;
                if (a.length < 4) {
                    plugin.msg(s, "<red>Usage: /relic gen create <id> <item> [amount] [seconds]");
                    return;
                }
                if (plugin.items().create(a[3], 1) == null) {
                    plugin.msg(s, "<red>Unknown item: " + Text.esc(a[3]));
                    return;
                }
                Integer amount = a.length > 4 ? integer(s, a[4]) : Integer.valueOf(1);
                Integer seconds = a.length > 5 ? integer(s, a[5]) : Integer.valueOf(10);
                if (amount == null || seconds == null) return;
                Block target = p.getTargetBlockExact(6);
                Location base = target != null && !target.getType().isAir()
                        ? target.getLocation()
                        : p.getLocation().getBlock().getRelative(BlockFace.DOWN).getLocation();
                Generator g = plugin.generators().create(a[2], base, a[3], amount, seconds);
                plugin.msg(s, "<green>Generator <white>" + g.id + "</white> drops " + amount + "x " + Text.esc(a[3])
                        + " every " + g.intervalSeconds + "s at " + LocUtil.pretty(g.base) + ".");
            }
            case "remove", "delete" -> {
                if (a.length < 3) {
                    plugin.msg(s, "<red>Usage: /relic gen remove <id>");
                    return;
                }
                plugin.msg(s, plugin.generators().remove(a[2]) ? "<green>Removed." : "<red>No generator called " + Text.esc(a[2]));
            }
            default -> {
                plugin.msg(s, "<gold>Generators (" + plugin.generators().all().size() + "):");
                for (Generator g : plugin.generators().all()) {
                    s.sendMessage(Text.mm("<white>" + g.id + " <gray>" + g.amount + "x " + Text.esc(g.item)
                            + " / " + g.intervalSeconds + "s @ " + g.base.getWorld().getName() + " " + LocUtil.pretty(g.base)));
                }
            }
        }
    }

    // ------------------------------------------------------------------ zones

    private void zone(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "pos1", "pos2" -> {
                Player p = player(s);
                if (p == null) return;
                Location l = p.getLocation().getBlock().getLocation();
                if (sub.equals("pos1")) plugin.zones().setPos1(p.getUniqueId(), l);
                else plugin.zones().setPos2(p.getUniqueId(), l);
                plugin.msg(s, "<green>" + sub + " set to " + LocUtil.pretty(l) + ".");
            }
            case "create" -> {
                Player p = player(s);
                if (p == null) return;
                if (a.length < 4) {
                    plugin.msg(s, "<red>Usage: /relic zone create <id> <level> [pvp true/false] [build true/false]");
                    return;
                }
                Location p1 = plugin.zones().pos1(p.getUniqueId());
                Location p2 = plugin.zones().pos2(p.getUniqueId());
                if (p1 == null || p2 == null || p1.getWorld() != p2.getWorld()) {
                    plugin.msg(s, "<red>Set /relic zone pos1 and pos2 first (same world).");
                    return;
                }
                Integer level = integer(s, a[3]);
                if (level == null) return;
                boolean pvp = a.length < 5 || Boolean.parseBoolean(a[4]);
                boolean build = a.length < 6 || Boolean.parseBoolean(a[5]);
                Zone z = plugin.zones().create(a[2], p1, p2, level, pvp, build);
                plugin.msg(s, "<green>Zone <white>" + z.id() + "</white> created: level " + z.level()
                        + ", PvP " + (z.pvp() ? "on" : "off") + ", building " + (z.build() ? "on" : "off") + ", " + (z.maxX() - z.minX() + 1) + "x" + (z.maxZ() - z.minZ() + 1) + " blocks.");
            }
            case "setlevel" -> {
                if (a.length < 4) {
                    plugin.msg(s, "<red>Usage: /relic zone setlevel <id> <level>");
                    return;
                }
                Integer level = integer(s, a[3]);
                if (level == null) return;
                plugin.msg(s, plugin.zones().update(a[2], level, null, null) != null ? "<green>Updated." : "<red>No such zone.");
            }
            case "setpvp" -> {
                if (a.length < 4) {
                    plugin.msg(s, "<red>Usage: /relic zone setpvp <id> <true/false>");
                    return;
                }
                plugin.msg(s, plugin.zones().update(a[2], null, Boolean.parseBoolean(a[3]), null) != null ? "<green>Updated." : "<red>No such zone.");
            }
            case "setbuild" -> {
                if (a.length < 4) {
                    plugin.msg(s, "<red>Usage: /relic zone setbuild <id> <true/false>");
                    return;
                }
                plugin.msg(s, plugin.zones().update(a[2], null, null, Boolean.parseBoolean(a[3])) != null ? "<green>Updated." : "<red>No such zone.");
            }
            case "remove", "delete" -> {
                if (a.length < 3) {
                    plugin.msg(s, "<red>Usage: /relic zone remove <id>");
                    return;
                }
                plugin.msg(s, plugin.zones().remove(a[2]) ? "<green>Removed." : "<red>No such zone.");
            }
            default -> {
                plugin.msg(s, "<gold>Zones (" + plugin.zones().all().size() + "):");
                for (Zone z : plugin.zones().all()) {
                    s.sendMessage(Text.mm("<white>" + z.id() + " <gray>level " + z.level() + ", PvP " + (z.pvp() ? "on" : "off") + ", build " + (z.build() ? "on" : "off")
                            + " @ " + z.world() + " " + z.minX() + "," + z.minZ() + " → " + z.maxX() + "," + z.maxZ()));
                }
            }
        }
    }

    // ------------------------------------------------------------------ bosses

    private void boss(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        if (sub.equals("list")) {
            plugin.msg(s, "<gold>Bosses:");
            plugin.bosses().describe().forEach(line -> s.sendMessage(Text.mm(line)));
            return;
        }
        if (a.length < 3 || !plugin.bosses().exists(a[2])) {
            plugin.msg(s, "<red>Usage: /relic boss " + sub + " <id>  <gray>(ids: " + String.join(", ", plugin.bosses().ids()) + ")");
            return;
        }
        String id = a[2];
        switch (sub) {
            case "setspawn" -> {
                Player p = player(s);
                if (p == null) return;
                plugin.bosses().setSpawn(id, p.getLocation());
                plugin.msg(s, "<green>Spawn for " + id + " set. It appears when a player comes within 64 blocks.");
            }
            case "clearspawn" -> {
                plugin.bosses().setSpawn(id, null);
                plugin.msg(s, "<green>Spawn cleared; " + id + " won't respawn on its own.");
            }
            case "spawn" -> {
                Player p = player(s);
                if (p == null) return;
                plugin.msg(s, plugin.bosses().spawn(id, p.getLocation()) != null ? "<green>Spawned." : "<red>It's already alive.");
            }
            case "kill" -> plugin.msg(s, plugin.bosses().kill(id) ? "<green>Removed (no drops)." : "<red>It isn't alive.");
            default -> plugin.msg(s, "<red>Unknown boss command.");
        }
    }

    // ------------------------------------------------------------------ rare spawns

    private void rare(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "addpoint" -> {
                Player p = player(s);
                if (p == null) return;
                plugin.rares().addPoint(p.getLocation());
                plugin.msg(s, "<green>Rare spawn point added (" + plugin.rares().points().size() + " total).");
            }
            case "removepoint" -> {
                Player p = player(s);
                if (p == null) return;
                plugin.msg(s, plugin.rares().removeNear(p.getLocation()) ? "<green>Removed the nearest point." : "<red>No point within 5 blocks.");
            }
            case "clearpoints" -> {
                plugin.rares().clearPoints();
                plugin.msg(s, "<green>All rare spawn points removed.");
            }
            case "spawnnow" -> {
                String error = plugin.rares().spawnRandom();
                plugin.msg(s, error == null ? "<green>Spawned a rare item." : "<red>Couldn't spawn: " + Text.esc(error));
            }
            default -> {
                plugin.msg(s, "<gold>Rare spawn points (" + plugin.rares().points().size() + "), "
                        + plugin.rares().activeCount() + " item(s) out now:");
                for (Location l : plugin.rares().points()) {
                    s.sendMessage(Text.mm("<gray>" + l.getWorld().getName() + " " + LocUtil.pretty(l)));
                }
            }
        }
    }

    // ------------------------------------------------------------------ players

    private void give(CommandSender s, String[] a) {
        if (a.length < 3) {
            plugin.msg(s, "<red>Usage: /relic give <player> <item> [amount]");
            return;
        }
        Player target = online(s, a[1]);
        if (target == null) return;
        Integer amount = a.length > 3 ? integer(s, a[3]) : Integer.valueOf(1);
        if (amount == null) return;
        List<ItemStack> stacks = plugin.items().createStacks(a[2], amount);
        if (stacks.isEmpty()) {
            plugin.msg(s, "<red>Unknown item: " + Text.esc(a[2]));
            return;
        }
        stacks.forEach(st -> plugin.items().give(target, st));
        plugin.msg(s, "<green>Gave " + amount + "x " + Text.esc(a[2]) + " to " + target.getName() + ".");
    }

    private void giveKey(CommandSender s, String[] a) {
        if (a.length < 3) {
            plugin.msg(s, "<red>Usage: /relic givekey <player> <tier>");
            return;
        }
        Player target = online(s, a[1]);
        Integer tier = integer(s, a[2]);
        if (target == null || tier == null || tier < 1) return;
        plugin.items().give(target, plugin.items().key(tier));
        plugin.msg(s, "<green>Gave a Level " + tier + " Key to " + target.getName() + ".");
    }

    private void setLevel(CommandSender s, String[] a) {
        if (a.length < 3) {
            plugin.msg(s, "<red>Usage: /relic setlevel <player> <level>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(a[1]);
        if (target == null) {
            plugin.msg(s, "<red>Never seen a player called " + Text.esc(a[1]));
            return;
        }
        Integer level = integer(s, a[2]);
        if (level == null) return;
        plugin.players().get(target.getUniqueId()).level = Math.max(0, level);
        plugin.players().save();
        plugin.msg(s, "<green>" + target.getName() + " is now level " + Math.max(0, level) + ".");
    }

    private void kit(CommandSender s, String[] a) {
        Player target = a.length > 1 ? online(s, a[1]) : player(s);
        if (target == null) return;
        plugin.kits().give(target);
        plugin.msg(s, "<green>Gave the starter kit to " + target.getName() + ".");
    }

    // ------------------------------------------------------------------ helpers

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        plugin.msg(s, "<red>Only players can do that.");
        return null;
    }

    private Player online(CommandSender s, String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) plugin.msg(s, "<red>" + Text.esc(name) + " isn't online.");
        return p;
    }

    private Integer integer(CommandSender s, String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            plugin.msg(s, "<red>Not a number: " + Text.esc(raw));
            return null;
        }
    }

    // ------------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] a) {
        List<String> options = switch (a.length) {
            case 1 -> List.of("gen", "zone", "boss", "rare", "give", "givekey", "setlevel", "kit", "reload");
            case 2 -> switch (a[0].toLowerCase(Locale.ROOT)) {
                case "gen", "generator" -> List.of("create", "remove", "list");
                case "zone" -> List.of("pos1", "pos2", "create", "setlevel", "setpvp", "setbuild", "remove", "list");
                case "boss" -> List.of("setspawn", "clearspawn", "spawn", "kill", "list");
                case "rare" -> List.of("addpoint", "removepoint", "clearpoints", "spawnnow", "list");
                case "give", "givekey", "setlevel", "kit" -> onlineNames();
                default -> List.of();
            };
            case 3 -> switch (a[0].toLowerCase(Locale.ROOT)) {
                case "boss" -> new ArrayList<>(plugin.bosses().ids());
                case "gen", "generator" -> a[1].equalsIgnoreCase("remove")
                        ? plugin.generators().all().stream().map(g -> g.id).toList() : List.of();
                case "zone" -> List.of("setlevel", "setpvp", "setbuild", "remove").contains(a[1].toLowerCase(Locale.ROOT))
                        ? plugin.zones().all().stream().map(Zone::id).toList() : List.of();
                case "give" -> itemNames();
                default -> List.of();
            };
            case 4 -> a[0].equalsIgnoreCase("gen") && a[1].equalsIgnoreCase("create") ? itemNames() : List.of();
            default -> List.of();
        };
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).limit(50).toList();
    }

    private List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private List<String> itemNames() {
        return Stream.concat(plugin.items().customIds().stream(),
                Stream.of(Material.values()).filter(m -> !m.isLegacy() && m.isItem()).map(m -> m.name().toLowerCase(Locale.ROOT))).toList();
    }
}
