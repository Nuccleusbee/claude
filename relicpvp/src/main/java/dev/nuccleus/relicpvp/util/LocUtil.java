package dev.nuccleus.relicpvp.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class LocUtil {

    private LocUtil() {}

    public static String serialize(Location l) {
        return l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
    }

    /** Returns null if the string is malformed or the world isn't loaded. */
    public static Location parse(String s) {
        if (s == null) return null;
        String[] p = s.split(";");
        if (p.length < 4) return null;
        World world = Bukkit.getWorld(p[0]);
        if (world == null) return null;
        try {
            float yaw = p.length > 4 ? Float.parseFloat(p[4]) : 0f;
            float pitch = p.length > 5 ? Float.parseFloat(p[5]) : 0f;
            return new Location(world, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String pretty(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }

    public static boolean isChunkLoaded(Location l) {
        return l.getWorld() != null && l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4);
    }
}
