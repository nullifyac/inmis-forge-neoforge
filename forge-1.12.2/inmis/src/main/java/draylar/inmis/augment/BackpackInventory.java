package draylar.inmis.augment;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.util.BackpackStorage;
import draylar.inmis.util.InventoryUtils;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.util.EnumFacing;
import javax.annotation.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class BackpackInventory extends InventoryBasic implements ISidedInventory {

    private final ItemStack backpackStack;
    private final BackpackInfo tier;
    private boolean loading = true;
    private final int[] availableSlots;

    public BackpackInventory(ItemStack backpackStack, BackpackInfo tier) {
        this(backpackStack, tier, BackpackStorage.getRequiredSize(backpackStack, tier));
    }

    public BackpackInventory(ItemStack backpackStack, BackpackInfo tier, int size) {
        super("inmis", false, Math.max(0, size));
        this.backpackStack = backpackStack;
        this.tier = tier;

        this.availableSlots = new int[getSizeInventory()];
        for (int i = 0; i < availableSlots.length; i++) {
            availableSlots[i] = i;
        }

        NBTTagList tag = Inmis.getOrCreateInventory(backpackStack, tier);
        InventoryUtils.fromTag(tag, this);
        loading = false;
    }

    public ItemStack getBackpackStack() {
        return backpackStack;
    }

    @Override
    public void markDirty() {
        if (loading) {
            return;
        }
        Inmis.tag(backpackStack).setTag("Inventory", InventoryUtils.toTag(this));
        super.markDirty();
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot >= 0 && slot < tier.getRowWidth() * tier.getNumberOfRows()
                && slot < getSizeInventory() && super.isItemValidForSlot(slot, stack) && isAllowedItem(stack);
    }

    public ItemStack addItem(ItemStack stack) {
        // InventoryBasic.addItem bypasses canPlaceItem, including nesting and recovery-slot restrictions.
        ItemStack remaining = stack.copy();
        if (remaining.isEmpty() || !isAllowedItem(remaining)) {
            return remaining;
        }
        boolean changed = false;
        for (int i = 0; i < getSizeInventory() && !remaining.isEmpty(); i++) {
            ItemStack existing = getStackInSlot(i);
            if (!isItemValidForSlot(i, remaining) || existing.isEmpty()
                    || !(ItemStack.areItemsEqual(existing, remaining) && ItemStack.areItemStackTagsEqual(existing, remaining))) {
                continue;
            }
            int limit = Math.min(getInventoryStackLimit(), existing.getMaxStackSize());
            int moved = Math.min(remaining.getCount(), Math.max(0, limit - existing.getCount()));
            if (moved > 0) {
                existing.grow(moved);
                remaining.shrink(moved);
                changed = true;
            }
        }
        for (int i = 0; i < getSizeInventory() && !remaining.isEmpty(); i++) {
            if (isItemValidForSlot(i, remaining) && getStackInSlot(i).isEmpty()) {
                int moved = Math.min(remaining.getCount(), Math.min(getInventoryStackLimit(), remaining.getMaxStackSize()));
                setInventorySlotContents(i, remaining.splitStack(moved));
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
        return remaining;
    }

    @Override
    public int[] getSlotsForFace(EnumFacing direction) {
        return availableSlots;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, @Nullable EnumFacing direction) {
        return isItemValidForSlot(slot, stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, EnumFacing direction) {
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
        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack stack = getStackInSlot(i);
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
        if (Inmis.CONFIG.disableShulkers && stack.getItem() instanceof ItemBlock) {
            ItemBlock blockItem = (ItemBlock) stack.getItem();
            return !(blockItem.getBlock() instanceof BlockShulkerBox);
        }
        return true;
    }

    private boolean isFilteredOut(ItemStack stack, List<ResourceLocation> filters) {
        ResourceLocation id = stack.getItem().getRegistryName();
        return id == null || !filters.contains(id);
    }
}
