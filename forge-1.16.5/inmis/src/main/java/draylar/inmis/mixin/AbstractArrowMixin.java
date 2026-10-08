package draylar.inmis.mixin;

import draylar.inmis.augment.BackpackAugmentHandler;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

@Mixin(AbstractArrowEntity.class)
public abstract class AbstractArrowMixin {

    // Vanilla reaches this call only after checking grounded arrows, shake time and ALLOWED pickup.
    @Redirect(method = "playerTouch", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerInventory;add(Lnet/minecraft/item/ItemStack;)Z"))
    private boolean inmis$addArrowToBackpack(PlayerInventory inventory, ItemStack stack) {
        return BackpackAugmentHandler.beforeArrowPickup(inventory.player, (AbstractArrowEntity) (Object) this)
                || inventory.add(stack);
    }
}
