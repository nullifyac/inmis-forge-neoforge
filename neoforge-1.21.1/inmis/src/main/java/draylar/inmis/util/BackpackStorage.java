package draylar.inmis.util;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.component.BackpackComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Conserves saved items when a server administrator reduces a backpack's capacity. */
public final class BackpackStorage {

    private BackpackStorage() {
    }

    public static int getRequiredRows(ItemStack stack, BackpackInfo tier) {
        BackpackComponent component = Inmis.getOrCreateComponent(stack, tier);
        List<ItemStack> contents = component != null ? component.stacks() : List.of();
        for (int i = contents.size() - 1; i >= 0; i--) {
            if (!contents.get(i).isEmpty()) {
                return getRequiredRows(tier.getRowWidth(), tier.getNumberOfRows(), i);
            }
        }
        return getRequiredRows(tier.getRowWidth(), tier.getNumberOfRows(), -1);
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
