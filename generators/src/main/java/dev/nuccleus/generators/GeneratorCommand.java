package dev.nuccleus.generators;

import dev.nuccleus.generators.BlockGeneratorManager.BlockGen;
import dev.nuccleus.generators.DropGeneratorManager.DropGen;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /generator: admin tool for infinite generators. */
public final class GeneratorCommand implements TabExecutor {

    private final GeneratorsPlugin plugin;
    private final Map<UUID, Block> pos1 = new HashMap<>();
    private final Map<UUID, Block> pos2 = new HashMap<>();

    public GeneratorCommand(GeneratorsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "create" -> createBlock(s, a, false);
            case "area" -> createBlock(s, a, true);
            case "pos1", "pos2" -> {
                Player p = player(s);
                if (p == null) return true;
                Block target = p.getTargetBlockExact(8);
                if (target == null) {
                    plugin.msg(s, "<red>Look at a block first.");
                    return true;
                }
                (sub.equals("pos1") ? pos1 : pos2).put(p.getUniqueId(), target);
                plugin.msg(s, "<green>" + sub + " set to " + target.getX() + ", " + target.getY() + ", " + target.getZ() + ".");
            }
            case "setdrop" -> {
                if (a.length < 3) {
                    plugin.msg(s, "<red>Usage: /generator setdrop <id> <item|natural> [amount]");
                    return true;
                }
                BlockGen g = findBlock(s, a[1]);
                if (g == null) return true;
                if (a[2].equalsIgnoreCase("natural")) {
                    g.drop = null;
                } else {
                    Material item = item(s, a[2]);
                    Integer amount = a.length > 3 ? integer(s, a[3]) : Integer.valueOf(1);
                    if (item == null || amount == null) return true;
                    g.drop = item;
                    g.dropAmount = Math.max(1, amount);
                }
                plugin.blocks().update(g);
                plugin.msg(s, "<green>Drop for " + g.id + " is now "
                        + (g.drop == null ? "the block's normal drop" : g.dropAmount + "x " + name(g.drop)) + ".");
            }
            case "setregen" -> {
                if (a.length < 3) {
                    plugin.msg(s, "<red>Usage: /generator setregen <id> <seconds>  <gray>(0 = never breaks)");
                    return true;
                }
                BlockGen g = findBlock(s, a[1]);
                Integer seconds = integer(s, a[2]);
                if (g == null || seconds == null) return true;
                g.regenSeconds = Math.max(0, seconds);
                plugin.blocks().update(g);
                plugin.msg(s, "<green>" + g.id + " now " + regenText(g) + ".");
            }
            case "setblock" -> {
                if (a.length < 3) {
                    plugin.msg(s, "<red>Usage: /generator setblock <id> <block>");
                    return true;
                }
                BlockGen g = findBlock(s, a[1]);
                Material block = block(s, a[2]);
                if (g == null || block == null) return true;
                g.block = block;
                plugin.blocks().update(g);
                plugin.blocks().fill(g);
                plugin.msg(s, "<green>" + g.id + " is now " + name(block) + ".");
            }
            case "reset" -> {
                if (a.length < 2) {
                    plugin.msg(s, "<red>Usage: /generator reset <id>");
                    return true;
                }
                BlockGen g = findBlock(s, a[1]);
                if (g == null) return true;
                plugin.blocks().fill(g);
                plugin.msg(s, "<green>Refilled " + g.id + ".");
            }
            case "remove", "delete" -> {
                if (a.length < 2) {
                    plugin.msg(s, "<red>Usage: /generator remove <id>");
                    return true;
                }
                plugin.msg(s, plugin.blocks().remove(a[1])
                        ? "<green>Removed. The blocks stay, they're just normal blocks now."
                        : "<red>No generator called " + GeneratorsPlugin.esc(a[1]));
            }
            case "drop", "dropper" -> dropper(s, a);
            case "list" -> list(s);
            case "reload" -> {
                plugin.reloadConfig();
                plugin.msg(s, "<green>Config reloaded.");
            }
            default -> help(s);
        }
        return true;
    }

    // ------------------------------------------------------------------ block generators

    private void createBlock(CommandSender s, String[] a, boolean area) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 3) {
            plugin.msg(s, "<red>Usage: /generator " + (area ? "area" : "create") + " <id> <block> [regen-seconds]");
            return;
        }
        Material block = block(s, a[2]);
        if (block == null) return;
        Integer regen = a.length > 3 ? integer(s, a[3]) : Integer.valueOf(plugin.getConfig().getInt("block-generators.default-regen-seconds", 0));
        if (regen == null) return;

        Block from;
        Block to;
        if (area) {
            from = pos1.get(p.getUniqueId());
            to = pos2.get(p.getUniqueId());
            if (from == null || to == null || from.getWorld() != to.getWorld()) {
                plugin.msg(s, "<red>Look at a corner and run /generator pos1, then the other corner and /generator pos2.");
                return;
            }
            long size = (long) (Math.abs(from.getX() - to.getX()) + 1) * (Math.abs(from.getY() - to.getY()) + 1) * (Math.abs(from.getZ() - to.getZ()) + 1);
            if (size > BlockGeneratorManager.MAX_BLOCKS) {
                plugin.msg(s, "<red>That's " + size + " blocks; the limit is " + BlockGeneratorManager.MAX_BLOCKS + ".");
                return;
            }
        } else {
            from = p.getTargetBlockExact(8);
            if (from == null) {
                plugin.msg(s, "<red>Look at the block you want to turn into a generator.");
                return;
            }
            to = from;
        }
        BlockGen g = plugin.blocks().create(a[1], from, to, block, regen);
        plugin.msg(s, "<green>Generator <white>" + g.id + "</white>: " + g.size() + " block(s) of "
                + name(block) + ", " + regenText(g) + ". Mined items go straight to the inventory.");
    }

    private static String regenText(BlockGen g) {
        return g.regenSeconds == 0 ? "never runs out" : "regenerates after " + g.regenSeconds + "s";
    }

    // ------------------------------------------------------------------ drop generators

    private void dropper(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "create" -> {
                Player p = player(s);
                if (p == null) return;
                if (a.length < 4) {
                    plugin.msg(s, "<red>Usage: /generator drop create <id> <item> [amount] [seconds]");
                    return;
                }
                Material item = item(s, a[3]);
                Integer amount = a.length > 4 ? integer(s, a[4]) : Integer.valueOf(1);
                Integer seconds = a.length > 5 ? integer(s, a[5]) : Integer.valueOf(10);
                if (item == null || amount == null || seconds == null) return;
                Block target = p.getTargetBlockExact(6);
                Location base = target != null && !target.getType().isAir()
                        ? target.getLocation()
                        : p.getLocation().getBlock().getRelative(BlockFace.DOWN).getLocation();
                DropGen g = plugin.drops().create(a[2], base, item, amount, seconds);
                plugin.msg(s, "<green>Drop generator <white>" + g.id + "</white> drops " + g.amount + "x " + name(item)
                        + " every " + g.intervalSeconds + "s on top of " + LocUtil.pretty(g.base) + ".");
            }
            case "remove", "delete" -> {
                if (a.length < 3) {
                    plugin.msg(s, "<red>Usage: /generator drop remove <id>");
                    return;
                }
                plugin.msg(s, plugin.drops().remove(a[2]) ? "<green>Removed." : "<red>No drop generator called " + GeneratorsPlugin.esc(a[2]));
            }
            default -> list(s);
        }
    }

    // ------------------------------------------------------------------ shared

    private void list(CommandSender s) {
        plugin.msg(s, "<gold>Block generators (" + plugin.blocks().all().size() + "):");
        for (BlockGen g : plugin.blocks().all()) {
            s.sendMessage(MiniMessage.miniMessage().deserialize("<white>" + g.id + " <gray>" + name(g.block)
                    + " x" + g.size() + ", " + regenText(g)
                    + (g.drop != null ? ", drops " + g.dropAmount + "x " + name(g.drop) : "")
                    + " @ " + g.world + " " + g.minX + "," + g.minY + "," + g.minZ));
        }
        plugin.msg(s, "<gold>Drop generators (" + plugin.drops().all().size() + "):");
        for (DropGen g : plugin.drops().all()) {
            s.sendMessage(MiniMessage.miniMessage().deserialize("<white>" + g.id + " <gray>" + g.amount + "x " + name(g.item)
                    + " / " + g.intervalSeconds + "s @ " + g.base.getWorld().getName() + " " + LocUtil.pretty(g.base)));
        }
    }

    private void help(CommandSender s) {
        plugin.msg(s, "<gold><bold>Generators");
        String[] lines = {
                "<gray>Mine forever; items go straight to your inventory, even in protected areas:",
                "/generator create <id> <block> [regen-seconds] <gray>- the block you're looking at",
                "/generator pos1 | pos2 <gray>- look at two corners, then:",
                "/generator area <id> <block> [regen-seconds] <gray>- a whole mine",
                "/generator setdrop <id> <item|natural> [amount]",
                "/generator setregen <id> <seconds> <gray>- 0 = block never breaks",
                "/generator setblock <id> <block> | reset <id> | remove <id>",
                "<gray>Drop items on a timer:",
                "/generator drop create <id> <item> [amount] [seconds] <gray>- on the block you look at",
                "/generator drop remove <id>",
                "/generator list | reload",
        };
        for (String line : lines) s.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>" + line));
    }

    private BlockGen findBlock(CommandSender s, String id) {
        BlockGen g = plugin.blocks().get(id);
        if (g == null) plugin.msg(s, "<red>No generator called " + GeneratorsPlugin.esc(id));
        return g;
    }

    private Material block(CommandSender s, String raw) {
        Material m = Material.matchMaterial(raw);
        if (m == null || !m.isBlock() || m.isAir()) {
            plugin.msg(s, "<red>Not a block: " + GeneratorsPlugin.esc(raw));
            return null;
        }
        return m;
    }

    private Material item(CommandSender s, String raw) {
        Material m = Material.matchMaterial(raw);
        if (m == null || !m.isItem() || m.isAir()) {
            plugin.msg(s, "<red>Not an item: " + GeneratorsPlugin.esc(raw));
            return null;
        }
        return m;
    }

    private static String name(Material m) {
        return m.name().toLowerCase(Locale.ROOT);
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        plugin.msg(s, "<red>Only players can do that.");
        return null;
    }

    private Integer integer(CommandSender s, String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            plugin.msg(s, "<red>Not a number: " + GeneratorsPlugin.esc(raw));
            return null;
        }
    }

    // ------------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] a) {
        String first = a[0].toLowerCase(Locale.ROOT);
        List<String> options = switch (a.length) {
            case 1 -> List.of("create", "area", "pos1", "pos2", "setdrop", "setregen", "setblock", "reset", "remove", "drop", "list", "reload");
            case 2 -> switch (first) {
                case "setdrop", "setregen", "setblock", "reset", "remove", "delete" -> plugin.blocks().all().stream().map(g -> g.id).toList();
                case "drop", "dropper" -> List.of("create", "remove", "list");
                default -> List.of();
            };
            case 3 -> switch (first) {
                case "create", "area", "setblock" -> materials(true);
                case "setdrop" -> Stream.concat(Stream.of("natural"), materials(false).stream()).toList();
                case "drop", "dropper" -> a[1].equalsIgnoreCase("remove") ? plugin.drops().all().stream().map(g -> g.id).toList() : List.of();
                default -> List.of();
            };
            case 4 -> (first.equals("drop") || first.equals("dropper")) && a[1].equalsIgnoreCase("create") ? materials(false) : List.of();
            default -> List.of();
        };
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(prefix)).limit(50).toList();
    }

    private static List<String> materials(boolean blocks) {
        return Stream.of(Material.values())
                .filter(m -> !m.isLegacy() && !m.isAir() && (blocks ? m.isBlock() : m.isItem()))
                .map(GeneratorCommand::name).toList();
    }
}
