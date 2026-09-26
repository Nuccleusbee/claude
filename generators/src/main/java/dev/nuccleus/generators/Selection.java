package dev.nuccleus.generators;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.block.Block;

/** Each admin's two selected corners, set with the wand or /generator pos1|pos2. */
public final class Selection {

    private final Map<UUID, Block> pos1 = new HashMap<>();
    private final Map<UUID, Block> pos2 = new HashMap<>();

    public void set(UUID player, boolean first, Block block) {
        (first ? pos1 : pos2).put(player, block);
    }

    public Block pos1(UUID player) { return pos1.get(player); }
    public Block pos2(UUID player) { return pos2.get(player); }

    /** Both corners set and in the same world. */
    public boolean complete(UUID player) {
        Block a = pos1.get(player);
        Block b = pos2.get(player);
        return a != null && b != null && a.getWorld() == b.getWorld();
    }

    public long volume(UUID player) {
        Block a = pos1.get(player);
        Block b = pos2.get(player);
        if (a == null || b == null) return 0;
        return (long) (Math.abs(a.getX() - b.getX()) + 1) * (Math.abs(a.getY() - b.getY()) + 1) * (Math.abs(a.getZ() - b.getZ()) + 1);
    }
}
