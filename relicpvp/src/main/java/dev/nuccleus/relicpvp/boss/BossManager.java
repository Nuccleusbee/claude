package dev.nuccleus.relicpvp.boss;

import dev.nuccleus.relicpvp.RelicPvP;
import dev.nuccleus.relicpvp.util.Entities;
import dev.nuccleus.relicpvp.util.LocUtil;
import dev.nuccleus.relicpvp.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/** Config-defined bosses that guard a spawn point, drop level keys and respawn on a timer. */
public final class BossManager implements Listener {

    record Drop(String item, int amount, double chance) {}
    record Shockwave(int everySeconds, double radius, double damage) {}
    record Minions(int everySeconds, EntityType type, int count) {}

    static final class BossDef {
        String id;
        String name;
        EntityType type;
        double health;
        double damageMultiplier;
        int keyTier;
        int respawnSeconds;
        double leashRadius;
        int exp;
        boolean announceSpawn;
        BossBar.Color barColor;
        final Map<EquipmentSlot, String> equipment = new EnumMap<>(EquipmentSlot.class);
        final List<Drop> drops = new ArrayList<>();
        Shockwave shockwave;
        Minions minions;
    }

    static final class State {
        Location spawn;
        LivingEntity entity;
        long nextSpawnAt;
        int secondsAlive;
        BossBar bar;
        final Map<UUID, Double> damage = new HashMap<>();
        final Set<UUID> viewers = new HashSet<>();
    }

    private final RelicPvP plugin;
    private final NamespacedKey bossKey;
    private final NamespacedKey minionKey;
    private final Map<String, BossDef> defs = new LinkedHashMap<>();
    private final Map<String, State> states = new HashMap<>();

    public BossManager(RelicPvP plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "boss_id");
        this.minionKey = new NamespacedKey(plugin, "boss_minion");
    }

    // ------------------------------------------------------------------ loading

    public void loadConfig() {
        shutdown();
        defs.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("bosses");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection c = section.getConfigurationSection(id);
            if (c == null) continue;
            BossDef d = new BossDef();
            d.id = id.toLowerCase(Locale.ROOT);
            d.name = c.getString("name", id);
            d.type = entityType(c.getString("entity", "ZOMBIE"));
            if (d.type == null || !d.type.isAlive() || !d.type.isSpawnable()) {
                plugin.getLogger().warning("Boss '" + id + "' has an invalid entity type, skipping");
                continue;
            }
            d.health = Math.max(1, c.getDouble("health", 200));
            d.damageMultiplier = c.getDouble("damage-multiplier", 1.0);
            d.keyTier = c.getInt("key-tier", 0);
            d.respawnSeconds = c.getInt("respawn-seconds", 300);
            d.leashRadius = c.getDouble("leash-radius", 30);
            d.exp = c.getInt("exp", 100);
            d.announceSpawn = c.getBoolean("announce-spawn", true);
            try {
                d.barColor = BossBar.Color.valueOf(c.getString("bar-color", "RED").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                d.barColor = BossBar.Color.RED;
            }

            ConfigurationSection eq = c.getConfigurationSection("equipment");
            if (eq != null) {
                for (String slotName : eq.getKeys(false)) {
                    EquipmentSlot slot = slot(slotName);
                    if (slot == null) {
                        plugin.getLogger().warning("Boss '" + id + "' has unknown equipment slot '" + slotName + "'");
                        continue;
                    }
                    d.equipment.put(slot, eq.getString(slotName));
                }
            }
            ConfigurationSection drops = c.getConfigurationSection("drops");
            if (drops != null) {
                for (String key : drops.getKeys(false)) {
                    ConfigurationSection dr = drops.getConfigurationSection(key);
                    if (dr == null) continue;
                    d.drops.add(new Drop(dr.getString("item", "STONE"), dr.getInt("amount", 1), dr.getDouble("chance", 1.0)));
                }
            }
            ConfigurationSection sw = c.getConfigurationSection("abilities.shockwave");
            if (sw != null) {
                d.shockwave = new Shockwave(Math.max(1, sw.getInt("every-seconds", 15)), sw.getDouble("radius", 5), sw.getDouble("damage", 4));
            }
            ConfigurationSection mn = c.getConfigurationSection("abilities.minions");
            if (mn != null) {
                EntityType mt = entityType(mn.getString("entity", "ZOMBIE"));
                if (mt != null && mt.isAlive() && mt.isSpawnable()) {
                    d.minions = new Minions(Math.max(1, mn.getInt("every-seconds", 25)), mt, Math.max(1, mn.getInt("count", 2)));
                }
            }
            defs.put(d.id, d);
            states.computeIfAbsent(d.id, k -> new State());
        }
        states.keySet().retainAll(defs.keySet());
        loadData();
    }

    public void loadData() {
        ConfigurationSection section = plugin.data().yaml().getConfigurationSection("boss-spawns");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            State st = states.get(id);
            if (st != null) st.spawn = LocUtil.parse(section.getString(id));
        }
    }

    private static EntityType entityType(String name) {
        try {
            return EntityType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private static EquipmentSlot slot(String name) {
        return switch (name.toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "helmet", "head" -> EquipmentSlot.HEAD;
            case "chestplate", "chest" -> EquipmentSlot.CHEST;
            case "leggings", "legs" -> EquipmentSlot.LEGS;
            case "boots", "feet" -> EquipmentSlot.FEET;
            case "main-hand", "hand" -> EquipmentSlot.HAND;
            case "off-hand" -> EquipmentSlot.OFF_HAND;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ ticking

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (BossDef def : defs.values()) {
            State st = states.get(def.id);
            if (st.entity != null && !st.entity.isValid()) {
                // Unloaded or removed without dying: bring it back as soon as someone is near.
                clearAlive(st);
            }
            if (st.entity == null) {
                if (st.spawn != null && now >= st.nextSpawnAt && playersNear(st.spawn, 64)) spawn(def, st.spawn);
                continue;
            }
            st.secondsAlive++;
            leash(def, st);
            updateBar(st);
            if (def.shockwave != null && st.secondsAlive % def.shockwave.everySeconds() == 0) shockwave(def, st.entity);
            if (def.minions != null && st.secondsAlive % def.minions.everySeconds() == 0) summonMinions(def, st.entity);
        }
    }

    private static boolean playersNear(Location loc, double range) {
        return LocUtil.isChunkLoaded(loc) && !loc.getWorld().getNearbyPlayers(loc, range).isEmpty();
    }

    private void leash(BossDef def, State st) {
        if (def.leashRadius <= 0 || st.spawn == null) return;
        Location at = st.entity.getLocation();
        if (at.getWorld() == st.spawn.getWorld() && at.distanceSquared(st.spawn) <= def.leashRadius * def.leashRadius) return;
        st.entity.teleport(st.spawn);
        if (st.entity instanceof Mob mob) mob.setTarget(null);
    }

    private void updateBar(State st) {
        double max = maxHealth(st.entity);
        st.bar.progress((float) Math.max(0, Math.min(1, st.entity.getHealth() / max)));

        int range = plugin.getConfig().getInt("boss-bar-range", 48);
        Set<UUID> inRange = new HashSet<>();
        for (Player p : st.entity.getWorld().getNearbyPlayers(st.entity.getLocation(), range)) {
            inRange.add(p.getUniqueId());
            if (st.viewers.add(p.getUniqueId())) p.showBossBar(st.bar);
        }
        for (Iterator<UUID> it = st.viewers.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (inRange.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(st.bar);
            it.remove();
        }
    }

    private static double maxHealth(LivingEntity e) {
        AttributeInstance attr = Entities.attribute(e, "max_health");
        return attr == null ? Math.max(1, e.getHealth()) : attr.getValue();
    }

    private void shockwave(BossDef def, LivingEntity boss) {
        Location c = boss.getLocation();
        World w = c.getWorld();
        w.spawnParticle(Particle.EXPLOSION, c, 4, 1.0, 0.3, 1.0);
        w.playSound(c, "entity.generic.explode", 1.5f, 0.7f);
        for (Player p : w.getNearbyPlayers(c, def.shockwave.radius())) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
            p.damage(def.shockwave.damage());
            Vector away = p.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = new Vector(0.1, 0, 0);
            p.setVelocity(away.normalize().multiply(1.2).setY(0.6));
        }
    }

    private void summonMinions(BossDef def, LivingEntity boss) {
        Location c = boss.getLocation();
        long existing = boss.getWorld().getNearbyEntities(c, 24, 12, 24).stream()
                .filter(e -> e.getPersistentDataContainer().has(minionKey, PersistentDataType.BYTE))
                .count();
        if (existing >= def.minions.count() * 2L) return;
        Player target = boss instanceof Mob mob && mob.getTarget() instanceof Player p ? p : null;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < def.minions.count(); i++) {
            Location at = c.clone().add(rnd.nextDouble(-3, 3), 0, rnd.nextDouble(-3, 3));
            Entity e = c.getWorld().spawnEntity(at, def.minions.type());
            e.getPersistentDataContainer().set(minionKey, PersistentDataType.BYTE, (byte) 1);
            e.setPersistent(false);
            if (e instanceof Mob mob && target != null) mob.setTarget(target);
        }
        c.getWorld().spawnParticle(Particle.LARGE_SMOKE, c, 30, 2, 1, 2, 0.02);
    }

    // ------------------------------------------------------------------ spawning

    /** Spawns the boss at {@code loc}. Returns null if it's already alive or unknown. */
    public LivingEntity spawn(String id, Location loc) {
        BossDef def = defs.get(id.toLowerCase(Locale.ROOT));
        if (def == null) return null;
        State st = states.get(def.id);
        if (st.entity != null && st.entity.isValid()) return null;
        return spawn(def, loc);
    }

    private LivingEntity spawn(BossDef def, Location loc) {
        State st = states.get(def.id);
        Entity raw = loc.getWorld().spawnEntity(loc, def.type);
        if (!(raw instanceof LivingEntity boss)) {
            raw.remove();
            return null;
        }
        boss.customName(Text.mm(def.name));
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(false);
        boss.setCanPickupItems(false);
        if (boss instanceof Ageable ageable) ageable.setAdult();

        AttributeInstance hp = Entities.attribute(boss, "max_health");
        if (hp != null) hp.setBaseValue(def.health);
        boss.setHealth(Math.min(def.health, maxHealth(boss)));

        EntityEquipment eq = boss.getEquipment();
        if (eq != null) {
            eq.clear();
            def.equipment.forEach((slot, ref) -> {
                ItemStack item = plugin.items().create(ref, 1);
                if (item == null) return;
                eq.setItem(slot, item);
                eq.setDropChance(slot, 0f);
            });
        }
        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, def.id);

        st.entity = boss;
        st.secondsAlive = 0;
        st.damage.clear();
        st.bar = BossBar.bossBar(Text.mm(def.name), 1f, def.barColor, BossBar.Overlay.PROGRESS);
        if (def.announceSpawn) plugin.broadcast(def.name + " <gray>has awoken!");
        loc.getWorld().playSound(loc, "entity.wither.spawn", 1f, 1f);
        return boss;
    }

    private void clearAlive(State st) {
        if (st.bar != null) {
            for (UUID id : st.viewers) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.hideBossBar(st.bar);
            }
        }
        st.viewers.clear();
        st.entity = null;
        st.damage.clear();
    }

    /** Removes live bosses (on reload / shutdown). They come back when a player is near. */
    public void shutdown() {
        for (State st : states.values()) {
            if (st.entity != null && st.entity.isValid()) st.entity.remove();
            clearAlive(st);
            st.nextSpawnAt = 0;
        }
    }

    // ------------------------------------------------------------------ events

    private String bossId(Entity e) {
        return e.getPersistentDataContainer().get(bossKey, PersistentDataType.STRING);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBossHits(EntityDamageByEntityEvent e) {
        LivingEntity source = Entities.livingSource(e.getDamager());
        if (source == null) return;
        String id = bossId(source);
        BossDef def = id == null ? null : defs.get(id);
        if (def != null) e.setDamage(e.getDamage() * def.damageMultiplier);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBossHurt(EntityDamageByEntityEvent e) {
        String id = bossId(e.getEntity());
        if (id == null) return;
        State st = states.get(id);
        Player p = Entities.playerSource(e.getDamager());
        if (st != null && p != null) st.damage.merge(p.getUniqueId(), e.getFinalDamage(), Double::sum);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead.getPersistentDataContainer().has(minionKey, PersistentDataType.BYTE)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            return;
        }
        String id = bossId(dead);
        if (id == null) return;
        BossDef def = defs.get(id);
        State st = states.get(id);
        if (def == null || st == null) return;

        e.getDrops().clear();
        e.setDroppedExp(def.exp);
        Location at = dead.getLocation();

        Player top = null;
        double best = 0;
        for (Map.Entry<UUID, Double> entry : st.damage.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && entry.getValue() > best) {
                best = entry.getValue();
                top = p;
            }
        }
        if (top == null) top = dead.getKiller();

        if (def.keyTier > 0) {
            ItemStack key = plugin.items().key(def.keyTier);
            if (top != null && plugin.getConfig().getBoolean("keys.give-to-top-damager", true)) {
                plugin.items().give(top, key);
                plugin.msg(top, "<gold>You got the <bold>Level " + def.keyTier + " Key</bold>! Right-click it to unlock.");
            } else {
                at.getWorld().dropItemNaturally(at, key);
            }
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Drop drop : def.drops) {
            if (rnd.nextDouble() < drop.chance()) e.getDrops().addAll(plugin.items().createStacks(drop.item(), drop.amount()));
        }

        String who = top != null ? "<yellow>" + top.getName() + "</yellow>" : "<gray>Someone</gray>";
        plugin.broadcast(who + " <gray>has slain</gray> " + def.name + "<gray>!");
        clearAlive(st);
        st.nextSpawnAt = System.currentTimeMillis() + def.respawnSeconds * 1000L;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        if (bossId(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        if (bossId(e.getEntity()) != null) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ commands

    public Collection<String> ids() {
        return defs.keySet();
    }

    public boolean exists(String id) {
        return defs.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public void setSpawn(String id, Location loc) {
        id = id.toLowerCase(Locale.ROOT);
        State st = states.get(id);
        if (st == null) return;
        st.spawn = loc;
        plugin.data().yaml().set("boss-spawns." + id, loc == null ? null : LocUtil.serialize(loc));
        plugin.data().save();
    }

    /** Where this boss lives, or null if no spawn is set. */
    public Location spawnOf(String id) {
        State st = states.get(id.toLowerCase(Locale.ROOT));
        return st == null ? null : st.spawn;
    }

    public boolean kill(String id) {
        State st = states.get(id.toLowerCase(Locale.ROOT));
        if (st == null || st.entity == null || !st.entity.isValid()) return false;
        st.entity.remove();
        clearAlive(st);
        return true;
    }

    /** One line per boss for /relic boss list. */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (BossDef def : defs.values()) {
            State st = states.get(def.id);
            String status;
            if (st.entity != null && st.entity.isValid()) {
                status = "<green>alive (" + (int) st.entity.getHealth() + " hp)";
            } else if (st.spawn == null) {
                status = "<red>no spawn set";
            } else if (now < st.nextSpawnAt) {
                status = "<yellow>respawns in " + ((st.nextSpawnAt - now) / 1000) + "s";
            } else {
                status = "<gray>waiting for a player nearby";
            }
            String keyInfo = def.keyTier > 0 ? " <gold>[Key " + def.keyTier + "]" : "";
            out.add("<white>" + def.id + keyInfo + " <dark_gray>- " + status);
        }
        return out;
    }

    /** The display name of the boss that drops this key tier, or null. */
    public String bossForKey(int tier) {
        for (BossDef def : defs.values()) {
            if (def.keyTier == tier) return def.name;
        }
        return null;
    }
}
