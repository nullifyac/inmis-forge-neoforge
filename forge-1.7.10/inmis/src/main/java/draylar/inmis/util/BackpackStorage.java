package draylar.inmis.util;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Recovery rows preserve items after reducing configured capacity. */
public final class BackpackStorage {
    private BackpackStorage() { }
    public static int getRequiredRows(ItemStack stack, BackpackInfo tier) {
        NBTTagList contents = Inmis.getOrCreateInventory(stack, tier);
        int last = -1;
        for (int i = 0; i < contents.tagCount(); i++) {
            NBTTagCompound entry = contents.getCompoundTagAt(i);
            if (!InventoryUtils.isEmpty(ItemStack.loadItemStackFromNBT(entry.getCompoundTag("Stack")))) {
                int slot = entry.getInteger("Slot");
                if (slot < 0) throw new IllegalArgumentException("Negative occupied backpack slot");
                last = Math.max(last, slot);
            }
        }
        return getRequiredRows(tier.getRowWidth(), tier.getNumberOfRows(), last);
    }
    public static int getRequiredSize(ItemStack stack, BackpackInfo tier) {
        return Math.multiplyExact(tier.getRowWidth(), getRequiredRows(stack, tier));
    }
    public static int getRequiredRows(int width, int configuredRows, int lastSlot) {
        if (width <= 0 || configuredRows <= 0) throw new IllegalArgumentException("Invalid backpack dimensions");
        long rows = Math.max(configuredRows, lastSlot < 0 ? 0 : (long)lastSlot / width + 1);
        if (rows * width > Short.MAX_VALUE - 36L || width * 18L + 16 > Integer.MAX_VALUE
                || (rows + 4) * 18 + 44 > Integer.MAX_VALUE) throw new IllegalArgumentException("Backpack dimensions exceed menu protocol");
        return (int)rows;
    }
}
