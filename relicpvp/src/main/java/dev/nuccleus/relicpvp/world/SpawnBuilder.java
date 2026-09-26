package dev.nuccleus.relicpvp.world;

import dev.nuccleus.relicpvp.RelicPvP;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Builds the spawn plaza at 0,0: a round stone-brick plaza with a fountain, four portal arches
 * (one facing each mega biome), four generator pads and lantern posts. Also makes it a safe zone.
 */
public final class SpawnBuilder {

    /** Arch accent colour per direction: north, east, south, west. */
    private static final Material[] ACCENT = {
            Material.LIGHT_BLUE_CONCRETE, Material.LIME_CONCRETE, Material.YELLOW_CONCRETE, Material.PURPLE_CONCRETE,
    };
    private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private final RelicPvP plugin;

    public SpawnBuilder(RelicPvP plugin) {
        this.plugin = plugin;
    }

    /** Builds the plaza and returns the floor Y. */
    public int build(World world, int radius) {
        int floor = Math.max(world.getSeaLevel() + 2, Math.min(world.getHighestBlockYAt(0, 0), world.getSeaLevel() + 30));
        int clearHeight = 30;

        for (int x = -radius - 2; x <= radius + 2; x++) {
            for (int z = -radius - 2; z <= radius + 2; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > radius + 0.5) continue;

                // Foundation down to the ground, air above.
                for (int y = floor - 1; y > floor - 40 && y > world.getMinHeight(); y--) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType().isSolid() && !b.getType().name().endsWith("_LEAVES")) break;
                    b.setType(Material.STONE_BRICKS, false);
                }
                for (int y = floor + 1; y <= floor + clearHeight; y++) set(world, x, y, z, Material.AIR);

                // Floor pattern: rings of andesite in stone bricks, with some wear.
                Material m;
                int ring = (int) Math.round(d);
                if (ring % 6 == 0) m = Material.POLISHED_ANDESITE;
                else {
                    int h = Math.floorMod(x * 734287 + z * 912931, 17);
                    m = h == 0 ? Material.CRACKED_STONE_BRICKS : h == 1 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS;
                }
                set(world, x, floor, z, m);

                // Low wall around the edge, open where the arches are.
                if (d > radius - 0.5 && Math.abs(x) > 3 && Math.abs(z) > 3) {
                    set(world, x, floor + 1, z, Material.STONE_BRICK_WALL);
                }
            }
        }

        fountain(world, floor);
        for (int i = 0; i < 4; i++) arch(world, floor, radius - 4, DIRS[i][0], DIRS[i][1], ACCENT[i]);
        int pad = (int) Math.round(radius * 0.5);
        generatorPad(world, floor, pad, pad);
        generatorPad(world, floor, -pad, pad);
        generatorPad(world, floor, pad, -pad);
        generatorPad(world, floor, -pad, -pad);
        lanterns(world, floor, radius - 2);

        world.setSpawnLocation(new Location(world, 0.5, floor + 1, 6.5, 180f, 0f));
        plugin.zones().create("spawn", new Location(world, -radius - 3, floor, -radius - 3),
                new Location(world, radius + 3, floor, radius + 3), 0, false, false);
        return floor;
    }

    private static void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    private static void fountain(World w, int floor) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d < 3) {
                    set(w, x, floor - 1, z, Material.STONE_BRICKS);
                    set(w, x, floor, z, Material.WATER);
                } else if (d < 4) {
                    set(w, x, floor + 1, z, Material.STONE_BRICK_SLAB);
                }
            }
        }
        for (int y = floor; y <= floor + 2; y++) set(w, 0, y, 0, Material.CHISELED_STONE_BRICKS);
        set(w, 0, floor + 3, 0, Material.SEA_LANTERN);
    }

    /** A 5-wide, 6-tall arch whose 3x4 opening is where you put a /portal. */
    private static void arch(World w, int floor, int dist, int dx, int dz, Material accent) {
        int cx = dx * dist;
        int cz = dz * dist;
        // "across" runs along the arch face.
        int ax = dz != 0 ? 1 : 0;
        int az = dx != 0 ? 1 : 0;
        for (int i = -3; i <= 3; i++) {
            for (int y = floor + 1; y <= floor + 6; y++) {
                int x = cx + ax * i;
                int z = cz + az * i;
                boolean side = Math.abs(i) >= 2;
                boolean top = y >= floor + 5;
                if (Math.abs(i) == 3) {
                    if (y <= floor + 2) set(w, x, y, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                    continue;
                }
                if (side || top) set(w, x, y, z, Material.POLISHED_BLACKSTONE_BRICKS);
                else set(w, x, y, z, Material.AIR);
            }
            set(w, cx + ax * i, floor, cz + az * i, Material.POLISHED_BLACKSTONE);
        }
        set(w, cx, floor + 6, cz, accent);
        set(w, cx, floor + 7, cz, Material.LANTERN);
        // Accent path from the fountain to the arch.
        for (int s = 5; s < dist; s++) set(w, dx * s, floor, dz * s, terracotta(accent));
    }

    private static Material terracotta(Material concrete) {
        return switch (concrete) {
            case LIGHT_BLUE_CONCRETE -> Material.LIGHT_BLUE_TERRACOTTA;
            case LIME_CONCRETE -> Material.LIME_TERRACOTTA;
            case YELLOW_CONCRETE -> Material.YELLOW_TERRACOTTA;
            default -> Material.PURPLE_TERRACOTTA;
        };
    }

    /** A 5x5 dark pad with gold corners: a place to put /generator blocks. */
    private static void generatorPad(World w, int floor, int cx, int cz) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
                set(w, cx + x, floor, cz + z, corner ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE);
            }
        }
    }

    private static void lanterns(World w, int floor, int dist) {
        for (int deg = 22; deg < 360; deg += 45) {
            double rad = Math.toRadians(deg);
            int x = (int) Math.round(Math.sin(rad) * dist);
            int z = (int) Math.round(-Math.cos(rad) * dist);
            set(w, x, floor + 1, z, Material.DARK_OAK_FENCE);
            set(w, x, floor + 2, z, Material.DARK_OAK_FENCE);
            set(w, x, floor + 3, z, Material.LANTERN);
        }
    }
}
