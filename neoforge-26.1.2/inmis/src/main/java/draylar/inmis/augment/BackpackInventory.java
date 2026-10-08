package draylar.inmis.augment;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.util.BackpackStorage;
import draylar.inmis.item.component.BackpackComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.Hopper;

import java.util.List;
import java.util.function.Predicate;

public class BackpackInventory extends SimpleContainer {

    private final ItemStack backpackStack;
    private final BackpackInfo tier;
    private boolean loading = true;

    public BackpackInventory(ItemStack backpackStack, BackpackInfo tier) {
        this(backpackStack, tier, BackpackStorage.getRequiredSize(backpackStack, tier));
    }

    public BackpackInventory(ItemStack backpackStack, BackpackInfo tier, int size) {
        super(Math.max(0, size));
        this.backpackStack = backpackStack;
        this.tier = tier;

        BackpackComponent component = Inmis.getOrCreateComponent(backpackStack, tier);
        List<ItemStack> stacks = component != null ? component.stacks() : List.of();
        for (int i = 0; i < getContainerSize(); i++) {
            setItem(i, i < stacks.size() ? stacks.get(i).copy() : ItemStack.EMPTY);
        }
        loading = false;
    }

    public ItemStack getBackpackStack() {
        return backpackStack;
    }

    @Override
    public void setChanged() {
        if (loading) {
            return;
        }
        backpackStack.set(Inmis.BACKPACK_COMPONENT.get(), BackpackComponent.fromContainer(this));
        super.setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < tier.getRowWidth() * tier.getNumberOfRows()
                && slot < getContainerSize() && super.canPlaceItem(slot, stack) && isAllowedItem(stack);
    }

    @Override
    public ItemStack addItem(ItemStack stack) {
        // SimpleContainer.addItem bypasses canPlaceItem, including nesting and recovery-slot restrictions.
        ItemStack remaining = stack.copy();
        if (remaining.isEmpty() || !isAllowedItem(remaining)) {
            return remaining;
        }
        boolean changed = false;
        for (int i = 0; i < getContainerSize() && !remaining.isEmpty(); i++) {
            ItemStack existing = getItem(i);
            if (!canPlaceItem(i, remaining) || existing.isEmpty()
                    || !ItemStack.isSameItemSameComponents(existing, remaining)) {
                continue;
            }
            int limit = Math.min(getMaxStackSize(), existing.getMaxStackSize());
            int moved = Math.min(remaining.getCount(), Math.max(0, limit - existing.getCount()));
            if (moved > 0) {
                existing.grow(moved);
                remaining.shrink(moved);
                changed = true;
            }
        }
        for (int i = 0; i < getContainerSize() && !remaining.isEmpty(); i++) {
            if (canPlaceItem(i, remaining) && getItem(i).isEmpty()) {
                int moved = Math.min(remaining.getCount(), Math.min(getMaxStackSize(), remaining.getMaxStackSize()));
                setItem(i, remaining.split(moved));
                changed = true;
            }
        }
        if (changed) {
            setChanged();
        }
        return remaining;
    }

    @Override
    public boolean canTakeItem(Container container, int slot, ItemStack stack) {
        if (container instanceof Hopper) {
            BackpackAugmentsComponent augments = Inmis.getOrCreateAugments(backpackStack, tier);
            if (BackpackAugments.isUnlocked(tier, BackpackAugmentType.HOPPER_BRIDGE)
                    && augments.hopperBridge().enabled()) {
                BackpackAugmentsComponent.HopperBridgeSettings settings = augments.hopperBridge();
                if (!settings.extract()) {
                    return false;
                }
                if (settings.filterMode().checkExtract() && isFilteredOut(stack, settings.filters())) {
                    return false;
                }
            }
        }
        return super.canTakeItem(container, slot, stack);
    }

    public ItemStack findFirst(Predicate<ItemStack> predicate) {
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack stack = getItem(i);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public boolean isAllowedItem(ItemStack stack) {
        return isAllowedStack(stack);
    }

    public static boolean isAllowedStack(ItemStack stack) {
        if (stack.getItem() instanceof BackpackItem) {
            return false;
        }
        if (Inmis.CONFIG.unstackablesOnly && stack.getMaxStackSize() > 1) {
            return false;
        }
        if (Inmis.CONFIG.disableShulkers && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock) {
            return false;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null && Inmis.CONFIG.blacklist != null && Inmis.CONFIG.blacklist.contains(id.toString())) {
            return false;
        }
        return true;
    }

    private boolean isFilteredOut(ItemStack stack, List<Identifier> filters) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null || !filters.contains(id);
    }
}
