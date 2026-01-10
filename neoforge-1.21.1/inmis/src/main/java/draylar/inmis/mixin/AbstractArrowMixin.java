package draylar.inmis.mixin;

import draylar.inmis.augment.BackpackAugmentHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {

    @Shadow
    public AbstractArrow.Pickup pickup;

    @Inject(method = "tryPickup", at = @At("HEAD"), cancellable = true)
    private void inmis$addArrowToBackpack(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (this.pickup == AbstractArrow.Pickup.ALLOWED) {
            if (BackpackAugmentHandler.beforeArrowPickup(player, (AbstractArrow) (Object) this)) {
                cir.setReturnValue(true);
            }
        }
    }
}
