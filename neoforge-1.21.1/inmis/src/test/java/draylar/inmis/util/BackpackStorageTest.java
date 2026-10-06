package draylar.inmis.util;

import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.component.BackpackComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackpackStorageTest {

    @Test
    void reducingCapacityRetainsEverySavedItemUntilItCanBeRemoved() {
        ItemStack backpack = new ItemStack(Inmis.BACKPACKS.getFirst().get());
        List<ItemStack> contents = new ArrayList<>(Collections.nCopies(54, ItemStack.EMPTY));
        contents.set(0, new ItemStack(Items.DIAMOND, 32));
        contents.set(53, new ItemStack(Items.NETHERITE_CHESTPLATE));
        backpack.set(Inmis.BACKPACK_COMPONENT.get(), new BackpackComponent(contents));
        BackpackInfo smallerTier = tier(9, 3);

        BackpackComponent normalized = Inmis.getOrCreateComponent(backpack, smallerTier);

        assertEquals(54, normalized.stacks().size());
        assertEquals(32, normalized.stacks().getFirst().getCount());
        assertTrue(normalized.stacks().get(53).is(Items.NETHERITE_CHESTPLATE));
        assertEquals(54, BackpackStorage.getRequiredSize(backpack, smallerTier));

        BackpackInventory recovery = new BackpackInventory(backpack, smallerTier);
        assertFalse(recovery.canPlaceItem(27, new ItemStack(Items.DIAMOND)));
        ItemStack recoveredArmor = recovery.removeItem(53, 1);

        assertTrue(recoveredArmor.is(Items.NETHERITE_CHESTPLATE));
        assertEquals(32, Inmis.getOrCreateComponent(backpack, smallerTier).stacks().getFirst().getCount());
        assertTrue(Inmis.getOrCreateComponent(backpack, smallerTier).stacks().get(53).isEmpty());
        assertEquals(27, BackpackStorage.getRequiredSize(backpack, smallerTier));
    }

    @Test
    void increasingCapacityPadsEmptySlotsWithoutChangingSavedItems() {
        ItemStack backpack = new ItemStack(Inmis.BACKPACKS.getFirst().get());
        backpack.set(Inmis.BACKPACK_COMPONENT.get(),
                new BackpackComponent(List.of(ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 7), ItemStack.EMPTY)));

        BackpackComponent expanded = Inmis.getOrCreateComponent(backpack, tier(9, 1));

        assertEquals(9, expanded.stacks().size());
        assertEquals(7, expanded.stacks().get(1).getCount());
        for (int i = 2; i < expanded.stacks().size(); i++) {
            assertTrue(expanded.stacks().get(i).isEmpty());
        }
    }

    @Test
    void recoveryRowsFollowLastOccupiedSlotIncludingChangedRowWidth() {
        assertEquals(6, BackpackStorage.getRequiredRows(9, 3, 53));
        assertEquals(18, BackpackStorage.getRequiredRows(3, 1, 53));
        assertEquals(3, BackpackStorage.getRequiredRows(9, 3, -1));
        assertEquals(3, BackpackStorage.getRequiredRows(9, 3, 26));
        assertEquals(4, BackpackStorage.getRequiredRows(9, 3, 27));
    }

    @Test
    void invalidGeometryFailsBeforeAllocatingAnInventory() {
        assertThrows(IllegalArgumentException.class, () -> BackpackStorage.getRequiredRows(0, 3, -1));
        assertThrows(IllegalArgumentException.class, () -> BackpackStorage.getRequiredRows(9, -1, -1));
        assertThrows(IllegalArgumentException.class,
                () -> BackpackStorage.getRequiredRows(Integer.MAX_VALUE, 3, -1));
    }

    private static BackpackInfo tier(int width, int rows) {
        return new BackpackInfo("test", width, rows, false, "minecraft:item.armor.equip_leather");
    }
}
