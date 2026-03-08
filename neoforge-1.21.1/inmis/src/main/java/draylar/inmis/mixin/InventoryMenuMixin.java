package draylar.inmis.mixin;

import draylar.inmis.Inmis;
import draylar.inmis.compat.AccessoriesCompat;
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

    private static final ThreadLocal<Boolean> INMIS$QUICK_MOVE_GUARD = ThreadLocal.withInitial(() -> false);

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void inmis$quickMoveStack(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (player.level().isClientSide || INMIS$QUICK_MOVE_GUARD.get()) {
            return;
        }

        if ((!Inmis.CURIOS_LOADED && !Inmis.ACCESSORIES_LOADED) || !Inmis.CONFIG.enableTrinketCompatibility) {
            return;
        }

        Slot slot = ((InventoryMenu) (Object) this).getSlot(index);
        if (slot == null || !slot.hasItem()) {
            return;
        }

        // Only handle quick-equip from the player's main inventory/hotbar.
        // This avoids re-entering trinket auto-equip logic from armor/crafting/accessory slots.
        if (slot.container != player.getInventory()) {
            return;
        }
        int containerSlot = slot.getContainerSlot();
        if (containerSlot < 0 || containerSlot >= 36) {
            return;
        }

        ItemStack stack = slot.getItem();
        if (!(stack.getItem() instanceof BackpackItem) && stack.getItem() != Inmis.ENDER_POUCH.get()) {
            return;
        }

        INMIS$QUICK_MOVE_GUARD.set(true);
        try {
            ItemStack original = stack.copy();
            if (Inmis.ACCESSORIES_LOADED && AccessoriesCompat.tryEquipBackpack(player, stack)) {
                if (stack.isEmpty()) {
                    slot.set(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }
                slot.onTake(player, original);
                cir.setReturnValue(original);
                return;
            }

            if (Inmis.CURIOS_LOADED && CuriosCompat.tryEquipBackpack(player, stack)) {
                if (stack.isEmpty()) {
                    slot.set(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }
                slot.onTake(player, original);
                cir.setReturnValue(original);
            }
        } finally {
            INMIS$QUICK_MOVE_GUARD.set(false);
        }
    }
}
