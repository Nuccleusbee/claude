package dev.nuccleus.relicpvp.mine;

import dev.nuccleus.relicpvp.RelicPvP;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
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
import org.bukkit.inventory.PlayerInventory;

/**
 * Infinite block generators: blocks (or whole areas) that can be mined forever. Whatever they drop goes
 * straight into the miner's inventory, and they work inside protected areas because the break is handled
 * here, at the lowest priority, before any protection plugin sees it.
 */
public final class BlockGeneratorManager implements Listener {

    public static final class BlockGen {
        public final String id;
        public final String world;
        public final int minX, minY, minZ, maxX, maxY, maxZ;
        public Material block;
        public int regenSeconds;
        /** Item reference to give instead of the block's natural drops, or null for natural drops. */
        public String drop;
        public int dropAmount;

        BlockGen(String id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                 Material block, int regenSeconds, String drop, int dropAmount) {
            this.id = id;
            this.world = world;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.block = block;
            this.regenSeconds = regenSeconds;
            this.drop = drop;
            this.dropAmount = dropAmount;
        }

        boolean contains(Block b) {
            return b.getWorld().getName().equals(world)
                    && b.getX() >= minX && b.getX() <= maxX
                    && b.getY() >= minY && b.getY() <= maxY
                    && b.getZ() >= minZ && b.getZ() <= maxZ;
        }

        public long size() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }

    /** Hard cap so a typo can't freeze the server filling millions of blocks. */
    public static final long MAX_BLOCKS = 50_000;

    private final RelicPvP plugin;
    private final Map<String, BlockGen> gens = new LinkedHashMap<>();
    /** Blocks currently showing the placeholder, waiting to regenerate. */
    private final Map<Location, Material> regenerating = new HashMap<>();

    public BlockGeneratorManager(RelicPvP plugin) {
        this.plugin = plugin;
    }

    public void loadData() {
        gens.clear();
        ConfigurationSection section = plugin.data().yaml().getConfigurationSection("block-generators");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection c = section.getConfigurationSection(id);
            if (c == null) continue;
            Material block = Material.matchMaterial(c.getString("block", "STONE"));
            if (block == null || !block.isBlock()) block = Material.STONE;
            gens.put(id, new BlockGen(id, c.getString("world", "world"),
                    c.getInt("min-x"), c.getInt("min-y"), c.getInt("min-z"),
                    c.getInt("max-x"), c.getInt("max-y"), c.getInt("max-z"),
                    block, c.getInt("regen-seconds"), c.getString("drop"), c.getInt("drop-amount", 1)));
        }
    }

    private void persist(BlockGen g) {
        YamlConfiguration yaml = plugin.data().yaml();
        String p = "block-generators." + g.id + ".";
        yaml.set(p + "world", g.world);
        yaml.set(p + "min-x", g.minX);
        yaml.set(p + "min-y", g.minY);
        yaml.set(p + "min-z", g.minZ);
        yaml.set(p + "max-x", g.maxX);
        yaml.set(p + "max-y", g.maxY);
        yaml.set(p + "max-z", g.maxZ);
        yaml.set(p + "block", g.block.name());
        yaml.set(p + "regen-seconds", g.regenSeconds);
        yaml.set(p + "drop", g.drop);
        yaml.set(p + "drop-amount", g.dropAmount);
        plugin.data().save();
    }

    // ------------------------------------------------------------------ admin API

    /** Creates a generator covering the box between a and b and fills it with {@code block}. */
    public BlockGen create(String id, Block a, Block b, Material block, int regenSeconds) {
        id = id.toLowerCase(Locale.ROOT);
        remove(id);
        BlockGen g = new BlockGen(id, a.getWorld().getName(),
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()),
                block, Math.max(0, regenSeconds), null, 1);
        gens.put(id, g);
        persist(g);
        fill(g);
        return g;
    }

    public boolean remove(String id) {
        BlockGen g = gens.remove(id.toLowerCase(Locale.ROOT));
        if (g == null) return false;
        regenerating.entrySet().removeIf(e -> {
            Block b = e.getKey().getBlock();
            if (!g.contains(b)) return false;
            b.setType(e.getValue(), false);
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

    /** Sets every block in the generator to its block type. */
    public void fill(BlockGen g) {
        World w = Bukkit.getWorld(g.world);
        if (w == null) return;
        for (int x = g.minX; x <= g.maxX; x++) {
            for (int y = g.minY; y <= g.maxY; y++) {
                for (int z = g.minZ; z <= g.maxZ; z++) {
                    Block b = w.getBlockAt(x, y, z);
                    regenerating.remove(b.getLocation());
                    b.setType(g.block, false);
                }
            }
        }
    }

    public Collection<BlockGen> all() {
        return gens.values();
    }

    /** Puts every regenerating block back right away (used on shutdown so nothing is left as bedrock). */
    public void shutdown() {
        regenerating.forEach((loc, type) -> loc.getBlock().setType(type, false));
        regenerating.clear();
    }

    private BlockGen at(Block b) {
        for (BlockGen g : gens.values()) {
            if (g.contains(b)) return g;
        }
        return null;
    }

    // ------------------------------------------------------------------ mining

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        BlockGen g = at(block);
        if (g == null) return;

        // We handle the break ourselves; cancelling means protection plugins and the world never see it.
        e.setCancelled(true);
        Player p = e.getPlayer();

        if (regenerating.containsKey(block.getLocation())) return;
        if (p.getGameMode() == GameMode.CREATIVE) {
            if (p.hasPermission("relicpvp.admin")) plugin.msg(p, "<gray>That's generator <white>" + g.id + "</white>. Use /generator remove " + g.id + " to delete it.");
            return;
        }
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        if (block.getType() != g.block) {
            // Someone swapped the block (e.g. world edit); fix it and move on.
            block.setType(g.block, false);
            return;
        }

        ItemStack tool = p.getInventory().getItemInMainHand();
        List<ItemStack> drops = new ArrayList<>();
        if (g.drop != null) {
            drops.addAll(plugin.items().createStacks(g.drop, g.dropAmount));
        } else {
            drops.addAll(block.getDrops(tool, p));
            if (drops.isEmpty()) {
                plugin.msg(p, "<red>You need a better tool to mine this.");
                return;
            }
        }
        if (!fits(p.getInventory(), drops)) {
            plugin.msg(p, "<red>Your inventory is full!");
            p.playSound(p.getLocation(), "block.note_block.bass", 1f, 0.5f);
            return;
        }

        drops.forEach(d -> p.getInventory().addItem(d));
        int exp = e.getExpToDrop();
        if (exp > 0) p.giveExp(exp);
        if (tool.getType().getMaxDurability() > 0) p.damageItemStack(EquipmentSlot.HAND, 1);

        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().spawnParticle(Particle.BLOCK, center, 20, 0.3, 0.3, 0.3, block.getBlockData());
        p.playSound(center, "entity.item.pickup", 0.6f, 1.2f);

        if (g.regenSeconds > 0) {
            Material placeholder = placeholder();
            Location key = block.getLocation();
            regenerating.put(key, g.block);
            block.setType(placeholder, false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Material type = regenerating.remove(key);
                if (type != null) key.getBlock().setType(type, false);
            }, g.regenSeconds * 20L);
        }
    }

    private Material placeholder() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("block-generators.placeholder", "BEDROCK"));
        return m == null || !m.isBlock() ? Material.BEDROCK : m;
    }

    private static boolean fits(PlayerInventory inv, List<ItemStack> items) {
        // Check against a copy so a partial add never happens.
        ItemStack[] copy = inv.getStorageContents().clone();
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] != null) copy[i] = copy[i].clone();
        }
        for (ItemStack item : items) {
            int left = item.getAmount();
            for (int i = 0; i < copy.length && left > 0; i++) {
                ItemStack slot = copy[i];
                if (slot == null || slot.getType().isAir()) {
                    int n = Math.min(left, item.getMaxStackSize());
                    ItemStack placed = item.clone();
                    placed.setAmount(n);
                    copy[i] = placed;
                    left -= n;
                } else if (slot.isSimilar(item) && slot.getAmount() < slot.getMaxStackSize()) {
                    int n = Math.min(left, slot.getMaxStackSize() - slot.getAmount());
                    slot.setAmount(slot.getAmount() + n);
                    left -= n;
                }
            }
            if (left > 0) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ keep generators intact

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> at(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> at(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> at(b) != null)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> at(b) != null)) e.setCancelled(true);
    }
}
