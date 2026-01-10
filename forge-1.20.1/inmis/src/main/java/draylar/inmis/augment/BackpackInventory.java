package draylar.inmis.augment;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.util.InventoryUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
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

    public BackpackInventory(ItemStack backpackStack, BackpackInfo tier) {
        super(Math.max(0, tier.getRowWidth() * tier.getNumberOfRows()));
        this.backpackStack = backpackStack;
        this.tier = tier;

        ListTag tag = Inmis.getOrCreateInventory(backpackStack, tier);
        InventoryUtils.fromTag(tag, this);
    }

    public ItemStack getBackpackStack() {
        return backpackStack;
    }

    @Override
    public void setChanged() {
        backpackStack.getOrCreateTag().put("Inventory", InventoryUtils.toTag(this));
        super.setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return super.canPlaceItem(slot, stack) && isAllowedItem(stack);
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
        if (stack.getItem() instanceof BackpackItem) {
            return false;
        }
        if (Inmis.CONFIG.unstackablesOnly && stack.getMaxStackSize() > 1) {
            return false;
        }
        if (Inmis.CONFIG.disableShulkers && stack.getItem() instanceof BlockItem blockItem) {
            return !(blockItem.getBlock() instanceof ShulkerBoxBlock);
        }
        return true;
    }

    private boolean isFilteredOut(ItemStack stack, List<ResourceLocation> filters) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null || !filters.contains(id);
    }
}
