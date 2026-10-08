package draylar.inmis.mixin;

import draylar.inmis.augment.BackpackAugmentHandler;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityLivingBase.class)
public class LivingEntityMixin {

    @Redirect(method = "checkTotemDeathProtection", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;getHeldItem(Lnet/minecraft/util/EnumHand;)Lnet/minecraft/item/ItemStack;"))
    private ItemStack inmis$checkBackpackForTotem(EntityLivingBase entity, EnumHand hand) {
        ItemStack original = entity.getHeldItem(hand);
        if (hand == EnumHand.OFF_HAND && original.getItem() != Items.TOTEM_OF_UNDYING && entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            ItemStack fromBackpack = BackpackAugmentHandler.locateTotemOfUndying(player);
            if (!fromBackpack.isEmpty()) {
                return fromBackpack;
            }
        }
        return original;
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void inmis$finalizeBackpackTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof EntityPlayer) {
            BackpackAugmentHandler.finishTotemCheck(cir.getReturnValue());
        }
    }
}
