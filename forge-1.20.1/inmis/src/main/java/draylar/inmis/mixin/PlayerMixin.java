package draylar.inmis.mixin;

import draylar.inmis.augment.BackpackAugmentHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "getProjectile", at = @At("RETURN"), cancellable = true)
    private void inmis$checkBackpackAmmo(ItemStack weapon, CallbackInfoReturnable<ItemStack> cir) {
        Player player = (Player) (Object) this;
        ItemStack fromBackpack = BackpackAugmentHandler.locateAmmunition(player, weapon, cir.getReturnValue());
        if (!fromBackpack.isEmpty()) {
            cir.setReturnValue(fromBackpack);
        }
    }
}
