package draylar.inmis.ui;

import draylar.inmis.Inmis;
import draylar.inmis.api.Dimension;
import draylar.inmis.api.Point;
import draylar.inmis.augment.BackpackAugmentHelper;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.util.BackpackStorage;
import net.minecraft.network.PacketBuffer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class BackpackScreenHandler extends Container {

    private final ItemStack backpackStack;
    private final int rowWidth;
    private final int numberOfRows;
    private final int configuredSize;
    private final int lockedPlayerSlot;
    private final IInventory backpackInventory;
    private final int padding = 8;
    private final int titleSpace = 10;

    public BackpackScreenHandler(int synchronizationID, InventoryPlayer playerInventory, PacketBuffer packetByteBuf) {
        this(synchronizationID, playerInventory, readOpeningStack(packetByteBuf),
                packetByteBuf.readVarInt(), packetByteBuf.readVarInt(), packetByteBuf.readVarInt(),
                packetByteBuf.readInt(), true);
    }

    public BackpackScreenHandler(int synchronizationID, InventoryPlayer playerInventory, ItemStack backpackStack) {
        this(synchronizationID, playerInventory, backpackStack,
                ((BackpackItem) backpackStack.getItem()).getTier().getRowWidth(),
                BackpackStorage.getRequiredRows(backpackStack, ((BackpackItem) backpackStack.getItem()).getTier()),
                Math.multiplyExact(((BackpackItem) backpackStack.getItem()).getTier().getRowWidth(),
                        ((BackpackItem) backpackStack.getItem()).getTier().getNumberOfRows()),
                findPlayerSlot(playerInventory, backpackStack), false);
    }

    private BackpackScreenHandler(int synchronizationID, InventoryPlayer playerInventory, ItemStack backpackStack,
                                  int rowWidth, int numberOfRows, int configuredSize, int lockedPlayerSlot,
                                  boolean clientMenu) {
        super();
        this.windowId = synchronizationID;
        if (!(backpackStack.getItem() instanceof BackpackItem)
                || rowWidth <= 0 || numberOfRows <= 0 || configuredSize <= 0
                || (long) rowWidth * numberOfRows > Short.MAX_VALUE - 36L
                || configuredSize > (long) rowWidth * numberOfRows
                || rowWidth * 18L + 16 > Integer.MAX_VALUE
                || (numberOfRows + 4L) * 18 + 44 > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid backpack menu dimensions");
        }
        this.backpackStack = backpackStack;
        this.rowWidth = rowWidth;
        this.numberOfRows = numberOfRows;
        this.configuredSize = configuredSize;
        this.lockedPlayerSlot = lockedPlayerSlot;
        // The client receives slots through the normal menu sync; it must never resize saved contents using its config.
        this.backpackInventory = clientMenu
                ? new InventoryBasic("inmis", false, rowWidth * numberOfRows)
                : new BackpackInventory(backpackStack, getItem().getTier(), rowWidth * numberOfRows);
        setupContainer(playerInventory);
    }

    private static ItemStack readOpeningStack(PacketBuffer buffer) {
        try { return buffer.readItemStack(); } catch (java.io.IOException error) { throw new IllegalArgumentException("Invalid backpack stack", error); }
    }

    public static void writeOpeningData(PacketBuffer packetByteBuf, InventoryPlayer playerInventory, ItemStack backpackStack) {
        BackpackInfo tier = ((BackpackItem) backpackStack.getItem()).getTier();
        ItemStack description = backpackStack.copy();
        if (description.hasTagCompound()) description.getTagCompound().removeTag("Inventory");
        packetByteBuf.writeItemStack(description);
        packetByteBuf.writeVarInt(tier.getRowWidth());
        packetByteBuf.writeVarInt(BackpackStorage.getRequiredRows(backpackStack, tier));
        packetByteBuf.writeVarInt(Math.multiplyExact(tier.getRowWidth(), tier.getNumberOfRows()));
        packetByteBuf.writeInt(findPlayerSlot(playerInventory, backpackStack));
    }

    private static int findPlayerSlot(InventoryPlayer playerInventory, ItemStack backpackStack) {
        for (int slot = 0; slot < playerInventory.getSizeInventory(); slot++) {
            if (playerInventory.getStackInSlot(slot) == backpackStack) {
                return slot;
            }
        }
        return -1;
    }

    private void setupContainer(InventoryPlayer playerInventory) {
        Dimension dimension = getDimension();
        for (int y = 0; y < numberOfRows; y++) {
            for (int x = 0; x < rowWidth; x++) {
                Point position = getBackpackSlotPosition(dimension, x, y);
                addSlotToContainer(new BackpackLockedSlot(backpackInventory, y * rowWidth + x, position.x + 1, position.y + 1, false));
            }
        }
        for (int y = 0; y < 3; ++y) {
            for (int x = 0; x < 9; ++x) {
                int slot = x + y * 9 + 9;
                Point position = getPlayerInvSlotPosition(dimension, x, y);
                addSlotToContainer(new BackpackLockedSlot(playerInventory, slot, position.x + 1, position.y + 1, slot == lockedPlayerSlot));
            }
        }
        for (int x = 0; x < 9; ++x) {
            Point position = getPlayerInvSlotPosition(dimension, x, 3);
            addSlotToContainer(new BackpackLockedSlot(playerInventory, x, position.x + 1, position.y + 1, x == lockedPlayerSlot));
        }
    }

    public BackpackItem getItem() {
        return (BackpackItem) backpackStack.getItem();
    }

    public BackpackInventory getBackpackInventory() {
        return backpackInventory instanceof BackpackInventory ? (BackpackInventory) backpackInventory : null;
    }

    public Dimension getDimension() {
        return new Dimension(padding * 2 + Math.max(rowWidth, 9) * 18,
                padding * 2 + titleSpace * 2 + 8 + (numberOfRows + 4) * 18);
    }

    public Point getBackpackSlotPosition(Dimension dimension, int x, int y) {
        return new Point(dimension.getWidth() / 2 - rowWidth * 9 + x * 18, padding + titleSpace + y * 18);
    }

    public Point getPlayerInvSlotPosition(Dimension dimension, int x, int y) {
        return new Point(dimension.getWidth() / 2 - 9 * 9 + x * 18,
                dimension.getHeight() - padding - 4 * 18 - 3 + y * 18 + (y == 3 ? 4 : 0));
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (player.world.isRemote) {
            return true;
        }
        for (ItemStack ownedBackpack : BackpackAugmentHelper.getBackpackStacks(player)) {
            if (ownedBackpack == backpackStack) {
                return true;
            }
        }
        return false;
    }

    public ItemStack getBackpackStack() {
        return backpackStack;
    }

    @Override
    public ItemStack slotClick(int slotId, int button, ClickType clickType, EntityPlayer player) {
        // Number-key/offhand swaps access the player inventory directly and can bypass the hovered slot's lock.
        if (clickType == ClickType.SWAP && button == lockedPlayerSlot) {
            return ItemStack.EMPTY;
        }
        return super.slotClick(slotId, button, clickType, player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (index < 0 || index >= inventorySlots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = inventorySlots.get(index);
        if (!slot.getHasStack() || !slot.canTakeStack(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack toInsert = slot.getStack();
        if (index >= rowWidth * numberOfRows && !BackpackInventory.isAllowedStack(toInsert)) {
            return ItemStack.EMPTY;
        }
        ItemStack original = toInsert.copy();
        int backpackSize = rowWidth * numberOfRows;
        boolean moved = index < backpackSize
                ? mergeItemStack(toInsert, backpackSize, inventorySlots.size(), true)
                : mergeItemStack(toInsert, 0, configuredSize, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (toInsert.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        slot.onTake(player, toInsert);
        return original;
    }

    private class BackpackLockedSlot extends Slot {

        private final boolean locksOpenBackpack;

        private BackpackLockedSlot(IInventory inventory, int index, int x, int y, boolean locksOpenBackpack) {
            super(inventory, index, x, y);
            this.locksOpenBackpack = locksOpenBackpack;
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            // Previously collected backpacks must remain removable; only the backpack being viewed is locked.
            return !locksOpenBackpack && getStack() != backpackStack;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            if (locksOpenBackpack || stack == backpackStack) {
                return false;
            }
            if (inventory == backpackInventory) {
                // Extra rows recover contents after a capacity reduction and do not add usable storage.
                return getSlotIndex() < configuredSize
                        && BackpackInventory.isAllowedStack(stack);
            }
            return true;
        }
    }
}
