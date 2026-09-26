package dev.nuccleus.relicpvp.portal;

import dev.nuccleus.relicpvp.RelicPvP;
import dev.nuccleus.relicpvp.util.LocUtil;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Walk-through portals that send you to a spot, a boss, or spawn. */
public final class PortalManager implements Listener {

    public static final class Portal {
        public final String id;
        public final String world;
        public final int minX, minY, minZ, maxX, maxY, maxZ;
        /** A serialized location, "boss:<id>" or "spawn". */
        public String destination;
        public String fill;

        Portal(String id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String destination, String fill) {
            this.id = id;
            this.world = world;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.destination = destination;
            this.fill = fill;
        }

        boolean contains(Location l) {
            return l.getWorld() != null && l.getWorld().getName().equals(world)
                    && l.getBlockX() >= minX && l.getBlockX() <= maxX
                    && l.getBlockY() >= minY && l.getBlockY() <= maxY
                    && l.getBlockZ() >= minZ && l.getBlockZ() <= maxZ;
        }

        public long size() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }

    public static final long MAX_BLOCKS = 2_000;

    private final RelicPvP plugin;
    private final Map<String, Portal> portals = new LinkedHashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public PortalManager(RelicPvP plugin) {
        this.plugin = plugin;
    }

    public void loadData() {
        portals.clear();
        ConfigurationSection section = plugin.data().yaml().getConfigurationSection("portals");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection c = section.getConfigurationSection(id);
            if (c == null) continue;
            portals.put(id, new Portal(id, c.getString("world", "world"),
                    c.getInt("min-x"), c.getInt("min-y"), c.getInt("min-z"),
                    c.getInt("max-x"), c.getInt("max-y"), c.getInt("max-z"),
                    c.getString("destination"), c.getString("fill", "none")));
        }
    }

    private void persist(Portal p) {
        YamlConfiguration yaml = plugin.data().yaml();
        String k = "portals." + p.id + ".";
        yaml.set(k + "world", p.world);
        yaml.set(k + "min-x", p.minX);
        yaml.set(k + "min-y", p.minY);
        yaml.set(k + "min-z", p.minZ);
        yaml.set(k + "max-x", p.maxX);
        yaml.set(k + "max-y", p.maxY);
        yaml.set(k + "max-z", p.maxZ);
        yaml.set(k + "destination", p.destination);
        yaml.set(k + "fill", p.fill);
        plugin.data().save();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::particles, 20L, 10L);
    }

    private void particles() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Portal p : portals.values()) {
            World w = Bukkit.getWorld(p.world);
            if (w == null || !w.isChunkLoaded(p.minX >> 4, p.minZ >> 4)) continue;
            int count = (int) Math.min(12, p.size() * 2);
            for (int i = 0; i < count; i++) {
                double x = p.minX + rnd.nextDouble() * (p.maxX - p.minX + 1);
                double y = p.minY + rnd.nextDouble() * (p.maxY - p.minY + 1);
                double z = p.minZ + rnd.nextDouble() * (p.maxZ - p.minZ + 1);
                w.spawnParticle(Particle.PORTAL, x, y, z, 2, 0, 0, 0, 0.3);
            }
        }
    }

    // ------------------------------------------------------------------ admin API

    public Portal create(String id, Block a, Block b, String fill) {
        id = id.toLowerCase(Locale.ROOT);
        remove(id);
        Portal p = new Portal(id, a.getWorld().getName(),
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()),
                null, fill == null ? "none" : fill.toLowerCase(Locale.ROOT));
        portals.put(id, p);
        persist(p);
        applyFill(p);
        return p;
    }

    public void setDestination(Portal p, String destination) {
        p.destination = destination;
        persist(p);
    }

    public boolean remove(String id) {
        Portal p = portals.remove(id.toLowerCase(Locale.ROOT));
        if (p == null) return false;
        if (!p.fill.equals("none")) setAll(p, Material.AIR.createBlockData());
        plugin.data().yaml().set("portals." + p.id, null);
        plugin.data().save();
        return true;
    }

    public Portal get(String id) {
        return portals.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Portal> all() {
        return portals.values();
    }

    private void applyFill(Portal p) {
        if (p.fill.equals("none")) return;
        Material m = Material.matchMaterial(p.fill);
        if (m == null || !m.isBlock()) return;
        BlockData data = m.createBlockData();
        if (data instanceof Orientable o) o.setAxis(p.minX == p.maxX ? Axis.Z : Axis.X);
        setAll(p, data);
    }

    private static void setAll(Portal p, BlockData data) {
        World w = Bukkit.getWorld(p.world);
        if (w == null) return;
        for (int x = p.minX; x <= p.maxX; x++) {
            for (int y = p.minY; y <= p.maxY; y++) {
                for (int z = p.minZ; z <= p.maxZ; z++) {
                    w.getBlockAt(x, y, z).setBlockData(data, false);
                }
            }
        }
    }

    /** Where this portal currently sends people, or null if it has nowhere to go. */
    public Location resolve(Portal p) {
        if (p.destination == null) return null;
        if (p.destination.equals("spawn")) {
            World w = Bukkit.getWorld(p.world);
            return w == null ? null : w.getSpawnLocation();
        }
        if (p.destination.startsWith("boss:")) {
            Location l = plugin.bosses().spawnOf(p.destination.substring(5));
            return l == null ? null : l.clone().add(0, 0, 6);
        }
        return LocUtil.parse(p.destination);
    }

    public String describe(Portal p) {
        if (p.destination == null) return "nowhere yet";
        if (p.destination.equals("spawn") || p.destination.startsWith("boss:")) return p.destination;
        Location l = LocUtil.parse(p.destination);
        return l == null ? "a world that isn't loaded" : l.getWorld().getName() + " " + LocUtil.pretty(l);
    }

    private Portal at(Location l) {
        for (Portal p : portals.values()) {
            if (p.contains(l)) return p;
        }
        return null;
    }

    // ------------------------------------------------------------------ events

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        Location from = e.getFrom();
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ()) return;
        Portal portal = at(to);
        if (portal == null) return;

        Player player = e.getPlayer();
        long now = System.currentTimeMillis();
        Long last = cooldown.get(player.getUniqueId());
        if (last != null && now - last < 2000) return;
        cooldown.put(player.getUniqueId(), now);

        Location dest = resolve(portal);
        if (dest == null) {
            plugin.msg(player, "<red>This portal isn't connected yet.");
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            // The zone listener cancels this if the destination needs a higher level.
            if (player.teleport(dest, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                player.playSound(dest, "entity.enderman.teleport", 1f, 1f);
            } else {
                Location back = from.clone();
                player.teleport(back);
            }
        });
    }

    /** Our portal blocks shouldn't also act as vanilla nether portals. */
    @EventHandler(ignoreCancelled = true)
    public void onVanillaPortal(PlayerPortalEvent e) {
        if (at(e.getFrom()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        if (at(e.getFrom()) != null) e.setCancelled(true);
    }

    /** Keep frameless nether-portal blocks from popping. */
    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        if (e.getBlock().getType() == Material.NETHER_PORTAL && at(e.getBlock().getLocation()) != null) e.setCancelled(true);
    }
}
