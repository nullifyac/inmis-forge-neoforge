package draylar.inmis.util;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.AbstractList;
import java.util.List;

/** Mutable views of vanilla inventory sections, including the equipment-backed slots. */
public final class PlayerInventorySections {
    private PlayerInventorySections() {}

    public static List<ItemStack> armor(Inventory inventory) {
        return slots(inventory, 36, 4);
    }

    public static List<ItemStack> offhand(Inventory inventory) {
        return slots(inventory, Inventory.SLOT_OFFHAND, 1);
    }

    private static List<ItemStack> slots(Inventory inventory, int first, int count) {
        return new AbstractList<>() {
            @Override public ItemStack get(int index) {
                java.util.Objects.checkIndex(index, count);
                return inventory.getItem(first + index);
            }
            @Override public ItemStack set(int index, ItemStack value) {
                ItemStack previous = get(index);
                inventory.setItem(first + index, value);
                return previous;
            }
            @Override public int size() { return count; }
        };
    }
}
