package dev.nuccleus.relicpvp.rare;

import dev.nuccleus.relicpvp.RelicPvP;
import dev.nuccleus.relicpvp.util.LocUtil;
import dev.nuccleus.relicpvp.util.Text;
import dev.nuccleus.relicpvp.zone.ZoneManager.Zone;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** Every so often a glowing rare item appears at a random spawn point and the server is told. */
public final class RareSpawnManager implements Listener {

    public record Entry(String item, int amount, int weight, String announce) {}
    record Active(Item item, long expiresAt) {}

    private final RelicPvP plugin;
    private final List<Entry> table = new ArrayList<>();
    private final List<Location> points = new ArrayList<>();
    private final Map<UUID, Active> active = new HashMap<>();
    private int intervalSeconds;
    private int despawnSeconds;
    private int maxActive;
    private int minPlayers;
    private String reveal;
    private int seconds;

    public RareSpawnManager(RelicPvP plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("rare-spawns");
        table.clear();
        if (c == null) return;
        intervalSeconds = Math.max(10, c.getInt("interval-seconds", 300));
        despawnSeconds = c.getInt("despawn-seconds", 180);
        maxActive = c.getInt("max-active", 3);
        minPlayers = c.getInt("min-players-online", 1);
        reveal = c.getString("reveal", "zone").toLowerCase(Locale.ROOT);
        table.addAll(parseTable(c.getConfigurationSection("table")));
    }

    /** Reads a loot table section: {key: {item, amount, weight, announce}}. */
    public static List<Entry> parseTable(ConfigurationSection t) {
        List<Entry> out = new ArrayList<>();
        if (t == null) return out;
        for (String key : t.getKeys(false)) {
            ConfigurationSection e = t.getConfigurationSection(key);
            if (e == null) continue;
            out.add(new Entry(e.getString("item", "DIAMOND"), e.getInt("amount", 1),
                    Math.max(1, e.getInt("weight", 1)), e.getString("announce", "<yellow>A rare item has appeared")));
        }
        return out;
    }

    public void loadData() {
        points.clear();
        for (String s : plugin.data().yaml().getStringList("rare-points")) {
            Location l = LocUtil.parse(s);
            if (l != null) points.add(l);
        }
    }

    private void persist() {
        plugin.data().yaml().set("rare-points", points.stream().map(LocUtil::serialize).toList());
        plugin.data().save();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Active> it = active.values().iterator(); it.hasNext(); ) {
            Active a = it.next();
            if (!a.item().isValid()) {
                it.remove();
            } else if (despawnSeconds > 0 && now >= a.expiresAt()) {
                a.item().remove();
                it.remove();
            } else {
                Location l = a.item().getLocation();
                l.getWorld().spawnParticle(Particle.END_ROD, l.clone().add(0, 1.5, 0), 6, 0.05, 1.2, 0.05, 0);
            }
        }
        if (++seconds >= intervalSeconds) {
            seconds = 0;
            spawnRandom();
        }
    }

    /** Tries to spawn one rare item. Returns a reason it couldn't, or null on success. */
    public String spawnRandom() {
        if (points.isEmpty()) return "no spawn points (use /relic rare addpoint)";
        if (table.isEmpty()) return "the rare-spawns table in config.yml is empty";
        if (Bukkit.getOnlinePlayers().size() < minPlayers) return "not enough players online";
        if (active.size() >= maxActive) return "already " + active.size() + " rare items out";

        List<Location> free = new ArrayList<>(points);
        Collections.shuffle(free);
        Location spot = null;
        for (Location l : free) {
            if (active.values().stream().noneMatch(a -> a.item().getWorld() == l.getWorld() && a.item().getLocation().distanceSquared(l) < 4)) {
                spot = l;
                break;
            }
        }
        if (spot == null) return "every spawn point already has an item";

        return spawnAt(spot, pick(table), true) ? null : "unknown item in the rare-spawns table";
    }

    /**
     * Drops a glowing, tracked rare item from {@code entry} at {@code spot} and announces it.
     * {@code serverWide} announces to everyone; otherwise only to players within 100 blocks.
     */
    public boolean spawnAt(Location spot, Entry entry, boolean serverWide) {
        ItemStack stack = plugin.items().create(entry.item(), entry.amount());
        if (stack == null) return false;

        Item item = spot.getWorld().dropItem(spot, stack);
        item.setVelocity(new Vector());
        item.setGlowing(true);
        item.setUnlimitedLifetime(true);
        item.setPersistent(false);
        active.put(item.getUniqueId(), new Active(item, System.currentTimeMillis() + despawnSeconds * 1000L));

        String message = entry.announce() + where(spot);
        if (serverWide) {
            plugin.broadcast(message);
            for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), "block.beacon.activate", 0.8f, 1.4f);
        } else {
            for (Player p : spot.getWorld().getNearbyPlayers(spot, 100)) {
                plugin.msg(p, message);
                p.playSound(p.getLocation(), "block.beacon.activate", 0.8f, 1.4f);
            }
        }
        return true;
    }

    private String where(Location l) {
        Zone zone = plugin.zones().zoneAt(l);
        String zoneName = zone != null ? zone.id() : plugin.wasteland().isWasteland(l) ? "the Wasteland" : "the wilds";
        return switch (reveal) {
            case "none" -> "<yellow>!";
            case "coords" -> " <yellow>at <white>" + LocUtil.pretty(l) + "<yellow>!";
            case "both" -> " <yellow>in <white>" + zoneName + " <gray>(" + LocUtil.pretty(l) + ")<yellow>!";
            default -> " <yellow>in <white>" + zoneName + "<yellow>!";
        };
    }

    public static Entry pick(List<Entry> table) {
        int total = table.stream().mapToInt(Entry::weight).sum();
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (Entry e : table) {
            roll -= e.weight();
            if (roll < 0) return e;
        }
        return table.get(table.size() - 1);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPickup(EntityPickupItemEvent e) {
        if (active.remove(e.getItem().getUniqueId()) == null) return;
        if (e.getEntity() instanceof Player p) {
            String name = Text.serialize(e.getItem().getItemStack().displayName());
            plugin.broadcast("<yellow>" + p.getName() + " <gray>grabbed</gray> " + name + "<gray>!");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMerge(ItemMergeEvent e) {
        if (active.containsKey(e.getEntity().getUniqueId()) || active.containsKey(e.getTarget().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    public void shutdown() {
        active.values().forEach(a -> a.item().remove());
        active.clear();
    }

    // ------------------------------------------------------------------ commands

    public void addPoint(Location l) {
        points.add(l.clone());
        persist();
    }

    /** Removes the point nearest to {@code l} within 5 blocks. */
    public boolean removeNear(Location l) {
        Location best = null;
        double bestDist = 25;
        for (Location p : points) {
            if (p.getWorld() != l.getWorld()) continue;
            double d = p.distanceSquared(l);
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        if (best == null) return false;
        points.remove(best);
        persist();
        return true;
    }

    public void clearPoints() {
        points.clear();
        persist();
    }

    public List<Location> points() {
        return points;
    }

    public int activeCount() {
        return active.size();
    }
}
