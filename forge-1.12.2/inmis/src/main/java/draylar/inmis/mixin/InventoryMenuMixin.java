package draylar.inmis.mixin;

import draylar.inmis.Inmis;
import draylar.inmis.compat.BaublesCompat;
import draylar.inmis.item.BackpackItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ContainerPlayer.class)
public abstract class InventoryMenuMixin {

    @Inject(method = "transferStackInSlot", at = @At("HEAD"), cancellable = true)
    private void inmis$transferStackInSlot(EntityPlayer player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (!Inmis.BAUBLES_LOADED || !Inmis.CONFIG.enableTrinketCompatibility) {
            return;
        }

        java.util.List<net.minecraft.inventory.Slot> slots = ((AbstractContainerMenuAccessor) this).inmis$getSlots();
        if (index < 0 || index >= slots.size()) {
            return;
        }
        Slot slot = slots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return;
        }

        ItemStack stack = slot.getStack();
        if (!(stack.getItem() instanceof BackpackItem) && stack.getItem() != Inmis.ENDER_POUCH.get()) {
            return;
        }

        ItemStack original = stack.copy();
        if (BaublesCompat.tryEquipBackpack(player, stack)) {
            if (stack.isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }
            slot.onTake(player, stack);
            cir.setReturnValue(original);
        }
    }
}
