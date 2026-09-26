package dev.nuccleus.generators;

import dev.nuccleus.generators.BlockGeneratorManager.BlockGen;
import dev.nuccleus.generators.DropGeneratorManager.DropGen;
import java.util.List;
import java.util.Locale;
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

    public GeneratorCommand(GeneratorsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        if (command.getName().equalsIgnoreCase("generatorwand")) {
            giveWand(s);
            return true;
        }
        // Plain "/generator" turns the wand selection into the next numbered generator.
        if (a.length == 0) {
            Player p = player(s);
            if (p == null) return true;
            if (!plugin.selection().complete(p.getUniqueId())) {
                help(s);
                return true;
            }
            capture(p, plugin.blocks().nextId(), defaultRegen());
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "wand" -> giveWand(s);
            case "create" -> {
                // "/generator create <id> [seconds]" (no block) = use the wand selection as built.
                if (a.length == 2 || (a.length == 3 && a[2].matches("\\d+"))) {
                    Player p = player(s);
                    if (p == null) return true;
                    if (!plugin.selection().complete(p.getUniqueId())) {
                        plugin.msg(s, "<red>Select an area with /generatorwand first, or give a block: /generator create <id> <block>");
                        return true;
                    }
                    capture(p, a[1], a.length == 3 ? Integer.parseInt(a[2]) : defaultRegen());
                } else {
                    createBlock(s, a, false);
                }
            }
            case "area" -> createBlock(s, a, true);
            case "pos1", "pos2" -> {
                Player p = player(s);
                if (p == null) return true;
                Block target = p.getTargetBlockExact(8);
                if (target == null) {
                    plugin.msg(s, "<red>Look at a block first.");
                    return true;
                }
                plugin.selection().set(p.getUniqueId(), sub.equals("pos1"), target);
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
                    plugin.msg(s, "<red>Usage: /generator setregen <id> <seconds>  <gray>(0 = instant)");
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
                plugin.blocks().setAll(g, block);
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
            plugin.msg(s, "<red>Usage: /generator " + (area ? "area <id> <block> [regen-seconds]" : "create <id> <block> [size] [regen-seconds]")
                    + "  <gray>(size like 3x3, 5x5 or 5x3x5 = width x depth-down x length)");
            return;
        }
        Material block = block(s, a[2]);
        if (block == null) return;

        // Optional extras in any order: a size like 3x3 / 5x2x5, and a regen time in seconds.
        int[] dims = {1, 1, 1};
        Integer regen = defaultRegen();
        for (int i = 3; i < a.length; i++) {
            if (a[i].toLowerCase(Locale.ROOT).contains("x")) {
                if (area) {
                    plugin.msg(s, "<red>Area generators use pos1/pos2 for their size; use /generator create for a size.");
                    return;
                }
                dims = size(s, a[i]);
                if (dims == null) return;
            } else {
                regen = integer(s, a[i]);
                if (regen == null) return;
            }
        }

        Block from;
        Block to;
        if (area) {
            from = plugin.selection().pos1(p.getUniqueId());
            to = plugin.selection().pos2(p.getUniqueId());
            if (!plugin.selection().complete(p.getUniqueId())) {
                plugin.msg(s, "<red>Select two corners with /generatorwand (or /generator pos1 and pos2) first.");
                return;
            }
        } else {
            Block target = p.getTargetBlockExact(8);
            if (target == null) {
                plugin.msg(s, "<red>Look at the block you want to turn into a generator.");
                return;
            }
            // Centred on the block you look at; its top layer is that block and it extends downwards.
            int w = dims[0];
            int h = dims[1];
            int l = dims[2];
            from = target.getRelative(-(w - 1) / 2, -(h - 1), -(l - 1) / 2);
            to = target.getRelative(w / 2, 0, l / 2);
        }
        long size = (long) (Math.abs(from.getX() - to.getX()) + 1) * (Math.abs(from.getY() - to.getY()) + 1) * (Math.abs(from.getZ() - to.getZ()) + 1);
        if (size > BlockGeneratorManager.MAX_BLOCKS) {
            plugin.msg(s, "<red>That's " + size + " blocks; the limit is " + BlockGeneratorManager.MAX_BLOCKS + ".");
            return;
        }
        if (from.getY() < from.getWorld().getMinHeight()) {
            plugin.msg(s, "<red>That goes below the bottom of the world.");
            return;
        }
        BlockGen g = plugin.blocks().create(a[1], from, to, block, regen);
        String shape = (g.maxX - g.minX + 1) + "x" + (g.maxY - g.minY + 1) + "x" + (g.maxZ - g.minZ + 1);
        plugin.msg(s, "<green>Generator <white>" + g.id + "</white>: " + shape + " (" + g.size() + " blocks) of "
                + name(block) + ", " + regenText(g) + ". Mined items go straight to the inventory.");
    }

    /** Turns the player's wand selection into a generator, keeping every block exactly as built. */
    private void capture(Player p, String id, int regen) {
        UUID uuid = p.getUniqueId();
        long volume = plugin.selection().volume(uuid);
        if (volume > BlockGeneratorManager.MAX_BLOCKS) {
            plugin.msg(p, "<red>That selection is " + volume + " blocks; the limit is " + BlockGeneratorManager.MAX_BLOCKS + ".");
            return;
        }
        BlockGen g = plugin.blocks().capture(id, plugin.selection().pos1(uuid), plugin.selection().pos2(uuid), regen);
        if (g == null) {
            plugin.msg(p, "<red>There are no blocks in that selection. Place your ores first.");
            return;
        }
        plugin.msg(p, "<green>Generator <white>#" + g.id + "</white> made from " + g.size() + " blocks: <gray>" + g.contents()
                + "<green>. " + capitalize(regenText(g)) + ". Anyone can mine it; items go straight to their inventory.");
    }

    private void giveWand(CommandSender s) {
        Player p = player(s);
        if (p == null) return;
        p.getInventory().addItem(plugin.wand().createWand());
        plugin.msg(p, "<green>Left-click a block for corner 1, right-click for corner 2, then run <yellow>/generator</yellow>.");
    }

    private int defaultRegen() {
        return plugin.getConfig().getInt("block-generators.default-regen-seconds", 0);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** Parses "3x3" (3 wide, 1 deep, 3 long) or "5x2x5" (width x depth-down x length). */
    private int[] size(CommandSender s, String raw) {
        String[] parts = raw.toLowerCase(Locale.ROOT).split("x");
        try {
            int[] dims;
            if (parts.length == 2) {
                dims = new int[] {Integer.parseInt(parts[0]), 1, Integer.parseInt(parts[1])};
            } else if (parts.length == 3) {
                dims = new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
            } else {
                throw new NumberFormatException();
            }
            for (int d : dims) {
                if (d < 1 || d > 100) {
                    plugin.msg(s, "<red>Each side must be between 1 and 100.");
                    return null;
                }
            }
            return dims;
        } catch (NumberFormatException e) {
            plugin.msg(s, "<red>Size should look like 3x3, 5x5 or 5x3x5 (width x depth x length).");
            return null;
        }
    }

    private static String regenText(BlockGen g) {
        return g.regenSeconds == 0 ? "blocks come back instantly" : "blocks come back after " + g.regenSeconds + "s";
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
            s.sendMessage(MiniMessage.miniMessage().deserialize("<white>" + g.id + " <gray>" + g.contents()
                    + ", " + regenText(g)
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
                "<gray>Build your ores, then:",
                "/generatorwand <gray>- left-click corner 1, right-click corner 2",
                "/generator <gray>- turn the selected blocks into generator #1, #2, #3...",
                "/generator create <name> <gray>- same, with a name instead of a number",
                "<gray>Or place blocks for you:",
                "<gray>Mine forever; items go straight to your inventory, even in protected areas:",
                "/generator create <id> <block> [size] [regen-seconds] <gray>- on the block you look at",
                "<gray>  size: 1x1 (default), 3x3, 5x5, 5x3x5 (width x depth-down x length)",
                "/generator area <id> <block> [regen-seconds] <gray>- fill the selection with one block",
                "/generator setdrop <id> <item|natural> [amount]",
                "/generator setregen <id> <seconds> <gray>- optional delay (0 = instant, the default)",
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
            case 1 -> List.of("wand", "create", "area", "pos1", "pos2", "setdrop", "setregen", "setblock", "reset", "remove", "drop", "list", "reload");
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
            case 4 -> first.equals("create") ? List.of("1x1", "2x2", "3x3", "5x5", "7x7", "3x3x3", "5x3x5")
                    : (first.equals("drop") || first.equals("dropper")) && a[1].equalsIgnoreCase("create") ? materials(false) : List.of();
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
