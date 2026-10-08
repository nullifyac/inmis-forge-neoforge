/*
 * Backpacked-derived portions by MrCrayfish and contributors (GNU LGPL v2.1).
 * Source: https://github.com/MrCrayfish/Backpacked/blob/73db4d7cd729ebcd05b4cab8d7c0f7be9eeaa19a/common/src/main/java/com/mrcrayfish/backpacked/common/UseItemOnBlockFaceContext.java
 * Adapted for Inmis packages and Minecraft/loader APIs.
 * Attribution notice added 2026-10-08; see THIRD-PARTY-NOTICES.txt and
 * LICENSES/Backpacked-LGPL-2.1.txt in the main resources.
 */
package draylar.inmis.augment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class UseItemOnBlockFaceContext extends UseOnContext {
    private UseItemOnBlockFaceContext(Level level, ItemStack stack, BlockHitResult result) {
        super(level, null, InteractionHand.MAIN_HAND, stack, result);
    }

    private UseItemOnBlockFaceContext(Level level, ServerPlayer player, ItemStack stack, BlockHitResult result) {
        super(level, player, InteractionHand.MAIN_HAND, stack, result);
    }

    public static UseItemOnBlockFaceContext create(ServerLevel level, ItemStack stack, BlockPos pos, Direction face) {
        Vec3 hit = pos.getCenter().add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        BlockHitResult result = new BlockHitResult(hit, face, pos, false);
        return new UseItemOnBlockFaceContext(level, stack, result);
    }

    public static UseItemOnBlockFaceContext create(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos pos, Direction face) {
        Vec3 hit = pos.getCenter().add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        BlockHitResult result = new BlockHitResult(hit, face, pos, false);
        return new UseItemOnBlockFaceContext(level, player, stack, result);
    }
}
