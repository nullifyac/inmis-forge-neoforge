package draylar.inmis.mixin;

import draylar.inmis.augment.BackpackAugmentHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(Block.class)
public abstract class BlockMixin {

    @Inject(method = "dropResources(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void inmis$lootboundDrops(BlockState state, World level, BlockPos pos, TileEntity blockEntity,
                                             Entity entity, ItemStack tool, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) entity;

        if (!BackpackAugmentHandler.hasLootboundBackpacks(player)) {
            return;
        }
        if (!(level instanceof ServerWorld)) {
            return;
        }
        ServerWorld serverLevel = (ServerWorld) level;

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

        state.spawnAfterBreak(serverLevel, pos, tool);
        ci.cancel();
    }
}
