package dev.nuccleus.relicpvp.zone;

import dev.nuccleus.relicpvp.RelicPvP;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Rectangular areas (full build height) that need a level to enter and can turn PvP off. */
public final class ZoneManager {

    public record Zone(String id, String world, int minX, int minZ, int maxX, int maxZ, int level, boolean pvp, boolean build) {
        public boolean contains(Location l) {
            World w = l.getWorld();
            if (w == null || !w.getName().equals(world)) return false;
            int x = l.getBlockX();
            int z = l.getBlockZ();
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private final RelicPvP plugin;
    private final Map<String, Zone> zones = new LinkedHashMap<>();
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public ZoneManager(RelicPvP plugin) {
        this.plugin = plugin;
    }

    public void loadData() {
        zones.clear();
        ConfigurationSection section = plugin.data().yaml().getConfigurationSection("zones");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection z = section.getConfigurationSection(id);
            if (z == null) continue;
            zones.put(id, new Zone(id, z.getString("world", "world"),
                    z.getInt("min-x"), z.getInt("min-z"), z.getInt("max-x"), z.getInt("max-z"),
                    z.getInt("level"), z.getBoolean("pvp", true), z.getBoolean("build", true)));
        }
    }

    private void persist(Zone z) {
        YamlConfiguration yaml = plugin.data().yaml();
        String p = "zones." + z.id() + ".";
        yaml.set(p + "world", z.world());
        yaml.set(p + "min-x", z.minX());
        yaml.set(p + "min-z", z.minZ());
        yaml.set(p + "max-x", z.maxX());
        yaml.set(p + "max-z", z.maxZ());
        yaml.set(p + "level", z.level());
        yaml.set(p + "pvp", z.pvp());
        yaml.set(p + "build", z.build());
        plugin.data().save();
    }

    public Zone create(String id, Location a, Location b, int level, boolean pvp, boolean build) {
        id = id.toLowerCase(Locale.ROOT);
        Zone z = new Zone(id, a.getWorld().getName(),
                Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockZ(), b.getBlockZ()),
                level, pvp, build);
        zones.put(id, z);
        persist(z);
        return z;
    }

    public Zone update(String id, Integer level, Boolean pvp, Boolean build) {
        Zone old = zones.get(id.toLowerCase(Locale.ROOT));
        if (old == null) return null;
        Zone z = new Zone(old.id(), old.world(), old.minX(), old.minZ(), old.maxX(), old.maxZ(),
                level != null ? level : old.level(), pvp != null ? pvp : old.pvp(),
                build != null ? build : old.build());
        zones.put(z.id(), z);
        persist(z);
        return z;
    }

    public boolean remove(String id) {
        id = id.toLowerCase(Locale.ROOT);
        if (zones.remove(id) == null) return false;
        plugin.data().yaml().set("zones." + id, null);
        plugin.data().save();
        return true;
    }

    public Collection<Zone> all() {
        return zones.values();
    }

    /** Highest level required by any zone covering this spot (0 if none). */
    public int requiredLevel(Location l) {
        int req = 0;
        for (Zone z : zones.values()) {
            if (z.level() > req && z.contains(l)) req = z.level();
        }
        return req;
    }

    /** False if any zone covering this spot is a no-PvP zone. */
    public boolean pvpAllowed(Location l) {
        for (Zone z : zones.values()) {
            if (!z.pvp() && z.contains(l)) return false;
        }
        return true;
    }

    /** False if any zone covering this spot is a no-build zone. */
    public boolean buildAllowed(Location l) {
        for (Zone z : zones.values()) {
            if (!z.build() && z.contains(l)) return false;
        }
        return true;
    }

    /** The most specific (highest-level) zone at this spot, or null. */
    public Zone zoneAt(Location l) {
        Zone best = null;
        for (Zone z : zones.values()) {
            if (z.contains(l) && (best == null || z.level() > best.level())) best = z;
        }
        return best;
    }

    public void setPos1(UUID player, Location l) { pos1.put(player, l); }
    public void setPos2(UUID player, Location l) { pos2.put(player, l); }
    public Location pos1(UUID player) { return pos1.get(player); }
    public Location pos2(UUID player) { return pos2.get(player); }
}
