package draylar.inmis.augment;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.util.InventoryUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.BlockHopper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.inventory.IInventory;
import net.minecraft.util.Facing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.IPlantable;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

public final class BackpackAugmentHandler {
    private BackpackAugmentHandler(){ }
    private static BackpackInventory inventory(EntityPlayer player,ItemStack stack,BackpackInfo tier){
        if(player.openContainer instanceof draylar.inmis.ui.BackpackScreenHandler){
            draylar.inmis.ui.BackpackScreenHandler menu=(draylar.inmis.ui.BackpackScreenHandler)player.openContainer;
            if(menu.getBackpackStack()==stack&&menu.getBackpackInventory()!=null)return menu.getBackpackInventory();
        }
        return new BackpackInventory(stack,tier);
    }
    public static List<BackpackInventory> eligible(EntityPlayer player,BackpackAugmentType type){
        List<BackpackInventory> result=new ArrayList<>();
        for(ItemStack stack:BackpackAugmentHelper.getBackpackStacks(player)){
            BackpackInfo tier=((BackpackItem)stack.getItem()).getTier();BackpackAugmentsComponent settings=Inmis.getOrCreateAugments(stack,tier);
            boolean enabled;
            switch(type){
                case FUNNELLING:enabled=settings.funnelling().enabled();break;
                case QUIVERLINK:enabled=settings.quiverlink().enabled();break;
                case LOOTBOUND:enabled=settings.lootbound().enabled()&&settings.funnelling().enabled();break;
                case LIGHTWEAVER:enabled=settings.lightweaver().enabled();break;
                case FARMHAND:enabled=settings.farmhandEnabled();break;
                case SEEDFLOW:enabled=settings.seedflow().enabled();break;
                case HOPPER_BRIDGE:enabled=settings.hopperBridge().enabled();break;
                case IMBUED_HIDE:enabled=settings.imbuedHideEnabled();break;
                default:enabled=false;
            }
            if(enabled&&BackpackAugments.isUnlocked(tier,type))result.add(inventory(player,stack,tier));
        }
        return result;
    }
    public static ResourceLocation itemId(ItemStack stack){return new ResourceLocation(Item.itemRegistry.getNameForObject(stack.getItem()));}
    public static boolean funnelAccepts(BackpackInventory inventory,ItemStack stack){
        BackpackAugmentsComponent.FunnellingSettings settings=Inmis.getOrCreateAugments(inventory.getBackpackStack(),inventory.getTier()).funnelling();
        boolean match=settings.filters().contains(itemId(stack));
        return settings.filters().isEmpty()||((settings.mode()==BackpackAugmentsComponent.FunnellingSettings.Mode.ALLOW)==match);
    }
    public static ItemStack funnel(EntityPlayer player,ItemStack stack,boolean loot,boolean block){
        ItemStack left=stack.copy();
        for(BackpackInventory inventory:eligible(player,loot?BackpackAugmentType.LOOTBOUND:BackpackAugmentType.FUNNELLING)){
            BackpackAugmentsComponent settings=Inmis.getOrCreateAugments(inventory.getBackpackStack(),inventory.getTier());
            if(loot&&(block?!settings.lootbound().blocks():!settings.lootbound().mobs()))continue;
            if(funnelAccepts(inventory,left)){left=inventory.addItem(left);if(InventoryUtils.isEmpty(left))return null;}
        }
        return left;
    }
    public static BackpackInventory findQuiver(EntityPlayer player){
        for(BackpackInventory inventory:eligible(player,BackpackAugmentType.QUIVERLINK)){
            BackpackAugmentsComponent.QuiverlinkSettings settings=Inmis.getOrCreateAugments(inventory.getBackpackStack(),inventory.getTier()).quiverlink();
            if(settings.priority()==BackpackAugmentsComponent.QuiverlinkSettings.Priority.INVENTORY&&player.inventory.hasItem(Items.arrow))continue;
            for(int i=0;i<inventory.getSizeInventory();i++){ItemStack stack=inventory.getStackInSlot(i);if(!InventoryUtils.isEmpty(stack)&&stack.getItem()==Items.arrow)return inventory;}
        }
        return null;
    }
    public static boolean consumeArrow(BackpackInventory inventory){
        for(int i=0;i<inventory.getSizeInventory();i++){ItemStack stack=inventory.getStackInSlot(i);if(!InventoryUtils.isEmpty(stack)&&stack.getItem()==Items.arrow){inventory.decrStackSize(i,1);return true;}}
        return false;
    }
    public static void tick(EntityPlayer player){
        if(player.ticksExisted%5!=0)return;
        int x=MathHelper.floor_double(player.posX),y=MathHelper.floor_double(player.posY),z=MathHelper.floor_double(player.posZ);
        lightweaver(player,x,y,z);seedflow(player,x,y,z);hopperBridge(player,x,y,z);
    }
    private static void lightweaver(EntityPlayer player,int x,int y,int z){
        if(!player.worldObj.isAirBlock(x,y,z)||!Blocks.torch.canPlaceBlockAt(player.worldObj,x,y,z))return;
        for(BackpackInventory inventory:eligible(player,BackpackAugmentType.LIGHTWEAVER)){
            BackpackAugmentsComponent.LightweaverSettings settings=Inmis.getOrCreateAugments(inventory.getBackpackStack(),inventory.getTier()).lightweaver();
            if(player.worldObj.getBlockLightValue(x,y,z)>settings.minimumLight())continue;
            for(int i=0;i<inventory.getSizeInventory();i++){ItemStack torch=inventory.getStackInSlot(i);if(InventoryUtils.isEmpty(torch)||torch.getItem()!=Item.getItemFromBlock(Blocks.torch))continue;
                if(player.canPlayerEdit(x,y,z,1,torch)&&PlacementSounds.placeTorch(player,torch,x,y,z,settings.placeSound())){inventory.markDirty();return;}
            }
        }
    }
    private static void seedflow(EntityPlayer player,int x,int y,int z){
        net.minecraft.util.Vec3 look=player.getLookVec();double length=Math.sqrt(look.xCoord*look.xCoord+look.zCoord*look.zCoord);
        double fx=length<1e-6?0:look.xCoord/length,fz=length<1e-6?1:look.zCoord/length;
        int originX=MathHelper.floor_double(player.posX+fx),originZ=MathHelper.floor_double(player.posZ+fz);
        for(int dx=0;dx<2;dx++)for(int dz=0;dz<2;dz++){
            int targetX=originX+dx,targetZ=originZ+dz;if(!player.worldObj.isAirBlock(targetX,y,targetZ))continue;
            for(BackpackInventory inventory:eligible(player,BackpackAugmentType.SEEDFLOW)){
                BackpackAugmentsComponent.SeedflowSettings settings=Inmis.getOrCreateAugments(inventory.getBackpackStack(),inventory.getTier()).seedflow();
                List<Integer> candidates=new ArrayList<>();
                for(int i=0;i<inventory.getSizeInventory();i++){ItemStack seed=inventory.getStackInSlot(i);if(InventoryUtils.isEmpty(seed)||!(seed.getItem() instanceof IPlantable))continue;
                    if(settings.useFilters()&&!settings.filters().contains(itemId(seed)))continue;
                    Block plant=((IPlantable)seed.getItem()).getPlant(player.worldObj,targetX,y,targetZ);
                    if((plant instanceof BlockCrops||plant instanceof BlockNetherWart)&&plant.canPlaceBlockAt(player.worldObj,targetX,y,targetZ))candidates.add(i);
                }
                if(candidates.isEmpty())continue;
                int slot=candidates.get(settings.randomizeSeeds()?player.worldObj.rand.nextInt(candidates.size()):0);
                if(place(player,inventory.getStackInSlot(slot),targetX,y,targetZ)){inventory.markDirty();break;}
            }
        }
    }
    public static void replant(EntityPlayer player,Block harvested,int x,int y,int z){
        if(player.worldObj.isRemote||!player.worldObj.isAirBlock(x,y,z))return;
        for(BackpackInventory inventory:eligible(player,BackpackAugmentType.FARMHAND))for(int i=0;i<inventory.getSizeInventory();i++){
            ItemStack seed=inventory.getStackInSlot(i);if(InventoryUtils.isEmpty(seed)||!(seed.getItem() instanceof IPlantable))continue;
            if(((IPlantable)seed.getItem()).getPlant(player.worldObj,x,y,z)==harvested&&place(player,seed,x,y,z)){inventory.markDirty();return;}
        }
    }
    /** Uses Forge's native placement transaction so protection mods can reject automatic planting. */
    private static boolean place(EntityPlayer player,ItemStack stack,int x,int y,int z){
        if(!player.worldObj.isAirBlock(x,y,z)||!player.canPlayerEdit(x,y,z,1,stack))return false;
        return net.minecraftforge.common.ForgeHooks.onPlaceItemIntoWorld(stack,player,player.worldObj,x,y-1,z,1,.5F,1F,.5F);
    }
    private static void hopperBridge(EntityPlayer player,int x,int y,int z){
        ItemStack worn=player.inventory.armorInventory[2];
        if(!(worn!=null&&worn.getItem() instanceof BackpackItem))worn=Inmis.CONFIG.enableTrinketCompatibility?draylar.inmis.compat.BaublesCompat.findFirstEquippedBackpack(player):null;
        if(worn==null)return;BackpackInfo tier=((BackpackItem)worn.getItem()).getTier();
        BackpackAugmentsComponent.HopperBridgeSettings settings=Inmis.getOrCreateAugments(worn,tier).hopperBridge();
        if(!settings.enabled()||!BackpackAugments.isUnlocked(tier,BackpackAugmentType.HOPPER_BRIDGE))return;
        BackpackInventory inventory=inventory(player,worn,tier);
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=0;dy++)for(int dz=-1;dz<=1;dz++){
            if(Math.abs(dx)+Math.abs(dz)>1)continue;
            TileEntity tile=player.worldObj.getTileEntity(x+dx,y+dy,z+dz);if(!(tile instanceof TileEntityHopper))continue;
            TileEntityHopper hopper=(TileEntityHopper)tile;if(!BlockHopper.func_149917_c(hopper.getBlockMetadata())||hopper.func_145888_j())continue;
            boolean moved=false;
            if(dx==0&&dz==0&&dy==-1&&settings.extract())moved=transfer(inventory,hopper,settings,false);
            int direction=BlockHopper.getDirectionFromMetadata(hopper.getBlockMetadata());
            if(!moved&&settings.insert()&&x+dx+Facing.offsetsXForSide[direction]==x&&y+dy+Facing.offsetsYForSide[direction]==y&&z+dz+Facing.offsetsZForSide[direction]==z)moved=transfer(hopper,inventory,settings,true);
            if(moved){hopper.func_145896_c(8);hopper.markDirty();inventory.markDirty();}
        }
    }
    private static boolean transfer(IInventory from,IInventory to,BackpackAugmentsComponent.HopperBridgeSettings settings,boolean insert){
        for(int i=0;i<from.getSizeInventory();i++){
            ItemStack source=from.getStackInSlot(i);if(InventoryUtils.isEmpty(source))continue;
            if((insert?settings.filterMode().checkInsert():settings.filterMode().checkExtract())&&!settings.filters().contains(itemId(source)))continue;
            for(int j=0;j<to.getSizeInventory();j++){
                if(!to.isItemValidForSlot(j,source))continue;ItemStack target=to.getStackInSlot(j);
                if(target==null){ItemStack one=source.copy();one.stackSize=1;to.setInventorySlotContents(j,one);from.decrStackSize(i,1);return true;}
                if(target.isItemEqual(source)&&ItemStack.areItemStackTagsEqual(target,source)&&target.stackSize<Math.min(to.getInventoryStackLimit(),target.getMaxStackSize())){
                    target.stackSize++;from.decrStackSize(i,1);to.markDirty();return true;
                }
            }
        }
        return false;
    }
    public static void spillSelectedDrop(List<EntityItem> drops,EntityItem drop){
        ItemStack bag=drop.getEntityItem();if(!(bag!=null&&bag.getItem() instanceof BackpackItem))return;
        BackpackInventory inventory=new BackpackInventory(bag,((BackpackItem)bag.getItem()).getTier());
        for(int i=0;i<inventory.getSizeInventory();i++){ItemStack contents=inventory.getStackInSlot(i);if(!InventoryUtils.isEmpty(contents)){
            EntityItem item=new EntityItem(drop.worldObj,drop.posX,drop.posY,drop.posZ,contents.copy());item.delayBeforeCanPickup=drop.delayBeforeCanPickup;item.motionX=drop.motionX;item.motionY=drop.motionY;item.motionZ=drop.motionZ;drops.add(item);
        }}
        Inmis.wipeBackpack(bag);
    }
}
