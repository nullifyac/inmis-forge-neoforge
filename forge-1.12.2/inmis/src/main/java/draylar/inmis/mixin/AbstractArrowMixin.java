package draylar.inmis.mixin;

import draylar.inmis.augment.BackpackAugmentHandler;
import net.minecraft.entity.projectile.EntityArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

@Mixin(EntityArrow.class)
public abstract class AbstractArrowMixin {

    // Vanilla reaches this call only after checking grounded arrows, shake time and ALLOWED pickup.
    @Redirect(method = "onCollideWithPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/InventoryPlayer;addItemStackToInventory(Lnet/minecraft/item/ItemStack;)Z"))
    private boolean inmis$addArrowToBackpack(InventoryPlayer inventory, ItemStack stack) {
        return BackpackAugmentHandler.beforeArrowPickup(inventory.player, (EntityArrow) (Object) this)
                || inventory.addItemStackToInventory(stack);
    }
}
