package draylar.inmis.ui;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackComponent;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackpackScreenHandlerTest {

    @BeforeAll
    static void bindItemComponents() {
        draylar.inmis.test.MinecraftTestBootstrap.bindItemComponents();
    }

    @Test
    void clientUsesServerDimensionsInsteadOfTheLocalBackpackTier() {
        ItemStack backpack = backpack();
        List<ItemStack> contents = new ArrayList<>(Collections.nCopies(54, ItemStack.EMPTY));
        contents.set(53, new ItemStack(Items.NETHERITE_CHESTPLATE));
        backpack.set(Inmis.BACKPACK_COMPONENT.get(), new BackpackComponent(contents));
        Inventory inventory = new Inventory(null, new net.minecraft.world.entity.EntityEquipment());

        BackpackScreenHandler menu = clientMenu(inventory, backpack, 9, 6, 27, -1);

        // The locally registered baby tier has only three columns and one row.
        assertEquals(3, ((BackpackItem) backpack.getItem()).getTier().getRowWidth());
        assertEquals(1, ((BackpackItem) backpack.getItem()).getTier().getNumberOfRows());
        assertEquals(54 + 36, menu.slots.size());
        assertEquals(178, menu.getDimension().getWidth());
        assertEquals(224, menu.getDimension().getHeight());
        assertEquals(9, menu.getSlot(0).x);
        assertEquals(153, menu.getSlot(8).x);
        assertEquals(37, menu.getSlot(9).y);
        assertNull(menu.getBackpackInventory(), "Client menus must use synchronized temporary storage");
        assertEquals(54, menu.getBackpackStack().get(Inmis.BACKPACK_COMPONENT.get()).stacks().size());
        assertTrue(menu.getBackpackStack().get(Inmis.BACKPACK_COMPONENT.get()).stacks().get(53)
                .is(Items.NETHERITE_CHESTPLATE));
    }

    @Test
    void clientLocksTheActiveInventorySlotEvenWhenTheBackpackWasDecodedAsACopy() {
        ItemStack backpack = backpack();
        Inventory inventory = new Inventory(null, new net.minecraft.world.entity.EntityEquipment());
        ItemStack activeCopy = backpack.copy();
        ItemStack otherBackpack = backpack.copy();
        inventory.setItem(2, activeCopy);
        inventory.setItem(3, otherBackpack);
        BackpackScreenHandler menu = clientMenu(inventory, backpack, 9, 1, 9, 2);
        Slot activeSlot = playerSlot(menu, inventory, 2);
        Slot otherSlot = playerSlot(menu, inventory, 3);

        assertNotSame(menu.getBackpackStack(), activeCopy);
        assertFalse(activeSlot.mayPickup(null));
        assertFalse(activeSlot.mayPlace(new ItemStack(Items.DIAMOND)));
        assertTrue(otherSlot.mayPickup(null));
        assertTrue(menu.quickMoveStack(null, menu.slots.indexOf(activeSlot)).isEmpty());
        assertSame(activeCopy, inventory.getItem(2));

        menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 7));
        assertDoesNotThrow(() -> menu.clicked(0, 2, ContainerInput.SWAP, null));
        assertEquals(7, menu.getSlot(0).getItem().getCount());
        assertSame(activeCopy, inventory.getItem(2));
    }

    @Test
    void serverOpeningDataRoundTripsGeometryAndTheActivePlayerSlot() {
        ItemStack backpack = backpack();
        backpack.set(Inmis.BACKPACK_COMPONENT.get(),
                new BackpackComponent(List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                        ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 11))));
        Inventory serverInventory = new Inventory(null, new net.minecraft.world.entity.EntityEquipment());
        serverInventory.setItem(4, backpack);
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            BackpackScreenHandler.writeOpeningData(buffer, serverInventory, backpack);
            Inventory clientInventory = new Inventory(null, new net.minecraft.world.entity.EntityEquipment());
            clientInventory.setItem(4, backpack.copy());

            BackpackScreenHandler client = new BackpackScreenHandler(1, clientInventory, buffer);

            assertEquals(6 + 36, client.slots.size());
            assertEquals(0, buffer.readableBytes());
            assertFalse(playerSlot(client, clientInventory, 4).mayPickup(null));
            assertTrue(client.getSlot(2).mayPlace(new ItemStack(Items.DIAMOND)));
            assertFalse(client.getSlot(3).mayPlace(new ItemStack(Items.DIAMOND)));
            assertEquals(11, client.getBackpackStack().get(Inmis.BACKPACK_COMPONENT.get()).stacks().get(5).getCount());
        } finally {
            buffer.release();
        }
    }

    @Test
    void existingNestedBackpacksCanBeExtractedWithoutUnlockingTheActiveBackpack() {
        ItemStack activeBackpack = backpack();
        ItemStack nestedBackpack = backpack();
        nestedBackpack.set(Inmis.BACKPACK_COMPONENT.get(),
                new BackpackComponent(List.of(new ItemStack(Items.DIAMOND, 11))));
        activeBackpack.set(Inmis.BACKPACK_COMPONENT.get(),
                new BackpackComponent(List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                        ItemStack.EMPTY, ItemStack.EMPTY, nestedBackpack)));
        Inventory inventory = new Inventory(null, new net.minecraft.world.entity.EntityEquipment());
        inventory.setItem(0, activeBackpack);
        BackpackScreenHandler menu = new BackpackScreenHandler(1, inventory, activeBackpack);

        assertTrue(menu.getSlot(5).mayPickup(null));
        assertFalse(menu.getSlot(5).mayPlace(new ItemStack(Items.DIAMOND)));
        assertFalse(playerSlot(menu, inventory, 0).mayPickup(null));

        ItemStack moved = menu.quickMoveStack(null, 5);

        assertTrue(moved.getItem() instanceof BackpackItem);
        assertTrue(menu.getSlot(5).getItem().isEmpty());
        assertSame(activeBackpack, inventory.getItem(0));
        assertEquals(11, inventory.getItem(8).get(Inmis.BACKPACK_COMPONENT.get()).stacks().getFirst().getCount());
        assertTrue(activeBackpack.get(Inmis.BACKPACK_COMPONENT.get()).stacks().get(5).isEmpty());
    }

    @Test
    void clientRecoverySlotsAllowExtractionButCannotReceiveShiftClickedItems() {
        Inventory inventory = new Inventory(null, new net.minecraft.world.entity.EntityEquipment());
        inventory.setItem(2, backpack());
        inventory.setItem(3, new ItemStack(Items.DIAMOND, 8));
        BackpackScreenHandler menu = clientMenu(inventory, inventory.getItem(2), 9, 6, 27, 2);
        for (int i = 0; i < 27; i++) {
            menu.getSlot(i).set(new ItemStack(Items.DIAMOND, 64));
        }
        menu.getSlot(53).set(new ItemStack(Items.NETHERITE_CHESTPLATE));

        assertFalse(menu.getSlot(27).mayPlace(new ItemStack(Items.DIAMOND)));
        assertTrue(menu.getSlot(53).mayPickup(null));
        Slot source = playerSlot(menu, inventory, 3);
        assertTrue(menu.quickMoveStack(null, menu.slots.indexOf(source)).isEmpty());
        assertEquals(8, inventory.getItem(3).getCount());
        assertTrue(menu.getSlot(27).getItem().isEmpty());

        ItemStack extracted = menu.quickMoveStack(null, 53);

        assertTrue(extracted.is(Items.NETHERITE_CHESTPLATE));
        assertTrue(menu.getSlot(53).getItem().isEmpty());
        assertTrue(inventory.getItem(8).is(Items.NETHERITE_CHESTPLATE));
    }

    @Test
    void malformedServerGeometryIsRejectedBeforeSlotAllocation() {
        Inventory inventory = new Inventory(null, new net.minecraft.world.entity.EntityEquipment());
        ItemStack backpack = backpack();

        assertThrows(IllegalArgumentException.class, () -> clientMenu(inventory, backpack, 0, 6, 27, -1));
        assertThrows(IllegalArgumentException.class, () -> clientMenu(inventory, backpack, 9, 0, 27, -1));
        assertThrows(IllegalArgumentException.class, () -> clientMenu(inventory, backpack, 9, 1, 10, -1));
        assertThrows(IllegalArgumentException.class,
                () -> clientMenu(inventory, backpack, 9, Short.MAX_VALUE, 27, -1));
    }

    private static ItemStack backpack() {
        return new ItemStack(Inmis.BACKPACKS.getFirst().get());
    }

    private static Slot playerSlot(BackpackScreenHandler menu, Inventory inventory, int index) {
        return menu.slots.stream().filter(slot -> slot.container == inventory && slot.getContainerSlot() == index)
                .findFirst().orElseThrow();
    }

    private static BackpackScreenHandler clientMenu(Inventory inventory, ItemStack backpack,
                                                   int width, int rows, int configuredSlots, int lockedPlayerSlot) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            ItemStack.STREAM_CODEC.encode(buffer, backpack);
            buffer.writeVarInt(width);
            buffer.writeVarInt(rows);
            buffer.writeVarInt(configuredSlots);
            buffer.writeInt(lockedPlayerSlot);
            return new BackpackScreenHandler(1, inventory, buffer);
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
}
