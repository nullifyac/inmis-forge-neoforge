package draylar.inmis.ui;

import draylar.inmis.api.Dimension;
import draylar.inmis.api.Point;
import draylar.inmis.augment.BackpackAugmentHelper;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.network.ServerNetworking.OpeningData;
import draylar.inmis.util.BackpackStorage;
import draylar.inmis.util.InventoryUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public final class BackpackScreenHandler extends Container {
    private final ItemStack backpackStack;
    private final int rowWidth,numberOfRows,configuredSize,lockedPlayerSlot;
    private final IInventory backpackInventory;
    public BackpackScreenHandler(InventoryPlayer inventory,ItemStack stack){
        this(inventory,OpeningData.forStack(inventory,stack),false);
    }
    public BackpackScreenHandler(InventoryPlayer inventory,OpeningData data){this(inventory,data,true);}
    private BackpackScreenHandler(InventoryPlayer inventory,OpeningData data,boolean client){
        data.validate();backpackStack=data.stack;rowWidth=data.width;numberOfRows=data.rows;configuredSize=data.configuredSize;lockedPlayerSlot=data.lockedPlayerSlot;
        backpackInventory=client?new InventoryBasic(backpackStack.getDisplayName(),true,rowWidth*numberOfRows):new BackpackInventory(backpackStack,getItem().getTier(),rowWidth*numberOfRows);
        Dimension dimension=getDimension();
        for(int y=0;y<numberOfRows;y++)for(int x=0;x<rowWidth;x++){Point p=getBackpackSlotPosition(dimension,x,y);addSlotToContainer(new LockedSlot(backpackInventory,y*rowWidth+x,p.x+1,p.y+1,false));}
        for(int y=0;y<3;y++)for(int x=0;x<9;x++){int slot=x+y*9+9;Point p=getPlayerInvSlotPosition(dimension,x,y);addSlotToContainer(new LockedSlot(inventory,slot,p.x+1,p.y+1,slot==lockedPlayerSlot));}
        for(int x=0;x<9;x++){Point p=getPlayerInvSlotPosition(dimension,x,3);addSlotToContainer(new LockedSlot(inventory,x,p.x+1,p.y+1,x==lockedPlayerSlot));}
    }
    public BackpackItem getItem(){return(BackpackItem)backpackStack.getItem();}
    public ItemStack getBackpackStack(){return backpackStack;}
    public BackpackInventory getBackpackInventory(){return backpackInventory instanceof BackpackInventory?(BackpackInventory)backpackInventory:null;}
    public Dimension getDimension(){return new Dimension(16+Math.max(rowWidth,9)*18,44+(numberOfRows+4)*18);}
    public Point getBackpackSlotPosition(Dimension dimension,int x,int y){return new Point(dimension.getWidth()/2-rowWidth*9+x*18,18+y*18);}
    public Point getPlayerInvSlotPosition(Dimension dimension,int x,int y){return new Point(dimension.getWidth()/2-81+x*18,dimension.getHeight()-83+y*18+(y==3?4:0));}
    @Override public boolean canInteractWith(EntityPlayer player){
        if(player.worldObj.isRemote)return true;
        for(ItemStack stack:BackpackAugmentHelper.getBackpackStacks(player))if(stack==backpackStack)return true;
        return false;
    }
    @Override public ItemStack slotClick(int slotId,int button,int mode,EntityPlayer player){
        if(mode==2&&button==lockedPlayerSlot)return null;
        if(slotId>=inventorySlots.size()||slotId< -999)return null;
        return super.slotClick(slotId,button,mode,player);
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player,int index){
        if(index<0||index>=inventorySlots.size())return null;Slot slot=(Slot)inventorySlots.get(index);
        if(!slot.getHasStack()||!slot.canTakeStack(player))return null;ItemStack stack=slot.getStack();
        int size=rowWidth*numberOfRows;
        if(index>=size&&!BackpackInventory.isAllowedStack(stack))return null;
        ItemStack original=stack.copy();
        boolean moved=index<size?mergeItemStack(stack,size,inventorySlots.size(),true):mergeItemStack(stack,0,configuredSize,false);
        if(!moved)return null;if(stack.stackSize<=0)slot.putStack(null);else slot.onSlotChanged();slot.onPickupFromSlot(player,stack);return original;
    }
    private final class LockedSlot extends Slot {
        private final boolean locked;
        LockedSlot(IInventory inventory,int slot,int x,int y,boolean locked){super(inventory,slot,x,y);this.locked=locked;}
        @Override public boolean canTakeStack(EntityPlayer player){return !locked&&getStack()!=backpackStack;}
        @Override public boolean isItemValid(ItemStack stack){return !locked&&stack!=backpackStack
                &&(inventory!=backpackInventory||(getSlotIndex()<configuredSize&&BackpackInventory.isAllowedStack(stack)));}
    }
}
