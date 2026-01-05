package draylar.inmis.mixin;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.inventory.InventoryMenu$1")
public abstract class PlayerInventoryScreenMixin extends Slot {

    public PlayerInventoryScreenMixin(Container inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Inject(method = "mayPickup(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"), cancellable = true)
    private void checkUnequip(Player player, CallbackInfoReturnable<Boolean> cir) {
        ItemStack itemStack = getItem();
        if (itemStack.getItem() instanceof BackpackItem) {
            if (Inmis.CONFIG.requireEmptyForUnequip) {
                if (!Inmis.isBackpackEmpty(itemStack)) {
                    cir.setReturnValue(false);
                }
            }
        }
    }

    @Inject(method = "mayPlace(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void checkEquip(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof BackpackItem && !Inmis.CONFIG.allowBackpacksInChestplate) {
            cir.setReturnValue(false);
        }
    }
}
