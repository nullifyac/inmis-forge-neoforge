package draylar.inmis.item.component;

import draylar.inmis.Inmis;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackpackComponentTest {

    @BeforeAll
    static void bindItemComponents() {
        draylar.inmis.test.MinecraftTestBootstrap.bindItemComponents();
    }

    @Test
    void constructorSnapshotsMutableStacksAndInputList() {
        ItemStack diamonds = new ItemStack(Items.DIAMOND, 32);
        List<ItemStack> contents = new ArrayList<>(List.of(diamonds));
        BackpackComponent component = new BackpackComponent(contents);

        diamonds.shrink(10);
        contents.clear();

        assertEquals(1, component.stacks().size());
        assertEquals(32, component.stacks().getFirst().getCount());
    }

    @Test
    void readingContentsCannotMutateSavedSnapshot() {
        BackpackComponent component = new BackpackComponent(List.of(new ItemStack(Items.DIAMOND, 32)));

        component.stacks().getFirst().shrink(10);

        assertEquals(32, component.stacks().getFirst().getCount());
    }

    @Test
    void containersAndSavedComponentsHaveIndependentStacks() {
        SimpleContainer inventory = new SimpleContainer(2);
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 32));
        BackpackComponent component = BackpackComponent.fromContainer(inventory);

        inventory.removeItem(0, 10);
        SimpleContainer restored = component.toContainer();
        restored.removeItem(0, 5);

        assertEquals(22, inventory.getItem(0).getCount());
        assertEquals(27, restored.getItem(0).getCount());
        assertEquals(32, component.stacks().getFirst().getCount());
    }

    @Test
    void persistenceCodecPreservesSlotsCountsAndItemComponents() {
        BackpackComponent original = namedContents();
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        var encoded = BackpackComponent.CODEC.encodeStart(ops, original).getOrThrow();
        BackpackComponent decoded = BackpackComponent.CODEC.parse(ops, encoded).getOrThrow();

        assertContentsEqual(original, decoded);
    }

    @Test
    void networkCodecPreservesSlotsCountsAndItemComponents() {
        BackpackComponent original = namedContents();
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            BackpackComponent.STREAM_CODEC.encode(buffer, original);
            BackpackComponent decoded = BackpackComponent.STREAM_CODEC.decode(buffer);

            assertContentsEqual(original, decoded);
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void backpackItemCodecRetainsRegisteredContentsComponent() {
        ItemStack original = new ItemStack(Inmis.BACKPACKS.getFirst().get());
        original.set(Inmis.BACKPACK_COMPONENT.get(), namedContents());
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);

        var encoded = ItemStack.CODEC.encodeStart(ops, original).getOrThrow();
        ItemStack decoded = ItemStack.CODEC.parse(ops, encoded).getOrThrow();

        assertTrue(ItemStack.isSameItemSameComponents(original, decoded));
        assertContentsEqual(original.get(Inmis.BACKPACK_COMPONENT.get()),
                decoded.get(Inmis.BACKPACK_COMPONENT.get()));
    }

    @Test
    void independentlySavedSnapshotsCompareByItemContents() {
        BackpackComponent original = namedContents();
        BackpackComponent rebuilt = new BackpackComponent(original.stacks());

        assertEquals(original, rebuilt);
        assertEquals(original.hashCode(), rebuilt.hashCode());

        List<ItemStack> changedContents = new ArrayList<>(rebuilt.stacks());
        changedContents.getFirst().shrink(1);
        assertNotEquals(original, new BackpackComponent(changedContents));
        assertEquals(original, rebuilt);
    }

    private static BackpackComponent namedContents() {
        ItemStack armor = new ItemStack(Items.NETHERITE_CHESTPLATE);
        armor.set(DataComponents.CUSTOM_NAME, Component.literal("Saved armor"));
        armor.setDamageValue(23);
        return new BackpackComponent(List.of(new ItemStack(Items.DIAMOND, 32), ItemStack.EMPTY,
                armor, new ItemStack(Items.ARROW, 64)));
    }

    private static void assertContentsEqual(BackpackComponent expected, BackpackComponent actual) {
        List<ItemStack> expectedStacks = expected.stacks();
        List<ItemStack> actualStacks = actual.stacks();
        assertEquals(expectedStacks.size(), actualStacks.size());
        for (int i = 0; i < expectedStacks.size(); i++) {
            assertTrue(ItemStack.isSameItemSameComponents(expectedStacks.get(i), actualStacks.get(i)),
                    "Item/components changed in slot " + i);
            assertEquals(expectedStacks.get(i).getCount(), actualStacks.get(i).getCount(),
                    "Count changed in slot " + i);
        }
    }
}
