package draylar.inmis.augment;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;

public class UseItemOnBlockFaceContext extends ItemUseContext {
    private UseItemOnBlockFaceContext(World level, ItemStack stack, BlockRayTraceResult result) {
        super(level, null, Hand.MAIN_HAND, stack, result);
    }

    private UseItemOnBlockFaceContext(World level, ServerPlayerEntity player, ItemStack stack, BlockRayTraceResult result) {
        super(level, player, Hand.MAIN_HAND, stack, result);
    }

    public static UseItemOnBlockFaceContext create(ServerWorld level, ItemStack stack, BlockPos pos, Direction face) {
        Vector3d hit = Vector3d.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        BlockRayTraceResult result = new BlockRayTraceResult(hit, face, pos, false);
        return new UseItemOnBlockFaceContext(level, stack, result);
    }

    public static UseItemOnBlockFaceContext create(ServerWorld level, ServerPlayerEntity player, ItemStack stack, BlockPos pos, Direction face) {
        Vector3d hit = Vector3d.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        BlockRayTraceResult result = new BlockRayTraceResult(hit, face, pos, false);
        return new UseItemOnBlockFaceContext(level, player, stack, result);
    }
}
