package dev.nuccleus.relicpvp.world;

import java.util.Random;
import org.bukkit.Material;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

/** Paints the wasteland ring: ash-coloured ground, dead bushes, bones and ruined pillars. */
public final class WastelandPopulator extends BlockPopulator {

    private static final Material[] GROUND = {
            Material.COARSE_DIRT, Material.COARSE_DIRT, Material.COARSE_DIRT, Material.GRAVEL, Material.GRAVEL,
            Material.SOUL_SOIL, Material.SOUL_SOIL, Material.BLACKSTONE, Material.DIRT, Material.PACKED_MUD,
    };
    private static final Material[] RUIN = {
            Material.CRACKED_STONE_BRICKS, Material.MOSSY_COBBLESTONE, Material.COBBLESTONE, Material.CRACKED_STONE_BRICKS,
            Material.BLACKSTONE, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS,
    };

    private final WorldLayout layout;

    public WastelandPopulator(WorldLayout layout) {
        this.layout = layout;
    }

    @Override
    public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        // Quick reject: nowhere near the wasteland.
        double centre = Math.sqrt(Math.pow(baseX + 8, 2) + Math.pow(baseZ + 8, 2));
        if (centre < layout.biomeRadius - 80) return;

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = baseX + dx;
                int z = baseZ + dz;
                if (layout.regionAt(x, z) != WorldLayout.Region.WASTELAND) continue;
                int y = region.getHighestBlockYAt(x, z);
                if (y <= info.getMinHeight() || y >= info.getMaxHeight() - 2) continue;

                // Strip plants/trees sitting on top.
                Material top = region.getType(x, y, z);
                while (isPlant(top) && y > info.getMinHeight() + 1) {
                    region.setType(x, y, z, Material.AIR);
                    y--;
                    top = region.getType(x, y, z);
                }
                if (!isGround(top)) continue;

                Material ground = GROUND[random.nextInt(GROUND.length)];
                region.setType(x, y, z, ground);
                if (random.nextInt(4) == 0) region.setType(x, y - 1, z, Material.COARSE_DIRT);

                int roll = random.nextInt(1000);
                if (roll < 25 && ground != Material.GRAVEL) {
                    region.setType(x, y + 1, z, Material.DEAD_BUSH);
                } else if (roll < 28) {
                    region.setType(x, y + 1, z, Material.BONE_BLOCK);
                } else if (roll < 32 && ground == Material.SOUL_SOIL) {
                    region.setType(x, y + 1, z, Material.SOUL_FIRE);
                }
            }
        }

        // Occasional ruined pillar near the middle of the chunk.
        if (random.nextInt(40) == 0) {
            int x = baseX + 4 + random.nextInt(8);
            int z = baseZ + 4 + random.nextInt(8);
            if (layout.regionAt(x, z) == WorldLayout.Region.WASTELAND) {
                int y = region.getHighestBlockYAt(x, z);
                if (y > info.getMinHeight() && y < info.getMaxHeight() - 12 && isGround(region.getType(x, y, z))) {
                    int height = 3 + random.nextInt(6);
                    for (int i = 1; i <= height; i++) region.setType(x, y + i, z, RUIN[random.nextInt(RUIN.length)]);
                }
            }
        }
    }

    private static boolean isPlant(Material m) {
        String n = m.name();
        return n.endsWith("_LEAVES") || n.endsWith("_LOG") || n.endsWith("_WOOD") || n.equals("CACTUS")
                || n.equals("SHORT_GRASS") || n.equals("TALL_GRASS") || n.equals("FERN") || n.equals("LARGE_FERN")
                || n.equals("DEAD_BUSH") || n.equals("SNOW") || n.equals("SUGAR_CANE") || n.equals("VINE");
    }

    private static boolean isGround(Material m) {
        String n = m.name();
        return n.endsWith("TERRACOTTA") || n.endsWith("SAND") || n.equals("GRASS_BLOCK") || n.equals("DIRT")
                || n.equals("COARSE_DIRT") || n.equals("PODZOL") || n.equals("STONE") || n.equals("GRAVEL")
                || n.equals("MYCELIUM") || n.equals("SNOW_BLOCK") || n.equals("SANDSTONE") || n.equals("MUD");
    }
}
