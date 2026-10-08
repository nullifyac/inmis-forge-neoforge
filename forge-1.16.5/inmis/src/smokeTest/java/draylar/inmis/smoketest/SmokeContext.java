package draylar.inmis.smoketest;

import net.minecraft.entity.item.ItemEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

/** The small subset of GameTest's helper needed by synchronous native assertions. */
public final class SmokeContext {
    private final ServerWorld level;
    private boolean succeeded;

    public SmokeContext(ServerWorld level) {
        this.level = level;
        level.getChunk(0, 0);
    }
    public ServerWorld getLevel() { return level; }
    public BlockPos absolutePos(BlockPos relative) { return relative.offset(0, 64, 0); }
    public void fail(String message) { throw new AssertionError(message); }
    public void succeed() { succeeded = true; }
    public boolean hasSucceeded() { return succeeded; }
    public void killAllEntities() {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
                new AxisAlignedBB(-32, 0, -32, 32, 256, 32))) item.remove();
    }
}
