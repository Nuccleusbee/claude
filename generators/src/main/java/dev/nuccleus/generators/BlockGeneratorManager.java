package dev.nuccleus.generators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Infinite block generators: blocks that can be mined forever. Each block remembers exactly what it was
 * (so a hand-built mix of ores keeps its layout). Whatever they drop goes straight into the miner's
 * inventory (anything that doesn't fit is deleted), and they work inside protected areas because the
 * break is handled here, at the lowest priority, before any protection plugin sees it.
 */
public final class BlockGeneratorManager implements Listener {

    public static final class BlockGen {
        public final String id;
        public final String world;
        public final int minX, minY, minZ, maxX, maxY, maxZ;
        /** Packed position -> what the block should be. */
        final Map<Long, BlockData> blocks;
        public int regenSeconds;
        /** Item to give instead of the block's natural drops, or null for natural drops. */
        public Material drop;
        public int dropAmount;

        BlockGen(String id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                 Map<Long, BlockData> blocks, int regenSeconds, Material drop, int dropAmount) {
            this.id = id;
            this.world = world;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.blocks = blocks;
            this.regenSeconds = regenSeconds;
            this.drop = drop;
            this.dropAmount = dropAmount;
        }

        BlockData dataAt(Block b) {
            if (!b.getWorld().getName().equals(world)
                    || b.getX() < minX || b.getX() > maxX
                    || b.getY() < minY || b.getY() > maxY
                    || b.getZ() < minZ || b.getZ() > maxZ) return null;
            return blocks.get(pack(b.getX(), b.getY(), b.getZ()));
        }

        public int size() {
            return blocks.size();
        }

        /** e.g. "12 diamond_ore, 30 iron_ore". */
        public String contents() {
            Map<String, Integer> counts = new TreeMap<>();
            for (BlockData d : blocks.values()) counts.merge(d.getMaterial().name().toLowerCase(Locale.ROOT), 1, Integer::sum);
            StringBuilder sb = new StringBuilder();
            counts.forEach((name, n) -> {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(n).append(' ').append(name);
            });
            return sb.toString();
        }
    }

    /** Hard cap so a typo can't freeze the server with millions of blocks. */
    public static final long MAX_BLOCKS = 50_000;

    private final GeneratorsPlugin plugin;
    private final Map<String, BlockGen> gens = new LinkedHashMap<>();
    /** Blocks currently showing the placeholder, waiting to regenerate. */
    private final Map<Location, BlockData> regenerating = new HashMap<>();
    private final Map<UUID, Long> fullWarned = new HashMap<>();

    public BlockGeneratorManager(GeneratorsPlugin plugin) {
        this.plugin = plugin;
    }

    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }

    // ------------------------------------------------------------------ saving / loading

    public void loadData() {
        gens.clear();
        ConfigurationSection section = plugin.data().yaml().getConfigurationSection("block-generators");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection c = section.getConfigurationSection(id);
            if (c == null) continue;
            int minX = c.getInt("min-x"), minY = c.getInt("min-y"), minZ = c.getInt("min-z");
            int maxX = c.getInt("max-x"), maxY = c.getInt("max-y"), maxZ = c.getInt("max-z");
            Map<Long, BlockData> blocks = new HashMap<>();
            if (c.isList("blocks")) {
                for (String line : c.getStringList("blocks")) {
                    int bar = line.indexOf('|');
                    if (bar < 0) continue;
                    String[] pos = line.substring(0, bar).split(" ");
                    try {
                        BlockData data = Bukkit.createBlockData(line.substring(bar + 1));
                        blocks.put(pack(Integer.parseInt(pos[0]), Integer.parseInt(pos[1]), Integer.parseInt(pos[2])), data);
                    } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
                        plugin.getLogger().warning("Generator '" + id + "' has a bad block entry: " + line);
                    }
                }
            } else {
                // Older single-block-type format.
                Material type = Material.matchMaterial(c.getString("block", "STONE"));
                BlockData data = (type == null || !type.isBlock() ? Material.STONE : type).createBlockData();
                for (int x = minX; x <= maxX; x++)
                    for (int y = minY; y <= maxY; y++)
                        for (int z = minZ; z <= maxZ; z++) blocks.put(pack(x, y, z), data);
            }
            gens.put(id, new BlockGen(id, c.getString("world", "world"), minX, minY, minZ, maxX, maxY, maxZ,
                    blocks, c.getInt("regen-seconds"), dropMaterial(c.getString("drop")), c.getInt("drop-amount", 1)));
        }
    }

    private static Material dropMaterial(String name) {
        if (name == null) return null;
        Material m = Material.matchMaterial(name);
        return m == null || !m.isItem() ? null : m;
    }

    private void persist(BlockGen g) {
        YamlConfiguration yaml = plugin.data().yaml();
        String p = "block-generators." + g.id + ".";
        yaml.set("block-generators." + g.id, null);
        yaml.set(p + "world", g.world);
        yaml.set(p + "min-x", g.minX);
        yaml.set(p + "min-y", g.minY);
        yaml.set(p + "min-z", g.minZ);
        yaml.set(p + "max-x", g.maxX);
        yaml.set(p + "max-y", g.maxY);
        yaml.set(p + "max-z", g.maxZ);
        yaml.set(p + "regen-seconds", g.regenSeconds);
        yaml.set(p + "drop", g.drop == null ? null : g.drop.name());
        yaml.set(p + "drop-amount", g.dropAmount);
        List<String> lines = new ArrayList<>(g.blocks.size());
        for (int x = g.minX; x <= g.maxX; x++)
            for (int y = g.minY; y <= g.maxY; y++)
                for (int z = g.minZ; z <= g.maxZ; z++) {
                    BlockData d = g.blocks.get(pack(x, y, z));
                    if (d != null) lines.add(x + " " + y + " " + z + "|" + d.getAsString());
                }
        yaml.set(p + "blocks", lines);
        plugin.data().save();
    }

    // ------------------------------------------------------------------ admin API

    private static int[] bounds(Block a, Block b) {
        return new int[] {
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()),
        };
    }

    /** Turns every non-air block between a and b into a generator, keeping each block as it is. Null if all air. */
    public BlockGen capture(String id, Block a, Block b, int regenSeconds) {
        int[] r = bounds(a, b);
        World w = a.getWorld();
        Map<Long, BlockData> blocks = new HashMap<>();
        for (int x = r[0]; x <= r[3]; x++)
            for (int y = r[1]; y <= r[4]; y++)
                for (int z = r[2]; z <= r[5]; z++) {
                    Block block = w.getBlockAt(x, y, z);
                    if (!block.getType().isAir()) blocks.put(pack(x, y, z), block.getBlockData());
                }
        if (blocks.isEmpty()) return null;
        return register(id, w, r, blocks, regenSeconds);
    }

    /** Creates a generator covering the box between a and b, filled with {@code type}. */
    public BlockGen create(String id, Block a, Block b, Material type, int regenSeconds) {
        int[] r = bounds(a, b);
        BlockData data = type.createBlockData();
        Map<Long, BlockData> blocks = new HashMap<>();
        for (int x = r[0]; x <= r[3]; x++)
            for (int y = r[1]; y <= r[4]; y++)
                for (int z = r[2]; z <= r[5]; z++) blocks.put(pack(x, y, z), data);
        BlockGen g = register(id, a.getWorld(), r, blocks, regenSeconds);
        fill(g);
        return g;
    }

    private BlockGen register(String id, World w, int[] r, Map<Long, BlockData> blocks, int regenSeconds) {
        id = id.toLowerCase(Locale.ROOT);
        remove(id);
        BlockGen g = new BlockGen(id, w.getName(), r[0], r[1], r[2], r[3], r[4], r[5], blocks, Math.max(0, regenSeconds), null, 1);
        gens.put(id, g);
        persist(g);
        return g;
    }

    /** The next generator number: 1, 2, 3... (one higher than the biggest number used so far). */
    public String nextId() {
        int max = 0;
        for (String id : gens.keySet()) {
            try {
                max = Math.max(max, Integer.parseInt(id));
            } catch (NumberFormatException ignored) {
                // named generator
            }
        }
        return String.valueOf(max + 1);
    }

    public boolean remove(String id) {
        BlockGen g = gens.remove(id.toLowerCase(Locale.ROOT));
        if (g == null) return false;
        regenerating.entrySet().removeIf(e -> {
            Block b = e.getKey().getBlock();
            if (g.dataAt(b) == null) return false;
            b.setBlockData(e.getValue(), false);
            return true;
        });
        plugin.data().yaml().set("block-generators." + g.id, null);
        plugin.data().save();
        return true;
    }

    public BlockGen get(String id) {
        return gens.get(id.toLowerCase(Locale.ROOT));
    }

    public void update(BlockGen g) {
        persist(g);
    }

    /** Makes every block in the generator the same type. */
    public void setAll(BlockGen g, Material type) {
        BlockData data = type.createBlockData();
        g.blocks.replaceAll((k, v) -> data);
        persist(g);
        fill(g);
    }

    /** Puts every block in the generator back to what it should be. */
    public void fill(BlockGen g) {
        World w = Bukkit.getWorld(g.world);
        if (w == null) return;
        for (int x = g.minX; x <= g.maxX; x++)
            for (int y = g.minY; y <= g.maxY; y++)
                for (int z = g.minZ; z <= g.maxZ; z++) {
                    BlockData d = g.blocks.get(pack(x, y, z));
                    if (d == null) continue;
                    Block b = w.getBlockAt(x, y, z);
                    regenerating.remove(b.getLocation());
                    b.setBlockData(d, false);
                }
    }

    public Collection<BlockGen> all() {
        return gens.values();
    }

    /** Puts every regenerating block back right away (used on shutdown so nothing is left as bedrock). */
    public void shutdown() {
        regenerating.forEach((loc, data) -> loc.getBlock().setBlockData(data, false));
        regenerating.clear();
    }

    private BlockData expected(Block b) {
        for (BlockGen g : gens.values()) {
            BlockData d = g.dataAt(b);
            if (d != null) return d;
        }
        return null;
    }

    private BlockGen at(Block b) {
        for (BlockGen g : gens.values()) {
            if (g.dataAt(b) != null) return g;
        }
        return null;
    }

    // ------------------------------------------------------------------ mining

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        BlockGen g = at(block);
        if (g == null) return;
        BlockData should = g.dataAt(block);

        // We handle the break ourselves; cancelling means protection plugins and the world never see it.
        e.setCancelled(true);
        Player p = e.getPlayer();

        if (regenerating.containsKey(block.getLocation())) return;
        if (p.getGameMode() == GameMode.CREATIVE) {
            if (p.hasPermission("generators.admin")) plugin.msg(p, "<gray>That's generator <white>" + g.id + "</white>. Use /generator remove " + g.id + " to delete it.");
            return;
        }
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        if (block.getType() != should.getMaterial()) {
            // Someone swapped the block (e.g. world edit); fix it and move on.
            block.setBlockData(should, false);
            return;
        }

        ItemStack tool = p.getInventory().getItemInMainHand();
        List<ItemStack> drops = new ArrayList<>();
        if (g.drop != null) {
            int left = g.dropAmount;
            while (left > 0) {
                int n = Math.min(left, g.drop.getMaxStackSize());
                drops.add(new ItemStack(g.drop, n));
                left -= n;
            }
        } else {
            drops.addAll(block.getDrops(tool, p));
            if (drops.isEmpty()) {
                plugin.msg(p, "<red>You need a better tool to mine this.");
                return;
            }
        }

        // Straight into the inventory; whatever doesn't fit is deleted.
        boolean overflow = false;
        for (ItemStack d : drops) {
            if (!p.getInventory().addItem(d).isEmpty()) overflow = true;
        }
        if (overflow) warnFull(p);

        int exp = e.getExpToDrop();
        if (exp > 0) p.giveExp(exp);
        if (tool.getType().getMaxDurability() > 0) p.damageItemStack(EquipmentSlot.HAND, 1);

        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().spawnParticle(Particle.BLOCK, center, 20, 0.3, 0.3, 0.3, block.getBlockData());
        p.playSound(center, "entity.item.pickup", 0.6f, 1.2f);

        if (g.regenSeconds > 0) {
            Location key = block.getLocation();
            regenerating.put(key, should);
            block.setType(placeholder(), false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                BlockData data = regenerating.remove(key);
                if (data != null) key.getBlock().setBlockData(data, false);
            }, g.regenSeconds * 20L);
        }
    }

    private void warnFull(Player p) {
        long now = System.currentTimeMillis();
        Long last = fullWarned.get(p.getUniqueId());
        if (last != null && now - last < 3000) return;
        fullWarned.put(p.getUniqueId(), now);
        p.sendActionBar(MiniMessage.miniMessage().deserialize("<red>Inventory full! Extra items were deleted."));
        p.playSound(p.getLocation(), "block.note_block.bass", 0.7f, 0.5f);
    }

    private Material placeholder() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("block-generators.placeholder", "BEDROCK"));
        return m == null || !m.isBlock() ? Material.BEDROCK : m;
    }

    // ------------------------------------------------------------------ keep generators intact

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> expected(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> expected(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> expected(b) != null)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> expected(b) != null)) e.setCancelled(true);
    }
}
