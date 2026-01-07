package draylar.inmis.ui;

import draylar.inmis.Inmis;
import draylar.inmis.api.Dimension;
import draylar.inmis.api.Point;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.List;

public class BackpackScreenHandler extends AbstractContainerMenu {

    private final ItemStack backpackStack;
    private final int padding = 8;
    private final int titleSpace = 10;

    public BackpackScreenHandler(int synchronizationID, Inventory playerInventory, RegistryFriendlyByteBuf packetByteBuf) {
        this(synchronizationID, playerInventory, ItemStack.STREAM_CODEC.decode(packetByteBuf));
    }

    public BackpackScreenHandler(int synchronizationID, Inventory playerInventory, ItemStack backpackStack) {
        super(Inmis.CONTAINER_TYPE.get(), synchronizationID);
        this.backpackStack = backpackStack;

        if (backpackStack.getItem() instanceof BackpackItem) {
            setupContainer(playerInventory, backpackStack);
        } else {
            Player player = playerInventory.player;
            this.removed(player);
        }
    }

    private void setupContainer(Inventory playerInventory, ItemStack backpackStack) {
        Dimension dimension = getDimension();
        BackpackInfo tier = getItem().getTier();
        int rowWidth = tier.getRowWidth();
        int numberOfRows = tier.getNumberOfRows();
        int size = rowWidth * numberOfRows;

        BackpackComponent component = Inmis.getOrCreateComponent(backpackStack, tier);

        BackpackInventory inventory = new BackpackInventory(size) {
            @Override
            public void setChanged() {
                backpackStack.set(Inmis.BACKPACK_COMPONENT.get(), BackpackComponent.fromContainer(this));
                super.setChanged();
            }
        };

        List<ItemStack> stacks = component.stacks();
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
        }

        for (int y = 0; y < numberOfRows; y++) {
            for (int x = 0; x < rowWidth; x++) {
                Point backpackSlotPosition = getBackpackSlotPosition(dimension, x, y);
                addSlot(new BackpackLockedSlot(inventory, y * rowWidth + x, backpackSlotPosition.x + 1, backpackSlotPosition.y + 1));
            }
        }

        for (int y = 0; y < 3; ++y) {
            for (int x = 0; x < 9; ++x) {
                Point playerInvSlotPosition = getPlayerInvSlotPosition(dimension, x, y);
                this.addSlot(new BackpackLockedSlot(playerInventory, x + y * 9 + 9, playerInvSlotPosition.x + 1, playerInvSlotPosition.y + 1));
            }
        }

        for (int x = 0; x < 9; ++x) {
            Point playerInvSlotPosition = getPlayerInvSlotPosition(dimension, x, 3);
            this.addSlot(new BackpackLockedSlot(playerInventory, x, playerInvSlotPosition.x + 1, playerInvSlotPosition.y + 1));
        }

        backpackStack.set(Inmis.BACKPACK_COMPONENT.get(), BackpackComponent.fromContainer(inventory));
    }

    public BackpackItem getItem() {
        return (BackpackItem) backpackStack.getItem();
    }

    public Dimension getDimension() {
        BackpackInfo tier = getItem().getTier();
        return new Dimension(padding * 2 + Math.max(tier.getRowWidth(), 9) * 18,
                padding * 2 + titleSpace * 2 + 8 + (tier.getNumberOfRows() + 4) * 18);
    }

    public Point getBackpackSlotPosition(Dimension dimension, int x, int y) {
        BackpackInfo tier = getItem().getTier();
        return new Point(dimension.getWidth() / 2 - tier.getRowWidth() * 9 + x * 18, padding + titleSpace + y * 18);
    }

    public Point getPlayerInvSlotPosition(Dimension dimension, int x, int y) {
        return new Point(dimension.getWidth() / 2 - 9 * 9 + x * 18,
                dimension.getHeight() - padding - 4 * 18 - 3 + y * 18 + (y == 3 ? 4 : 0));
    }

    @Override
    public boolean stillValid(Player player) {
        return backpackStack.getItem() instanceof BackpackItem;
    }

    public ItemStack getBackpackStack() {
        return backpackStack;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack toInsert = slot.getItem();
            itemStack = toInsert.copy();
            BackpackInfo tier = getItem().getTier();
            if (index < tier.getNumberOfRows() * tier.getRowWidth()) {
                if (!this.moveItemStackTo(toInsert, tier.getNumberOfRows() * tier.getRowWidth(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(toInsert, 0, tier.getNumberOfRows() * tier.getRowWidth(), false)) {
                return ItemStack.EMPTY;
            }

            if (toInsert.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemStack;
    }

    private class BackpackLockedSlot extends Slot {

        public BackpackLockedSlot(Container inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return stackMovementIsAllowed(getItem());
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (Inmis.CONFIG.unstackablesOnly) {
                if (stack.getMaxStackSize() > 1) {
                    return false;
                }
            }

            if (Inmis.CONFIG.disableShulkers && container instanceof BackpackInventory) {
                Item item = stack.getItem();
                if (item instanceof BlockItem blockItem) {
                    return !(blockItem.getBlock() instanceof ShulkerBoxBlock);
                }
            }

            if (container instanceof BackpackInventory) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id != null && Inmis.CONFIG.blacklist != null && Inmis.CONFIG.blacklist.contains(id.toString())) {
                    return false;
                }
            }

            return stackMovementIsAllowed(stack);
        }

        private boolean stackMovementIsAllowed(ItemStack stack) {
            return !(stack.getItem() instanceof BackpackItem) && stack != backpackStack;
        }
    }

    public static class BackpackInventory extends SimpleContainer {

        public BackpackInventory(int slots) {
            super(slots);
        }
    }
}
