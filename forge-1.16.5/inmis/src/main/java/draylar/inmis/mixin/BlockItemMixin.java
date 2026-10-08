package draylar.inmis.mixin;

import draylar.inmis.augment.PlaceSoundControls;
import net.minecraft.util.ActionResultType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BlockItemUseContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;playSound(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/SoundEvent;Lnet/minecraft/util/SoundCategory;FF)V",
            ordinal = 0))
    private void inmis$aboutToPlayPlaceSound(BlockItemUseContext context, CallbackInfoReturnable<ActionResultType> cir) {
        PlaceSoundControls.markAboutToPlay();
    }
}
