package draylar.inmis.mixin;

import draylar.inmis.Inmis;
import draylar.inmis.compat.CuriosCompat;
import draylar.inmis.item.BackpackItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void inmis$quickMoveStack(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (!Inmis.CURIOS_LOADED || !Inmis.CONFIG.enableTrinketCompatibility) {
            return;
        }

        Slot slot = ((InventoryMenu) (Object) this).getSlot(index);
        if (slot == null || !slot.hasItem()) {
            return;
        }

        ItemStack stack = slot.getItem();
        if (!(stack.getItem() instanceof BackpackItem) && stack.getItem() != Inmis.ENDER_POUCH.get()) {
            return;
        }

        ItemStack original = stack.copy();
        if (CuriosCompat.tryEquipBackpack(player, stack)) {
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            slot.onTake(player, stack);
            cir.setReturnValue(original);
        }
    }
}
