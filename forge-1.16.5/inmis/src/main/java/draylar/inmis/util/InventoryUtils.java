package draylar.inmis.util;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

public class InventoryUtils {

    public static ListNBT toTag(Inventory inventory) {
        ListNBT tag = new ListNBT();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                CompoundNBT stackTag = new CompoundNBT();
                stackTag.putInt("Slot", i);
                stackTag.put("Stack", stack.save(new CompoundNBT()));
                tag.add(stackTag);
            }
        }

        return tag;
    }

    public static void fromTag(ListNBT tag, Inventory inventory) {
        inventory.clearContent();

        tag.forEach(element -> {
            CompoundNBT stackTag = (CompoundNBT) element;
            int slot = stackTag.getInt("Slot");
            ItemStack stack = ItemStack.of(stackTag.getCompound("Stack"));
            if (stack.isEmpty()) {
                return;
            }
            if (slot < 0 || slot >= inventory.getContainerSize()) {
                throw new IllegalArgumentException("Occupied backpack slot is outside the inventory: " + slot);
            }
            inventory.setItem(slot, stack);
        });
    }
}
