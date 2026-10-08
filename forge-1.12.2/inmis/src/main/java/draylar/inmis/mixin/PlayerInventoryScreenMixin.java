package draylar.inmis.mixin;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.inventory.ContainerPlayer$1")
public abstract class PlayerInventoryScreenMixin extends Slot {

    public PlayerInventoryScreenMixin(IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Inject(method = "canTakeStack(Lnet/minecraft/entity/player/EntityPlayer;)Z", at = @At("HEAD"), cancellable = true)
    private void checkUnequip(EntityPlayer player, CallbackInfoReturnable<Boolean> cir) {
        ItemStack itemStack = getStack();
        if (itemStack.getItem() instanceof BackpackItem) {
            if (Inmis.CONFIG.requireEmptyForUnequip) {
                if (!Inmis.isBackpackEmpty(itemStack)) {
                    cir.setReturnValue(false);
                }
            }
        }
    }

    @Inject(method = "isItemValid(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void checkEquip(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof BackpackItem && !Inmis.CONFIG.allowBackpacksInChestplate) {
            cir.setReturnValue(false);
        }
    }
}
