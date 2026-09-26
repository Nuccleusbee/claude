package dev.nuccleus.relicpvp.world;

import java.util.List;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/** Vanilla terrain, caves and structures, but with our biome layout and a scorched wasteland. */
public final class RelicChunkGenerator extends ChunkGenerator {

    private final WorldLayout layout;

    public RelicChunkGenerator(WorldLayout layout) {
        this.layout = layout;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                return layout.biomeAt(x, z);
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
                return layout.allBiomes();
            }
        };
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(new WastelandPopulator(layout));
    }

    @Override public boolean shouldGenerateNoise() { return true; }
    @Override public boolean shouldGenerateSurface() { return true; }
    @Override public boolean shouldGenerateCaves() { return true; }
    @Override public boolean shouldGenerateDecorations() { return true; }
    @Override public boolean shouldGenerateMobs() { return true; }
    @Override public boolean shouldGenerateStructures() { return true; }
}
