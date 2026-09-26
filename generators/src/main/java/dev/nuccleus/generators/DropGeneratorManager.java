package dev.nuccleus.generators;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** Blocks that drop an item on top of themselves on a timer, forever. */
public final class DropGeneratorManager {

    public static final class DropGen {
        public final String id;
        public final Location base;
        public final Material item;
        public final int amount;
        public final int intervalSeconds;
        int counter;

        DropGen(String id, Location base, Material item, int amount, int intervalSeconds) {
            this.id = id;
            this.base = base;
            this.item = item;
            this.amount = Math.max(1, amount);
            this.intervalSeconds = Math.max(1, intervalSeconds);
        }
    }

    private final GeneratorsPlugin plugin;
    private final Map<String, DropGen> gens = new LinkedHashMap<>();

    public DropGeneratorManager(GeneratorsPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadData() {
        gens.clear();
        ConfigurationSection section = plugin.data().yaml().getConfigurationSection("drop-generators");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection g = section.getConfigurationSection(id);
            if (g == null) continue;
            Location base = LocUtil.parse(g.getString("location"));
            Material item = Material.matchMaterial(g.getString("item", "IRON_INGOT"));
            if (base == null || item == null) {
                plugin.getLogger().warning("Drop generator '" + id + "' has a missing world or unknown item, skipping");
                continue;
            }
            gens.put(id, new DropGen(id, base, item, g.getInt("amount", 1), g.getInt("interval-seconds", 10)));
        }
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        int range = plugin.getConfig().getInt("drop-generators.activation-range", 32);
        int maxOnGround = plugin.getConfig().getInt("drop-generators.max-on-ground", 64);
        for (DropGen g : gens.values()) {
            if (++g.counter < g.intervalSeconds) continue;
            g.counter = 0;
            if (!LocUtil.isChunkLoaded(g.base)) continue;

            World world = g.base.getWorld();
            Location drop = g.base.clone().add(0.5, 1.1, 0.5);
            if (range > 0 && world.getNearbyPlayers(drop, range).isEmpty()) continue;

            int onGround = 0;
            for (Item it : world.getNearbyEntitiesByType(Item.class, drop, 1.5)) {
                if (it.getItemStack().getType() == g.item) onGround += it.getItemStack().getAmount();
            }
            if (onGround >= maxOnGround) continue;

            int left = g.amount;
            while (left > 0) {
                int n = Math.min(left, g.item.getMaxStackSize());
                Item dropped = world.dropItem(drop, new ItemStack(g.item, n));
                dropped.setVelocity(new Vector());
                left -= n;
            }
            world.spawnParticle(Particle.HAPPY_VILLAGER, drop, 6, 0.3, 0.3, 0.3);
        }
    }

    public DropGen create(String id, Location base, Material item, int amount, int intervalSeconds) {
        id = id.toLowerCase(Locale.ROOT);
        Location block = base.getBlock().getLocation();
        DropGen g = new DropGen(id, block, item, amount, intervalSeconds);
        gens.put(id, g);
        YamlConfiguration yaml = plugin.data().yaml();
        String p = "drop-generators." + id + ".";
        yaml.set(p + "location", LocUtil.serialize(block));
        yaml.set(p + "item", item.name());
        yaml.set(p + "amount", g.amount);
        yaml.set(p + "interval-seconds", g.intervalSeconds);
        plugin.data().save();
        return g;
    }

    public boolean remove(String id) {
        id = id.toLowerCase(Locale.ROOT);
        if (gens.remove(id) == null) return false;
        plugin.data().yaml().set("drop-generators." + id, null);
        plugin.data().save();
        return true;
    }

    public Collection<DropGen> all() {
        return gens.values();
    }
}
