package draylar.inmis.mixin;

import draylar.inmis.augment.BackpackAugmentHandler;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Redirect(method = "checkTotemDeathProtection", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getItemInHand(Lnet/minecraft/util/Hand;)Lnet/minecraft/item/ItemStack;"))
    private ItemStack inmis$checkBackpackForTotem(LivingEntity entity, Hand hand) {
        ItemStack original = entity.getItemInHand(hand);
        if (hand == Hand.OFF_HAND && original.getItem() != Items.TOTEM_OF_UNDYING && entity instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) entity;
            ItemStack fromBackpack = BackpackAugmentHandler.locateTotemOfUndying(player);
            if (!fromBackpack.isEmpty()) {
                return fromBackpack;
            }
        }
        return original;
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void inmis$finalizeBackpackTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PlayerEntity) {
            BackpackAugmentHandler.finishTotemCheck(cir.getReturnValue());
        }
    }
}
