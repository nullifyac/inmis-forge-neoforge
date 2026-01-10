package draylar.inmis.mixin;

import draylar.inmis.augment.BackpackAugmentHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {

    @Inject(method = "tryLoadProjectiles", at = @At("RETURN"))
    private static void inmis$syncBackpackAmmo(LivingEntity shooter, ItemStack crossbow,
                                               CallbackInfoReturnable<Boolean> cir) {
        BackpackAugmentHandler.finishQuiverlinkUse();
    }
}
