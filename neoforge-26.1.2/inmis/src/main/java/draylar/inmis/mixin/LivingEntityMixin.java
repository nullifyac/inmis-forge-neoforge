package draylar.inmis.mixin;

import draylar.inmis.augment.BackpackAugmentHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Redirect(method = "checkTotemDeathProtection", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack inmis$checkBackpackForTotem(LivingEntity entity, InteractionHand hand) {
        ItemStack original = entity.getItemInHand(hand);
        if (hand == InteractionHand.OFF_HAND && !original.has(net.minecraft.core.component.DataComponents.DEATH_PROTECTION) && entity instanceof Player player) {
            ItemStack fromBackpack = BackpackAugmentHandler.locateTotemOfUndying(player);
            if (!fromBackpack.isEmpty()) {
                return fromBackpack;
            }
        }
        return original;
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void inmis$finalizeBackpackTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player) {
            BackpackAugmentHandler.finishTotemCheck(cir.getReturnValue());
        }
    }
}
