package dev.nuccleus.relicpvp.command;

import dev.nuccleus.relicpvp.RelicPvP;
import dev.nuccleus.relicpvp.mine.BlockGeneratorManager;
import dev.nuccleus.relicpvp.mine.BlockGeneratorManager.BlockGen;
import dev.nuccleus.relicpvp.util.Text;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /generator: admin tool for infinite mineable block generators. */
public final class GeneratorCommand implements TabExecutor {

    private final RelicPvP plugin;
    private final Map<UUID, Block> pos1 = new HashMap<>();
    private final Map<UUID, Block> pos2 = new HashMap<>();

    public GeneratorCommand(RelicPvP plugin) {
        this.plugin = plugin;
    }

    private BlockGeneratorManager gens() {
        return plugin.blockGenerators();
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "create" -> create(s, a, false);
            case "area" -> create(s, a, true);
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
                BlockGen g = find(s, a[1]);
                if (g == null) return true;
                if (a[2].equalsIgnoreCase("natural")) {
                    g.drop = null;
                } else {
                    if (plugin.items().create(a[2], 1) == null) {
                        plugin.msg(s, "<red>Unknown item: " + Text.esc(a[2]));
                        return true;
                    }
                    Integer amount = a.length > 3 ? integer(s, a[3]) : Integer.valueOf(1);
                    if (amount == null) return true;
                    g.drop = a[2];
                    g.dropAmount = Math.max(1, amount);
                }
                gens().update(g);
                plugin.msg(s, "<green>Drop for " + g.id + " is now " + (g.drop == null ? "the block's normal drop" : g.dropAmount + "x " + Text.esc(g.drop)) + ".");
            }
            case "setregen" -> {
                if (a.length < 3) {
                    plugin.msg(s, "<red>Usage: /generator setregen <id> <seconds>  <gray>(0 = never breaks)");
                    return true;
                }
                BlockGen g = find(s, a[1]);
                Integer seconds = integer(s, a[2]);
                if (g == null || seconds == null) return true;
                g.regenSeconds = Math.max(0, seconds);
                gens().update(g);
                plugin.msg(s, "<green>" + g.id + " now " + regenText(g) + ".");
            }
            case "setblock" -> {
                if (a.length < 3) {
                    plugin.msg(s, "<red>Usage: /generator setblock <id> <block>");
                    return true;
                }
                BlockGen g = find(s, a[1]);
                Material block = block(s, a[2]);
                if (g == null || block == null) return true;
                g.block = block;
                gens().update(g);
                gens().fill(g);
                plugin.msg(s, "<green>" + g.id + " is now " + block.name().toLowerCase(Locale.ROOT) + ".");
            }
            case "reset" -> {
                if (a.length < 2) {
                    plugin.msg(s, "<red>Usage: /generator reset <id>");
                    return true;
                }
                BlockGen g = find(s, a[1]);
                if (g == null) return true;
                gens().fill(g);
                plugin.msg(s, "<green>Refilled " + g.id + ".");
            }
            case "remove", "delete" -> {
                if (a.length < 2) {
                    plugin.msg(s, "<red>Usage: /generator remove <id>");
                    return true;
                }
                plugin.msg(s, gens().remove(a[1]) ? "<green>Removed. The blocks stay, they're just normal blocks now." : "<red>No generator called " + Text.esc(a[1]));
            }
            case "list" -> {
                plugin.msg(s, "<gold>Block generators (" + gens().all().size() + "):");
                for (BlockGen g : gens().all()) {
                    s.sendMessage(Text.mm("<white>" + g.id + " <gray>" + g.block.name().toLowerCase(Locale.ROOT)
                            + " x" + g.size() + ", " + regenText(g)
                            + (g.drop != null ? ", drops " + g.dropAmount + "x " + Text.esc(g.drop) : "")
                            + " @ " + g.world + " " + g.minX + "," + g.minY + "," + g.minZ));
                }
            }
            default -> help(s);
        }
        return true;
    }

    private void create(CommandSender s, String[] a, boolean area) {
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
        BlockGen g = gens().create(a[1], from, to, block, regen);
        plugin.msg(s, "<green>Generator <white>" + g.id + "</white>: " + g.size() + " block(s) of "
                + block.name().toLowerCase(Locale.ROOT) + ", " + regenText(g) + ". Mined items go straight to the inventory.");
    }

    private static String regenText(BlockGen g) {
        return g.regenSeconds == 0 ? "never runs out" : "regenerates after " + g.regenSeconds + "s";
    }

    private void help(CommandSender s) {
        plugin.msg(s, "<gold><bold>Block generators</bold> <gray>(mined items go straight to the inventory, work in protected areas)");
        String[] lines = {
                "/generator create <id> <block> [regen-seconds] <gray>- the block you're looking at",
                "/generator pos1 | pos2 <gray>- look at two corners, then:",
                "/generator area <id> <block> [regen-seconds] <gray>- a whole mine",
                "/generator setdrop <id> <item|natural> [amount]",
                "/generator setregen <id> <seconds> <gray>- 0 = block never breaks",
                "/generator setblock <id> <block> | reset <id> | remove <id> | list",
        };
        for (String line : lines) s.sendMessage(Text.mm("<yellow>" + line));
    }

    private BlockGen find(CommandSender s, String id) {
        BlockGen g = gens().get(id);
        if (g == null) plugin.msg(s, "<red>No generator called " + Text.esc(id));
        return g;
    }

    private Material block(CommandSender s, String raw) {
        Material m = Material.matchMaterial(raw);
        if (m == null || !m.isBlock() || m.isAir()) {
            plugin.msg(s, "<red>Not a block: " + Text.esc(raw));
            return null;
        }
        return m;
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
            plugin.msg(s, "<red>Not a number: " + Text.esc(raw));
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] a) {
        List<String> options = switch (a.length) {
            case 1 -> List.of("create", "area", "pos1", "pos2", "setdrop", "setregen", "setblock", "reset", "remove", "list");
            case 2 -> List.of("setdrop", "setregen", "setblock", "reset", "remove", "delete").contains(a[0].toLowerCase(Locale.ROOT))
                    ? gens().all().stream().map(g -> g.id).toList() : List.of();
            case 3 -> switch (a[0].toLowerCase(Locale.ROOT)) {
                case "create", "area", "setblock" -> blockNames();
                case "setdrop" -> Stream.concat(Stream.of("natural"), plugin.items().customIds().stream()).toList();
                default -> List.of();
            };
            default -> List.of();
        };
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(prefix)).limit(50).toList();
    }

    private static List<String> blockNames() {
        return Stream.of(Material.values())
                .filter(m -> !m.isLegacy() && m.isBlock() && !m.isAir())
                .map(m -> m.name().toLowerCase(Locale.ROOT)).toList();
    }
}
