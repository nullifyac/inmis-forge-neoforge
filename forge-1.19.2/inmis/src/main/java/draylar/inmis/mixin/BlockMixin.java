package draylar.inmis.mixin;

import draylar.inmis.augment.BackpackAugmentHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(Block.class)
public abstract class BlockMixin {

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void inmis$lootboundDrops(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity,
                                             Entity entity, ItemStack tool, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        BackpackAugmentHandler.onBlockBroken(player, state, pos);
        if (!BackpackAugmentHandler.hasLootboundBackpacks(player)) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, blockEntity, entity, tool);
        if (drops.isEmpty()) {
            return;
        }

        List<ItemEntity> entities = new ArrayList<>();
        for (ItemStack stack : drops) {
            if (!stack.isEmpty()) {
                entities.add(new ItemEntity(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
            }
        }

        BackpackAugmentHandler.onLootDroppedByBlock(entities, player);
        for (ItemEntity itemEntity : entities) {
            serverLevel.addFreshEntity(itemEntity);
        }

        state.spawnAfterBreak(serverLevel, pos, tool, false);
        ci.cancel();
    }
}
