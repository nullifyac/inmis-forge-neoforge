package draylar.inmis.util;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;

public class InventoryUtils {

    public static NBTTagList toTag(InventoryBasic inventory) {
        NBTTagList tag = new NBTTagList();

        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                NBTTagCompound stackTag = new NBTTagCompound();
                stackTag.setInteger("Slot", i);
                stackTag.setTag("Stack", stack.writeToNBT(new NBTTagCompound()));
                tag.appendTag(stackTag);
            }
        }

        return tag;
    }

    public static void fromTag(NBTTagList tag, InventoryBasic inventory) {
        java.util.Map<Integer, ItemStack> parsed = new java.util.LinkedHashMap<>();
        draylar.inmis.Inmis.elements(tag).forEach(element -> {
            if (!(element instanceof NBTTagCompound)) throw new IllegalArgumentException("Invalid backpack inventory entry");
            NBTTagCompound stackTag = (NBTTagCompound) element;
            if (!stackTag.hasKey("Slot", 3) || !stackTag.hasKey("Stack", 10)) throw new IllegalArgumentException("Malformed backpack inventory entry");
            int slot = stackTag.getInteger("Slot");
            ItemStack stack = new ItemStack(stackTag.getCompoundTag("Stack"));
            if (stack.isEmpty()) {
                return;
            }
            if (slot < 0 || slot >= inventory.getSizeInventory()) {
                throw new IllegalArgumentException("Occupied backpack slot is outside the inventory: " + slot);
            }
            if (parsed.putIfAbsent(slot, stack) != null) throw new IllegalArgumentException("Duplicate occupied backpack slot: " + slot);
        });
        inventory.clear();
        parsed.forEach(inventory::setInventorySlotContents);
    }
}
