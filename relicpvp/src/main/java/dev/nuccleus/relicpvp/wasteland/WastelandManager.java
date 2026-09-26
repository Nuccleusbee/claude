package dev.nuccleus.relicpvp.wasteland;

import dev.nuccleus.relicpvp.RelicPvP;
import dev.nuccleus.relicpvp.rare.RareSpawnManager;
import dev.nuccleus.relicpvp.rare.RareSpawnManager.Entry;
import dev.nuccleus.relicpvp.util.Entities;
import dev.nuccleus.relicpvp.util.Text;
import dev.nuccleus.relicpvp.zone.ZoneManager.Zone;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * The wasteland: custom monsters keep spawning around players out there and hunt them down,
 * and rare loot keeps dropping near whoever is brave enough to be there.
 */
public final class WastelandManager implements Listener {

    record Drop(String item, int amount, double chance) {}

    static final class MonsterDef {
        String id;
        String name;
        EntityType type;
        double health;
        double damageMultiplier;
        double speedMultiplier;
        int weight;
        int exp;
        final Map<EquipmentSlot, String> equipment = new EnumMap<>(EquipmentSlot.class);
        final List<Drop> drops = new ArrayList<>();
    }

    private final RelicPvP plugin;
    private final NamespacedKey monsterKey;
    private final Map<String, MonsterDef> monsters = new LinkedHashMap<>();
    private final Map<UUID, LivingEntity> alive = new HashMap<>();
    private final List<Entry> loot = new ArrayList<>();

    private boolean enabled;
    private String worldName;
    private String zoneId;
    private int minRadius;
    private int spawnEverySeconds;
    private int maxPerPlayer;
    private int minDistance;
    private int maxDistance;
    private int chaseRange;
    private int lootEverySeconds;
    private int lootMinDistance;
    private int lootMaxDistance;
    private int seconds;

    public WastelandManager(RelicPvP plugin) {
        this.plugin = plugin;
        this.monsterKey = new NamespacedKey(plugin, "wasteland_monster");
    }

    public void loadConfig() {
        monsters.clear();
        loot.clear();
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("wasteland");
        enabled = c != null && c.getBoolean("enabled", true);
        if (c == null) return;
        worldName = c.getString("world", "world");
        zoneId = c.getString("zone", "");
        minRadius = c.getInt("min-radius", plugin.getConfig().getInt("world-layout.biome-radius", 1200));

        ConfigurationSection m = c.getConfigurationSection("monsters");
        if (m != null) {
            spawnEverySeconds = Math.max(1, m.getInt("spawn-every-seconds", 6));
            maxPerPlayer = m.getInt("max-per-player", 6);
            minDistance = m.getInt("spawn-distance-min", 16);
            maxDistance = Math.max(minDistance + 1, m.getInt("spawn-distance-max", 32));
            chaseRange = m.getInt("chase-range", 48);
            ConfigurationSection types = m.getConfigurationSection("types");
            if (types != null) {
                for (String id : types.getKeys(false)) {
                    MonsterDef d = parseMonster(id, types.getConfigurationSection(id));
                    if (d != null) monsters.put(d.id, d);
                }
            }
        }
        ConfigurationSection l = c.getConfigurationSection("loot");
        if (l != null) {
            lootEverySeconds = Math.max(5, l.getInt("spawn-every-seconds", 90));
            lootMinDistance = l.getInt("distance-min", 20);
            lootMaxDistance = Math.max(lootMinDistance + 1, l.getInt("distance-max", 60));
            loot.addAll(RareSpawnManager.parseTable(l.getConfigurationSection("table")));
        }
    }

    private MonsterDef parseMonster(String id, ConfigurationSection c) {
        if (c == null) return null;
        MonsterDef d = new MonsterDef();
        d.id = id.toLowerCase(Locale.ROOT);
        d.name = c.getString("name", id);
        try {
            d.type = EntityType.valueOf(c.getString("entity", "ZOMBIE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            d.type = null;
        }
        if (d.type == null || !d.type.isAlive() || !d.type.isSpawnable()) {
            plugin.getLogger().warning("Wasteland monster '" + id + "' has an invalid entity type, skipping");
            return null;
        }
        d.health = Math.max(1, c.getDouble("health", 30));
        d.damageMultiplier = c.getDouble("damage-multiplier", 1.0);
        d.speedMultiplier = c.getDouble("speed-multiplier", 1.0);
        d.weight = Math.max(1, c.getInt("weight", 10));
        d.exp = c.getInt("exp", 10);
        ConfigurationSection eq = c.getConfigurationSection("equipment");
        if (eq != null) {
            for (String slot : eq.getKeys(false)) {
                EquipmentSlot s = switch (slot.toLowerCase(Locale.ROOT)) {
                    case "helmet", "head" -> EquipmentSlot.HEAD;
                    case "chestplate", "chest" -> EquipmentSlot.CHEST;
                    case "leggings", "legs" -> EquipmentSlot.LEGS;
                    case "boots", "feet" -> EquipmentSlot.FEET;
                    case "main-hand", "hand" -> EquipmentSlot.HAND;
                    case "off-hand" -> EquipmentSlot.OFF_HAND;
                    default -> null;
                };
                if (s != null) d.equipment.put(s, eq.getString(slot));
            }
        }
        ConfigurationSection drops = c.getConfigurationSection("drops");
        if (drops != null) {
            for (String key : drops.getKeys(false)) {
                ConfigurationSection dr = drops.getConfigurationSection(key);
                if (dr != null) d.drops.add(new Drop(dr.getString("item", "BONE"), dr.getInt("amount", 1), dr.getDouble("chance", 1.0)));
            }
        }
        return d;
    }

    public boolean isWasteland(Location l) {
        if (!enabled || l.getWorld() == null) return false;
        if (zoneId != null && !zoneId.isEmpty()) {
            for (Zone z : plugin.zones().all()) {
                if (z.id().equalsIgnoreCase(zoneId)) return z.contains(l);
            }
            return false;
        }
        if (!l.getWorld().getName().equals(worldName)) return false;
        return l.getX() * l.getX() + l.getZ() * l.getZ() >= (double) minRadius * minRadius;
    }

    // ------------------------------------------------------------------ ticking

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    private void tick() {
        if (!enabled) return;
        seconds++;
        chase();
        List<Player> hunted = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                if (isWasteland(p.getLocation())) hunted.add(p);
            }
        }
        if (hunted.isEmpty()) return;

        if (!monsters.isEmpty() && seconds % spawnEverySeconds == 0) {
            for (Player p : hunted) {
                if (nearbyCount(p) < maxPerPlayer) spawnMonsterNear(p);
            }
        }
        if (!loot.isEmpty() && seconds % lootEverySeconds == 0) {
            Player lucky = hunted.get(ThreadLocalRandom.current().nextInt(hunted.size()));
            Location spot = surfaceNear(lucky.getLocation(), lootMinDistance, lootMaxDistance);
            if (spot != null) plugin.rares().spawnAt(spot.add(0.5, 0.5, 0.5), RareSpawnManager.pick(loot), false);
        }
    }

    /** Keeps every wasteland monster locked onto the nearest player so they chase you down. */
    private void chase() {
        for (Iterator<LivingEntity> it = alive.values().iterator(); it.hasNext(); ) {
            LivingEntity e = it.next();
            if (!e.isValid()) {
                it.remove();
                continue;
            }
            if (!(e instanceof Mob mob)) continue;
            if (mob.getTarget() instanceof Player t && t.isValid() && !t.isDead()
                    && t.getWorld() == mob.getWorld() && t.getLocation().distanceSquared(mob.getLocation()) < chaseRange * chaseRange) continue;
            Player nearest = null;
            double best = (double) chaseRange * chaseRange;
            for (Player p : mob.getWorld().getNearbyPlayers(mob.getLocation(), chaseRange)) {
                if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
                double d = p.getLocation().distanceSquared(mob.getLocation());
                if (d < best) {
                    best = d;
                    nearest = p;
                }
            }
            mob.setTarget(nearest);
            if (nearest == null && !isWasteland(mob.getLocation())) {
                // Wandered out and nobody around: despawn.
                mob.remove();
                it.remove();
            }
        }
    }

    private int nearbyCount(Player p) {
        int n = 0;
        double r = (double) maxDistance * 1.5;
        for (LivingEntity e : alive.values()) {
            if (e.isValid() && e.getWorld() == p.getWorld() && e.getLocation().distanceSquared(p.getLocation()) < r * r) n++;
        }
        return n;
    }

    private void spawnMonsterNear(Player p) {
        Location spot = surfaceNear(p.getLocation(), minDistance, maxDistance);
        if (spot == null || !isWasteland(spot)) return;
        MonsterDef def = pickMonster();
        spawn(def, spot.add(0.5, 0, 0.5), p);
    }

    private LivingEntity spawn(MonsterDef def, Location at, Player target) {
        Entity raw = at.getWorld().spawnEntity(at, def.type);
        if (!(raw instanceof LivingEntity e)) {
            raw.remove();
            return null;
        }
        e.customName(Text.mm(def.name));
        e.setCustomNameVisible(true);
        e.setRemoveWhenFarAway(true);
        e.setPersistent(false);
        e.setCanPickupItems(false);
        if (e instanceof Ageable a) a.setAdult();
        AttributeInstance hp = Entities.attribute(e, "max_health");
        if (hp != null) hp.setBaseValue(def.health);
        e.setHealth(Math.min(def.health, hp == null ? e.getHealth() : hp.getValue()));
        AttributeInstance speed = Entities.attribute(e, "movement_speed");
        if (speed != null) speed.setBaseValue(speed.getBaseValue() * def.speedMultiplier);
        AttributeInstance follow = Entities.attribute(e, "follow_range");
        if (follow != null) follow.setBaseValue(Math.max(follow.getBaseValue(), chaseRange));

        EntityEquipment eq = e.getEquipment();
        if (eq != null) {
            def.equipment.forEach((slot, ref) -> {
                ItemStack item = plugin.items().create(ref, 1);
                if (item == null) return;
                eq.setItem(slot, item);
                eq.setDropChance(slot, 0f);
            });
        }
        e.getPersistentDataContainer().set(monsterKey, PersistentDataType.STRING, def.id);
        if (e instanceof Mob mob && target != null) mob.setTarget(target);
        alive.put(e.getUniqueId(), e);
        return e;
    }

    private MonsterDef pickMonster() {
        int total = monsters.values().stream().mapToInt(d -> d.weight).sum();
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (MonsterDef d : monsters.values()) {
            roll -= d.weight;
            if (roll < 0) return d;
        }
        return monsters.values().iterator().next();
    }

    /** A safe standing spot on the surface between min and max blocks from {@code around}, or null. */
    private static Location surfaceNear(Location around, int min, int max) {
        World w = around.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = rnd.nextDouble(Math.PI * 2);
            double dist = rnd.nextDouble(min, max);
            int x = (int) Math.floor(around.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(around.getZ() + Math.sin(angle) * dist);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block ground = w.getHighestBlockAt(x, z);
            Material type = ground.getType();
            if (!type.isSolid() || type == Material.WATER || type == Material.LAVA) continue;
            if (Math.abs(ground.getY() - around.getBlockY()) > 20) continue;
            return ground.getLocation().add(0, 1, 0);
        }
        return null;
    }

    public void shutdown() {
        alive.values().forEach(Entity::remove);
        alive.clear();
    }

    public List<String> monsterIds() {
        return new ArrayList<>(monsters.keySet());
    }

    /** For /relic wasteland spawn <id>. */
    public boolean spawnById(String id, Location at) {
        MonsterDef def = monsters.get(id.toLowerCase(Locale.ROOT));
        return def != null && spawn(def, at, null) != null;
    }

    // ------------------------------------------------------------------ events

    private MonsterDef defOf(Entity e) {
        String id = e.getPersistentDataContainer().get(monsterKey, PersistentDataType.STRING);
        return id == null ? null : monsters.get(id);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        LivingEntity source = Entities.livingSource(e.getDamager());
        if (source == null) return;
        MonsterDef def = defOf(source);
        if (def != null) {
            // Monsters don't hurt each other.
            if (defOf(e.getEntity()) != null) {
                e.setCancelled(true);
                return;
            }
            e.setDamage(e.getDamage() * def.damageMultiplier);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        MonsterDef def = defOf(e.getEntity());
        if (def == null) return;
        alive.remove(e.getEntity().getUniqueId());
        e.getDrops().clear();
        e.setDroppedExp(def.exp);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Drop d : def.drops) {
            if (rnd.nextDouble() < d.chance()) e.getDrops().addAll(plugin.items().createStacks(d.item(), d.amount()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        if (defOf(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        if (defOf(e.getEntity()) != null) e.setCancelled(true);
    }
}
