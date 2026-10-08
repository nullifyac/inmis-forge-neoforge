/*
 * Backpacked-derived portions by MrCrayfish and contributors (GNU LGPL v2.1).
 * Source: https://github.com/MrCrayfish/Backpacked/blob/73db4d7cd729ebcd05b4cab8d7c0f7be9eeaa19a/common/src/main/java/com/mrcrayfish/backpacked/mixin/common/BlockItemMixin.java
 * Adapted for Inmis packages and Minecraft/loader APIs.
 * Attribution notice added 2026-10-08; see THIRD-PARTY-NOTICES.txt and
 * LICENSES/Backpacked-LGPL-2.1.txt in the main resources.
 */
package draylar.inmis.mixin;

import draylar.inmis.augment.PlaceSoundControls;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
            ordinal = 0))
    private void inmis$aboutToPlayPlaceSound(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        PlaceSoundControls.markAboutToPlay();
    }
}
