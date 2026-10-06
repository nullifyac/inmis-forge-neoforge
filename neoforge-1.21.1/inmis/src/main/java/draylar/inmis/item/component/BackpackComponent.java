package draylar.inmis.item.component;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record BackpackComponent(List<ItemStack> stacks) {

    public BackpackComponent {
        stacks = copyStacks(stacks);
    }

    @Override
    public List<ItemStack> stacks() {
        return copyStacks(stacks);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BackpackComponent component) || stacks.size() != component.stacks.size()) {
            return false;
        }
        for (int i = 0; i < stacks.size(); i++) {
            if (!ItemStack.matches(stacks.get(i), component.stacks.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (ItemStack stack : stacks) {
            hash = 31 * hash + (stack.isEmpty() ? 0 : 31 * ItemStack.hashItemAndComponents(stack) + stack.getCount());
        }
        return hash;
    }

    public static final Codec<BackpackComponent> CODEC = ItemStack.OPTIONAL_CODEC.listOf()
            .xmap(BackpackComponent::new, BackpackComponent::stacks);

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackComponent> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()),
                    BackpackComponent::stacks,
                    BackpackComponent::new);

    public SimpleContainer toContainer() {
        SimpleContainer container = new SimpleContainer(stacks.size());
        for (int i = 0; i < stacks.size(); i++) {
            container.setItem(i, stacks.get(i).copy());
        }
        return container;
    }

    public static BackpackComponent fromContainer(Container container) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            items.add(container.getItem(i));
        }
        return new BackpackComponent(items);
    }
}
