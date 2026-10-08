package draylar.inmis.mixin;

import draylar.inmis.augment.BackpackAugmentHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BowItem.class)
public abstract class BowItemMixin {

    @Inject(method = "releaseUsing", at = @At("TAIL"))
    private void inmis$syncBackpackAmmo(ItemStack stack, World level, LivingEntity user, int remainingUseTicks,
                                        CallbackInfo ci) {
        BackpackAugmentHandler.finishQuiverlinkUse();
    }
}
