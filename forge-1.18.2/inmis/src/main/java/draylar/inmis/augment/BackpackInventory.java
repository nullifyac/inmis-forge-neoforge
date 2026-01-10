package draylar.inmis.augment;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.util.InventoryUtils;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.core.Direction;
import javax.annotation.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class BackpackInventory extends SimpleContainer implements WorldlyContainer {

    private final ItemStack backpackStack;
    private final BackpackInfo tier;
    private final int[] availableSlots;

    public BackpackInventory(ItemStack backpackStack, BackpackInfo tier) {
        super(Math.max(0, tier.getRowWidth() * tier.getNumberOfRows()));
        this.backpackStack = backpackStack;
        this.tier = tier;

        this.availableSlots = new int[getContainerSize()];
        for (int i = 0; i < availableSlots.length; i++) {
            availableSlots[i] = i;
        }

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
    public int[] getSlotsForFace(Direction direction) {
        return availableSlots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        BackpackAugmentsComponent augments = Inmis.getOrCreateAugments(backpackStack, tier);
        if (BackpackAugments.isUnlocked(tier, BackpackAugmentType.HOPPER_BRIDGE) && augments.hopperBridge().enabled()) {
            BackpackAugmentsComponent.HopperBridgeSettings settings = augments.hopperBridge();
            if (!settings.extract()) {
                return false;
            }
            if (settings.filterMode().checkExtract() && isFilteredOut(stack, settings.filters())) {
                return false;
            }
        }
        return true;
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
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null || !filters.contains(id);
    }
}
