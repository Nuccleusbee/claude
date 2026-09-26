package dev.nuccleus.relicpvp.generator;

import dev.nuccleus.relicpvp.RelicPvP;
import dev.nuccleus.relicpvp.util.LocUtil;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** Blocks that drop an item forever on a timer. */
public final class GeneratorManager {

    public static final class Generator {
        public final String id;
        public final Location base;
        public final String item;
        public final int amount;
        public final int intervalSeconds;
        int counter;

        Generator(String id, Location base, String item, int amount, int intervalSeconds) {
            this.id = id;
            this.base = base;
            this.item = item;
            this.amount = amount;
            this.intervalSeconds = Math.max(1, intervalSeconds);
        }

        public Location dropPoint() {
            return base.clone().add(0.5, 1.1, 0.5);
        }
    }

    private final RelicPvP plugin;
    private final Map<String, Generator> generators = new LinkedHashMap<>();

    public GeneratorManager(RelicPvP plugin) {
        this.plugin = plugin;
    }

    public void loadData() {
        generators.clear();
        ConfigurationSection section = plugin.data().yaml().getConfigurationSection("generators");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection g = section.getConfigurationSection(id);
            if (g == null) continue;
            Location base = LocUtil.parse(g.getString("location"));
            if (base == null) {
                plugin.getLogger().warning("Generator '" + id + "' is in a world that isn't loaded, skipping");
                continue;
            }
            generators.put(id, new Generator(id, base, g.getString("item", "IRON_INGOT"),
                    g.getInt("amount", 1), g.getInt("interval-seconds", 10)));
        }
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        int range = plugin.getConfig().getInt("generators.activation-range", 32);
        int maxOnGround = plugin.getConfig().getInt("generators.max-on-ground", 64);
        for (Generator g : generators.values()) {
            if (++g.counter < g.intervalSeconds) continue;
            g.counter = 0;
            if (!LocUtil.isChunkLoaded(g.base)) continue;

            World world = g.base.getWorld();
            Location drop = g.dropPoint();
            if (range > 0 && world.getNearbyPlayers(drop, range).isEmpty()) continue;

            ItemStack proto = plugin.items().create(g.item, 1);
            if (proto == null) continue;
            int onGround = 0;
            for (Item it : world.getNearbyEntitiesByType(Item.class, drop, 1.5)) {
                if (it.getItemStack().isSimilar(proto)) onGround += it.getItemStack().getAmount();
            }
            if (onGround >= maxOnGround) continue;

            for (ItemStack stack : plugin.items().createStacks(g.item, g.amount)) {
                Item dropped = world.dropItem(drop, stack);
                dropped.setVelocity(new Vector());
            }
            world.spawnParticle(Particle.HAPPY_VILLAGER, drop, 6, 0.3, 0.3, 0.3);
        }
    }

    public Generator create(String id, Location base, String item, int amount, int intervalSeconds) {
        id = id.toLowerCase(Locale.ROOT);
        Location block = base.getBlock().getLocation();
        Generator g = new Generator(id, block, item, amount, intervalSeconds);
        generators.put(id, g);
        YamlConfiguration yaml = plugin.data().yaml();
        String p = "generators." + id + ".";
        yaml.set(p + "location", LocUtil.serialize(block));
        yaml.set(p + "item", item);
        yaml.set(p + "amount", amount);
        yaml.set(p + "interval-seconds", g.intervalSeconds);
        plugin.data().save();
        return g;
    }

    public boolean remove(String id) {
        id = id.toLowerCase(Locale.ROOT);
        if (generators.remove(id) == null) return false;
        plugin.data().yaml().set("generators." + id, null);
        plugin.data().save();
        return true;
    }

    public Collection<Generator> all() {
        return generators.values();
    }
}
