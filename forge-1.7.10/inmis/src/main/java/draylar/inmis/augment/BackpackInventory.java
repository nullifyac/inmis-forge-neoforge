package draylar.inmis.augment;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.util.BackpackStorage;
import draylar.inmis.util.InventoryUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public final class BackpackInventory implements ISidedInventory {
    private final ItemStack backpackStack;
    private final BackpackInfo tier;
    private final ItemStack[] items;
    private boolean loading = true;
    public BackpackInventory(ItemStack stack, BackpackInfo tier) { this(stack, tier, BackpackStorage.getRequiredSize(stack,tier)); }
    public BackpackInventory(ItemStack stack, BackpackInfo tier, int size) {
        this.backpackStack=stack;this.tier=tier;items=new ItemStack[size];
        InventoryUtils.fromTag(Inmis.getOrCreateInventory(stack,tier),this);loading=false;
    }
    public ItemStack getBackpackStack() { return backpackStack; }
    public BackpackInfo getTier() { return tier; }
    public int getSizeInventory() { return items.length; }
    public ItemStack getStackInSlot(int slot) { return slot>=0&&slot<items.length?items[slot]:null; }
    public ItemStack decrStackSize(int slot,int count) {
        ItemStack stack=getStackInSlot(slot); if (InventoryUtils.isEmpty(stack)||count<=0)return null;
        ItemStack removed=stack.splitStack(Math.min(count,stack.stackSize));
        if(stack.stackSize<=0)items[slot]=null;markDirty();return removed;
    }
    public ItemStack getStackInSlotOnClosing(int slot) { ItemStack stack=getStackInSlot(slot);items[slot]=null;markDirty();return stack; }
    public void setInventorySlotContents(int slot,ItemStack stack) { items[slot]=stack;markDirty(); }
    public String getInventoryName() { return backpackStack.getDisplayName(); }
    public boolean hasCustomInventoryName() { return backpackStack.hasDisplayName(); }
    public int getInventoryStackLimit() { return 64; }
    public void markDirty() { if(!loading)Inmis.tag(backpackStack).setTag("Inventory",InventoryUtils.toTag(this)); }
    public boolean isUseableByPlayer(EntityPlayer player) { return BackpackAugmentHelper.getBackpackStacks(player).contains(backpackStack); }
    public void openInventory() { }
    public void closeInventory() { markDirty(); }
    public boolean isItemValidForSlot(int slot,ItemStack stack) {
        return slot>=0&&slot<items.length&&slot<tier.getRowWidth()*tier.getNumberOfRows()&&isAllowedStack(stack);
    }
    public static boolean isAllowedStack(ItemStack stack) {
        return !InventoryUtils.isEmpty(stack)&&!(stack.getItem() instanceof BackpackItem)
                &&(!Inmis.CONFIG.unstackablesOnly||stack.getMaxStackSize()==1);
    }
    /** Returns an independent remainder; does not alter the caller's stack. */
    public ItemStack addItem(ItemStack input) {
        if(InventoryUtils.isEmpty(input))return null; ItemStack left=input.copy();
        if(!isAllowedStack(left))return left;
        for(int i=0;i<items.length&&left.stackSize>0;i++){
            ItemStack existing=items[i];
            if(isItemValidForSlot(i,left)&&!InventoryUtils.isEmpty(existing)&&existing.isItemEqual(left)&&ItemStack.areItemStackTagsEqual(existing,left)){
                int moved=Math.min(left.stackSize,Math.max(0,Math.min(64,existing.getMaxStackSize())-existing.stackSize));
                existing.stackSize+=moved;left.stackSize-=moved;
            }
        }
        for(int i=0;i<items.length&&left.stackSize>0;i++)if(isItemValidForSlot(i,left)&&InventoryUtils.isEmpty(items[i])){
            int moved=Math.min(left.stackSize,Math.min(64,left.getMaxStackSize()));items[i]=left.splitStack(moved);
        }
        markDirty();return left.stackSize<=0?null:left;
    }
    public int[] getAccessibleSlotsFromSide(int side) { int[] slots=new int[items.length];for(int i=0;i<slots.length;i++)slots[i]=i;return slots; }
    public boolean canInsertItem(int slot,ItemStack stack,int side) { return isItemValidForSlot(slot,stack); }
    public boolean canExtractItem(int slot,ItemStack stack,int side) {
        BackpackAugmentsComponent.HopperBridgeSettings settings=Inmis.getOrCreateAugments(backpackStack,tier).hopperBridge();
        if(!settings.enabled())return true;
        return settings.extract()&&(!settings.filterMode().checkExtract()||settings.filters().contains(new ResourceLocation(net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()))));
    }
}
