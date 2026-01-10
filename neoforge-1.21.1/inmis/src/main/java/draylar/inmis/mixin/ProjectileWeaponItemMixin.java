package draylar.inmis.mixin;

import draylar.inmis.augment.BackpackAugmentHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProjectileWeaponItem.class)
public abstract class ProjectileWeaponItemMixin {

    @Inject(method = "useAmmo", at = @At("RETURN"))
    private static void inmis$syncBackpackAmmo(ItemStack weapon, ItemStack ammo, LivingEntity shooter,
                                               boolean multishot, CallbackInfoReturnable<ItemStack> cir) {
        BackpackAugmentHandler.finishQuiverlinkUse();
    }
}
