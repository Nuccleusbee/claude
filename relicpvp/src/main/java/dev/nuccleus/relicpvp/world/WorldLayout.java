package dev.nuccleus.relicpvp.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The map's shape, centred on 0,0:
 * spawn plains (r &lt; spawnRadius), four mega-biome wedges N/E/S/W (r &lt; biomeRadius),
 * then the wasteland ring out to wastelandRadius. Edges are wobbled so they look natural.
 * Pure math, safe to call from world-gen threads.
 */
public final class WorldLayout {

    public enum Region { SPAWN, NORTH, EAST, SOUTH, WEST, WASTELAND }

    public final int spawnRadius;
    public final int biomeRadius;
    public final int wastelandRadius;
    private final Biome spawnBiome;
    private final Biome[] sectorBiomes = new Biome[4];
    private final Biome wastelandBiome;

    public WorldLayout(ConfigurationSection c, Logger log) {
        spawnRadius = c == null ? 120 : c.getInt("spawn-radius", 120);
        biomeRadius = c == null ? 1200 : c.getInt("biome-radius", 1200);
        wastelandRadius = c == null ? 2400 : c.getInt("wasteland-radius", 2400);
        spawnBiome = biome(c, "spawn-biome", "plains", log);
        sectorBiomes[0] = biome(c, "biomes.north", "snowy_taiga", log);
        sectorBiomes[1] = biome(c, "biomes.east", "jungle", log);
        sectorBiomes[2] = biome(c, "biomes.south", "desert", log);
        sectorBiomes[3] = biome(c, "biomes.west", "dark_forest", log);
        wastelandBiome = biome(c, "wasteland-biome", "badlands", log);
    }

    private static Biome biome(ConfigurationSection c, String path, String def, Logger log) {
        String name = c == null ? def : c.getString(path, def);
        Biome b = Registry.BIOME.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        if (b == null) {
            log.warning("Unknown biome '" + name + "' at world-layout." + path + ", using " + def);
            b = Registry.BIOME.get(NamespacedKey.minecraft(def));
        }
        return b;
    }

    public Region regionAt(int x, int z) {
        double dist = Math.sqrt((double) x * x + (double) z * z);
        double angle = Math.atan2(x, -z); // 0 = north (-z), clockwise
        // Wobble the borders.
        double wobble = 30 * Math.sin(angle * 7) + 18 * Math.sin(x / 53.0) * Math.cos(z / 61.0);
        double r = dist + wobble;
        if (r < spawnRadius) return Region.SPAWN;
        if (r >= biomeRadius) return Region.WASTELAND;
        double a = angle + 0.12 * Math.sin(dist / 90.0) + 0.06 * Math.sin(dist / 37.0);
        double turn = (a + Math.PI / 4) / (Math.PI / 2);
        int sector = Math.floorMod((int) Math.floor(turn), 4);
        return switch (sector) {
            case 0 -> Region.NORTH;
            case 1 -> Region.EAST;
            case 2 -> Region.SOUTH;
            default -> Region.WEST;
        };
    }

    public Biome biomeAt(int x, int z) {
        return switch (regionAt(x, z)) {
            case SPAWN -> spawnBiome;
            case NORTH -> sectorBiomes[0];
            case EAST -> sectorBiomes[1];
            case SOUTH -> sectorBiomes[2];
            case WEST -> sectorBiomes[3];
            case WASTELAND -> wastelandBiome;
        };
    }

    public List<Biome> allBiomes() {
        List<Biome> out = new ArrayList<>();
        for (Biome b : new Biome[] {spawnBiome, sectorBiomes[0], sectorBiomes[1], sectorBiomes[2], sectorBiomes[3], wastelandBiome}) {
            if (!out.contains(b)) out.add(b);
        }
        return out;
    }
}
