package dev.nuccleus.relicpvp.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

public final class Entities {

    private Entities() {}

    /** The player behind a hit, including arrows/tridents/etc. they shot. */
    public static Player playerSource(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    public static LivingEntity livingSource(Entity damager) {
        if (damager instanceof LivingEntity l) return l;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof LivingEntity l) return l;
        return null;
    }

    /**
     * Looks an attribute up by key, e.g. "max_health". Works on 1.21.1 ("generic.max_health")
     * and 1.21.3+ ("max_health") where the keys were renamed.
     */
    public static AttributeInstance attribute(LivingEntity entity, String key) {
        Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        if (attr == null) attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + key));
        return attr == null ? null : entity.getAttribute(attr);
    }
}
