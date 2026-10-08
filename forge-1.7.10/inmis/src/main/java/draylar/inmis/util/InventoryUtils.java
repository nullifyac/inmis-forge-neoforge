package draylar.inmis.util;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public final class InventoryUtils {
    private InventoryUtils() { }
    public static boolean isEmpty(ItemStack stack) { return stack == null || stack.stackSize <= 0; }
    public static NBTTagList toTag(IInventory inventory) {
        NBTTagList tag = new NBTTagList();
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!isEmpty(stack)) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setInteger("Slot", i);
                entry.setTag("Stack", stack.writeToNBT(new NBTTagCompound()));
                tag.appendTag(entry);
            }
        }
        return tag;
    }
    public static void fromTag(NBTTagList tag, IInventory inventory) {
        boolean[] occupied = new boolean[inventory.getSizeInventory()];
        for (int i = 0; i < tag.tagCount(); i++) {
            NBTTagCompound entry = tag.getCompoundTagAt(i);
            ItemStack stack = ItemStack.loadItemStackFromNBT(entry.getCompoundTag("Stack"));
            if (isEmpty(stack)) continue;
            int slot = entry.getInteger("Slot");
            if (slot < 0 || slot >= occupied.length || occupied[slot])
                throw new IllegalArgumentException("Invalid or duplicate occupied backpack slot: " + slot);
            occupied[slot] = true;
            inventory.setInventorySlotContents(slot, stack);
        }
    }
}
