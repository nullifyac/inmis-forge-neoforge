package draylar.inmis.util;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Conserves saved items when a server administrator reduces a backpack's capacity. */
public final class BackpackStorage {

    private BackpackStorage() {
    }

    public static int getRequiredRows(ItemStack stack, BackpackInfo tier) {
        ListTag contents = Inmis.getOrCreateInventory(stack, tier);
        int lastOccupiedSlot = -1;
        for (Tag entry : contents) {
            if (entry instanceof CompoundTag itemTag) {
                int slot = itemTag.getInt("Slot");
                if (!ItemStack.of(itemTag.getCompound("Stack")).isEmpty()) {
                    if (slot < 0) {
                        throw new IllegalArgumentException("Occupied backpack slot is negative: " + slot);
                    }
                    lastOccupiedSlot = Math.max(lastOccupiedSlot, slot);
                }
            }
        }
        return getRequiredRows(tier.getRowWidth(), tier.getNumberOfRows(), lastOccupiedSlot);
    }

    public static int getRequiredSize(ItemStack stack, BackpackInfo tier) {
        return Math.multiplyExact(tier.getRowWidth(), getRequiredRows(stack, tier));
    }

    public static int getRequiredRows(int rowWidth, int configuredRows, int lastOccupiedSlot) {
        if (rowWidth <= 0 || configuredRows <= 0) {
            throw new IllegalArgumentException("Backpack dimensions must be positive");
        }
        long recoveryRows = lastOccupiedSlot < 0 ? 0 : (long) lastOccupiedSlot / rowWidth + 1;
        long rows = Math.max(configuredRows, recoveryRows);
        if (rows * rowWidth > Integer.MAX_VALUE - 36L
                || rowWidth * 18L + 16 > Integer.MAX_VALUE
                || (rows + 4) * 18L + 44 > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Backpack dimensions are too large");
        }
        return (int) rows;
    }
}
