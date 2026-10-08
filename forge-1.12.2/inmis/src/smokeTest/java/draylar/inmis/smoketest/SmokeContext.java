package draylar.inmis.smoketest;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** The small subset of GameTest's helper needed by synchronous native assertions. */
public final class SmokeContext {
    private final WorldServer level;
    private boolean succeeded;

    public SmokeContext(WorldServer level) {
        this.level = level;
        level.getChunkFromChunkCoords(0, 0);
    }
    public WorldServer getLevel() { return level; }
    public BlockPos absolutePos(BlockPos relative) { return relative.add(0, 64, 0); }
    public void fail(String message) { throw new AssertionError(message); }
    public void succeed() { succeeded = true; }
    public boolean hasSucceeded() { return succeeded; }
    public void killAllEntities() {
        for (EntityItem item : level.getEntitiesWithinAABB(EntityItem.class,
                new AxisAlignedBB(-32, 0, -32, 32, 256, 32))) item.setDead();
    }
}
