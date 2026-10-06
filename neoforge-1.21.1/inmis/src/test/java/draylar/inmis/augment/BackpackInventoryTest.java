package draylar.inmis.augment;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.config.InmisConfig;
import draylar.inmis.item.component.BackpackComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackpackInventoryTest {

    private InmisConfig originalConfig;

    @BeforeEach
    void useIsolatedConfig() {
        originalConfig = Inmis.CONFIG;
        Inmis.CONFIG = new InmisConfig();
    }

    @AfterEach
    void restoreConfig() {
        Inmis.CONFIG = originalConfig;
    }

    @Test
    void automaticInsertionRejectsBackpacksWithoutConsumingThem() {
        BackpackInventory inventory = inventory(3, 3);
        ItemStack nested = new ItemStack(Inmis.BACKPACKS.getFirst().get());
        nested.set(Inmis.BACKPACK_COMPONENT.get(),
                new BackpackComponent(List.of(new ItemStack(Items.DIAMOND, 32))));

        ItemStack remainder = inventory.addItem(nested);

        assertEquals(1, nested.getCount());
        assertEquals(1, remainder.getCount());
        assertTrue(ItemStack.isSameItemSameComponents(nested, remainder));
        assertEquals(32, remainder.get(Inmis.BACKPACK_COMPONENT.get()).stacks().getFirst().getCount());
        assertTrue(inventory.isEmpty());
    }

    @Test
    void automaticInsertionAppliesShulkerBlacklistAndUnstackablePolicies() {
        BackpackInventory inventory = inventory(3, 3);
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        assertFalse(inventory.isAllowedItem(shulker));
        assertEquals(1, inventory.addItem(shulker).getCount());

        // Ordinary BlockItems must reach the blacklist check, unlike the former early return.
        Inmis.CONFIG.blacklist = List.of("minecraft:chest");
        ItemStack blacklistedBlock = new ItemStack(Items.CHEST, 4);
        assertFalse(inventory.isAllowedItem(blacklistedBlock));
        assertEquals(4, inventory.addItem(blacklistedBlock).getCount());

        Inmis.CONFIG.unstackablesOnly = true;
        assertEquals(4, inventory.addItem(new ItemStack(Items.DIAMOND, 4)).getCount());
        assertTrue(inventory.addItem(new ItemStack(Items.NETHERITE_CHESTPLATE)).isEmpty());
    }

    @Test
    void automaticInsertionAcceptsOrdinaryBlocksAndOptionalShulkers() {
        BackpackInventory inventory = inventory(3, 3);
        assertTrue(inventory.addItem(new ItemStack(Items.CHEST, 4)).isEmpty());
        assertEquals(4, inventory.getItem(0).getCount());

        Inmis.CONFIG.disableShulkers = false;
        assertTrue(inventory.addItem(new ItemStack(Items.SHULKER_BOX)).isEmpty());
        assertTrue(inventory.getItem(1).is(Items.SHULKER_BOX));
    }

    @Test
    void insertionConservesItemsWhileMergingAndFillingAvailableSlots() {
        BackpackInventory inventory = inventory(2, 2);
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 60));
        ItemStack source = new ItemStack(Items.DIAMOND, 100);

        ItemStack remainder = inventory.addItem(source);

        assertEquals(100, source.getCount());
        assertEquals(64, inventory.getItem(0).getCount());
        assertEquals(64, inventory.getItem(1).getCount());
        assertEquals(32, remainder.getCount());
        assertEquals(160, inventory.getItem(0).getCount() + inventory.getItem(1).getCount() + remainder.getCount());
    }

    @Test
    void automaticInsertionCannotFillRecoverySlotsAfterCapacityIsReduced() {
        BackpackInventory inventory = inventory(2, 4);
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 64));
        inventory.setItem(1, new ItemStack(Items.DIAMOND, 64));
        inventory.setItem(3, new ItemStack(Items.NETHERITE_CHESTPLATE));

        ItemStack remainder = inventory.addItem(new ItemStack(Items.DIAMOND, 8));

        assertEquals(8, remainder.getCount());
        assertTrue(inventory.getItem(2).isEmpty());
        assertTrue(inventory.getItem(3).is(Items.NETHERITE_CHESTPLATE));
        assertFalse(inventory.canPlaceItem(2, new ItemStack(Items.DIAMOND)));
        assertTrue(Inmis.getBackpackContents(inventory.getBackpackStack()).get(3).is(Items.NETHERITE_CHESTPLATE));
    }

    private static BackpackInventory inventory(int configuredSlots, int savedSlots) {
        ItemStack backpack = new ItemStack(Inmis.BACKPACKS.getFirst().get());
        BackpackInfo tier = new BackpackInfo("test", configuredSlots, 1, false,
                "minecraft:item.armor.equip_leather");
        return new BackpackInventory(backpack, tier, savedSlots);
    }
}
