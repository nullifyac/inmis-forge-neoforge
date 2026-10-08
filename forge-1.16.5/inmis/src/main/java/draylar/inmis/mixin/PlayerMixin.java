package draylar.inmis.mixin;

import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackAugmentHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerMixin {

    @Inject(method = "dropEquipment", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerInventory;dropAll()V"))
    private void inmis$spillBackpacksBeforeInventoryDrops(CallbackInfo ci) {
        // Vanilla already checked keepInventory and removed vanishing-cursed items at this point.
        Inmis.spillVanillaInventoryOnDeath((PlayerEntity) (Object) this);
    }

    @Inject(method = "getProjectile", at = @At("RETURN"), cancellable = true)
    private void inmis$checkBackpackAmmo(ItemStack weapon, CallbackInfoReturnable<ItemStack> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        ItemStack fromBackpack = BackpackAugmentHandler.locateAmmunition(player, weapon, cir.getReturnValue());
        if (!fromBackpack.isEmpty()) {
            cir.setReturnValue(fromBackpack);
        }
    }
}
