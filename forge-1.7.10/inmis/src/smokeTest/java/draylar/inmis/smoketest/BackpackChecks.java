package draylar.inmis.smoketest;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.DyeableBackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.augment.BackpackAugments;
import draylar.inmis.augment.BackpackAugmentType;
import draylar.inmis.network.ServerNetworking;
import draylar.inmis.network.LegacyBuffer;
import draylar.inmis.ui.BackpackScreenHandler;
import draylar.inmis.util.BackpackStorage;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldServer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Container;
import net.minecraft.item.crafting.CraftingManager;
import io.netty.buffer.Unpooled;

public final class BackpackChecks {
    static ItemStack bag(String name){for(BackpackItem item:Inmis.BACKPACK_ITEMS)if(item.getTier().getName().equals(name))return new ItemStack(item);throw new AssertionError("Missing tier "+name);}
    static BackpackInventory inventory(ItemStack bag){return new BackpackInventory(bag,((BackpackItem)bag.getItem()).getTier());}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void registered_tiers(WorldServer world){check(Inmis.BACKPACK_ITEMS.size()==8,"Default tiers missing");check(BackpackAugments.getUnlocked(((BackpackItem)bag("endless").getItem()).getTier()).size()==8,"Supported augments must total eight");check(Inmis.ENDER_POUCH!=null,"Ender pouch missing");}
    public static void storage_nbt_restart_roundtrip(WorldServer world){
        ItemStack bag=bag("withered");ItemStack armor=new ItemStack(Items.diamond_chestplate);armor.setStackDisplayName("Persistence armor");armor.setItemDamage(23);armor.addEnchantment(Enchantment.protection,3);
        inventory(bag).setInventorySlotContents(65,armor);ItemStack loaded=ItemStack.loadItemStackFromNBT(bag.writeToNBT(new NBTTagCompound()));ItemStack actual=inventory(loaded).getStackInSlot(65);
        check(ItemStack.areItemStacksEqual(armor,actual),"Late named enchanted damaged item changed across native NBT serialization");
    }
    public static void capacity_shrink_recovery(WorldServer world){
        ItemStack bag=bag("withered");inventory(bag).setInventorySlotContents(65,new ItemStack(Items.diamond,7));BackpackInfo smaller=new BackpackInfo("withered",9,1,false,"random.chestopen");BackpackInventory reduced=new BackpackInventory(bag,smaller);
        check(reduced.getSizeInventory()==72&&reduced.getStackInSlot(65).stackSize==7,"Capacity shrink lost late items");check(!reduced.isItemValidForSlot(65,new ItemStack(Items.diamond)),"Recovery slots permit insertion");check(reduced.decrStackSize(65,7).stackSize==7,"Recovery item not removable");
    }
    public static void nested_backpack_insertion_rejected(WorldServer world){ItemStack bag=bag("frayed");check(inventory(bag).addItem(bag("baby"))!=null,"Nested bag inserted");check(inventory(bag).getStackInSlot(0)==null,"Nested bag mutated storage");}
    public static void unstackable_filter(WorldServer world){boolean old=Inmis.CONFIG.unstackablesOnly;try{Inmis.CONFIG.unstackablesOnly=true;check(inventory(bag("frayed")).addItem(new ItemStack(Items.diamond))!=null,"Stackable inserted with restriction");check(inventory(bag("frayed")).addItem(new ItemStack(Items.diamond_sword))==null,"Unstackable rejected");}finally{Inmis.CONFIG.unstackablesOnly=old;}}
    public static void malformed_slot_preserved(WorldServer world){ItemStack bag=bag("frayed");NBTTagList contents=new NBTTagList();NBTTagCompound entry=new NBTTagCompound();entry.setInteger("Slot",-1);entry.setTag("Stack",new ItemStack(Items.diamond).writeToNBT(new NBTTagCompound()));contents.appendTag(entry);Inmis.tag(bag).setTag("Inventory",contents);try{inventory(bag);throw new AssertionError("Negative occupied slot accepted");}catch(IllegalArgumentException expected){check(Inmis.tag(bag).getTagList("Inventory",10).getCompoundTagAt(0).getInteger("Slot")==-1,"Malformed contents altered");}}
    public static void augmentation_serialization_and_bounds(WorldServer world){
        BackpackAugmentsComponent input=BackpackAugmentsComponent.DEFAULT.withFarmhandEnabled(true).withFunnelling(new BackpackAugmentsComponent.FunnellingSettings(true,BackpackAugmentsComponent.FunnellingSettings.Mode.DISALLOW,java.util.Arrays.asList(new net.minecraft.util.ResourceLocation("minecraft:diamond"))));
        check(input.equals(BackpackAugmentsComponent.fromTag(input.toTag())),"Augment NBT changed");io.netty.buffer.ByteBuf bytes=Unpooled.buffer();input.write(new LegacyBuffer(bytes));check(input.equals(BackpackAugmentsComponent.read(new LegacyBuffer(bytes))),"Augment wire changed");bytes.release();
        io.netty.buffer.ByteBuf bad=Unpooled.buffer();bad.writeBoolean(true).writeByte(0).writeInt(Integer.MAX_VALUE);try{BackpackAugmentsComponent.read(new LegacyBuffer(bad));throw new AssertionError("Unbounded filter count accepted");}catch(IllegalArgumentException expected){}finally{bad.release();}
    }
    public static void upgrade_preserves_contents_and_dye(WorldServer world){
        ItemStack original=bag("frayed");inventory(original).setInventorySlotContents(0,new ItemStack(Items.diamond,7));((DyeableBackpackItem)original.getItem()).setColor(original,0x123456);
        InventoryCrafting crafting=new InventoryCrafting(new Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player){return true;}},3,3);
        for(int i=0;i<9;i++)crafting.setInventorySlotContents(i,i==4?original:new ItemStack(Items.iron_ingot));ItemStack result=CraftingManager.getInstance().findMatchingRecipe(crafting,world);
        check(result!=null&&((BackpackItem)result.getItem()).getTier().getName().equals("plated"),"Native upgrade failed");check(Inmis.getBackpackContents(result).get(0).stackSize==7,"Upgrade lost contents");check(result.getTagCompound().getCompoundTag("display").getInteger("color")==0x123456,"Upgrade lost dye");
        checkBranch(world,"bejeweled","blazing",new ItemStack(Items.diamond),new ItemStack(Items.magma_cream),null);
        checkBranch(world,"bejeweled","withered",new ItemStack(Blocks.soul_sand),new ItemStack(Blocks.soul_sand),new ItemStack(Items.nether_star));
        checkBranch(world,"withered","endless",new ItemStack(Blocks.end_stone),new ItemStack(Blocks.end_stone),new ItemStack(Items.ender_eye));
    }
    private static void checkBranch(WorldServer world,String before,String after,ItemStack corners,ItemStack edges,ItemStack top){
        ItemStack original=bag(before);inventory(original).setInventorySlotContents(0,new ItemStack(Items.diamond,7));original.setStackDisplayName("Branch contents");Inmis.tag(original).setTag("Augments",BackpackAugmentsComponent.DEFAULT.withFarmhandEnabled(true).toTag());
        InventoryCrafting crafting=new InventoryCrafting(new Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player){return true;}},3,3);
        for(int i=0;i<9;i++)crafting.setInventorySlotContents(i,i==4?original:(i==1&&top!=null?top:(i%2==0?corners:edges)).copy());
        ItemStack result=CraftingManager.getInstance().findMatchingRecipe(crafting,world);check(result!=null&&((BackpackItem)result.getItem()).getTier().getName().equals(after),"Native branch upgrade failed: "+after);check(original.getTagCompound().equals(result.getTagCompound()),"Native branch lost contents/name/settings: "+after);
    }
    public static void menu_number_swap_and_nested_lock(WorldServer world){NativeTestPlayer player=new NativeTestPlayer(world);ItemStack bag=bag("frayed");player.inventory.setInventorySlotContents(0,bag);BackpackScreenHandler menu=new BackpackScreenHandler(player.inventory,bag);player.openContainer=menu;menu.slotClick(0,0,2,player);check(player.inventory.getStackInSlot(0)==bag,"Number swap removed open bag");check(!menu.getSlot(9+27).canTakeStack(player),"Open bag pickup allowed");check(!menu.getSlot(0).isItemValid(bag("baby")),"Menu nesting allowed");}
    public static void update_request_ownership_and_window(WorldServer world){NativeTestPlayer player=new NativeTestPlayer(world);ItemStack bag=bag("endless");player.inventory.setInventorySlotContents(0,bag);BackpackScreenHandler menu=new BackpackScreenHandler(player.inventory,bag);menu.windowId=8;player.openContainer=menu;BackpackAugmentsComponent changed=BackpackAugmentsComponent.DEFAULT.withFarmhandEnabled(true);
        check(!ServerNetworking.applyUpdate(player,7,changed),"Wrong window accepted");check(ServerNetworking.applyUpdate(player,8,changed),"Own active menu rejected");player.inventory.setInventorySlotContents(0,null);check(!ServerNetworking.applyUpdate(player,8,BackpackAugmentsComponent.DEFAULT),"Detached bag update accepted");check(Inmis.getOrCreateAugments(bag,menu.getItem().getTier()).farmhandEnabled(),"Rejected request mutated settings");}
    public static void server_descriptor_uses_server_dimensions(WorldServer world){NativeTestPlayer player=new NativeTestPlayer(world);ItemStack bag=bag("withered");player.inventory.setInventorySlotContents(0,bag);ServerNetworking.OpeningData original=ServerNetworking.OpeningData.forStack(player.inventory,bag);BackpackScreenHandler client=new BackpackScreenHandler(player.inventory,new ServerNetworking.OpeningData(bag.copy(),9,6,54,0));check(client.inventorySlots.size()==90&&client.getDimension().getWidth()==178,"Client menu trusted local tier dimensions");}
    public static void ender_storage_is_player_owned(WorldServer world){NativeTestPlayer first=new NativeTestPlayer(world),second=new NativeTestPlayer(world);first.getInventoryEnderChest().setInventorySlotContents(0,new ItemStack(Items.diamond));check(second.getInventoryEnderChest().getStackInSlot(0)==null,"Ender inventories shared across players");}
    public static void vanilla_chest_native_equip_and_filled_removal(WorldServer world){boolean empty=Inmis.CONFIG.requireEmptyForUnequip,allow=Inmis.CONFIG.allowBackpacksInChestplate;
        try{NativeTestPlayer player=new NativeTestPlayer(world);ItemStack bag=bag("frayed");net.minecraft.inventory.Slot chest=player.inventoryContainer.getSlot(6);Inmis.CONFIG.allowBackpacksInChestplate=true;check(chest.isItemValid(bag),"Native chest rejects backpack");Inmis.CONFIG.allowBackpacksInChestplate=false;check(!chest.isItemValid(bag),"Disabled chest equip permits backpack");player.inventory.armorInventory[2]=bag;inventory(bag).setInventorySlotContents(0,new ItemStack(Items.diamond));Inmis.CONFIG.requireEmptyForUnequip=true;check(!chest.canTakeStack(player),"Native chest permits filled removal");Inmis.wipeBackpack(bag);check(chest.canTakeStack(player),"Native chest rejects empty removal");player.openContainer=new BackpackScreenHandler(player.inventory,bag);check(!chest.canTakeStack(player),"Native chest permits active open bag removal");check(draylar.inmis.core.BackpackDeathTransformer.armorHooks==1,"Native armor hook not loaded");}
        finally{Inmis.CONFIG.requireEmptyForUnequip=empty;Inmis.CONFIG.allowBackpacksInChestplate=allow;}}
    public static void native_air_use_stack_copy_and_deferred_ownership(WorldServer world) throws java.io.IOException {
        boolean equippedOnly=Inmis.CONFIG.requireArmorTrinketToOpen;
        NativeTestPlayer player=new NativeTestPlayer(world);io.netty.channel.embedded.EmbeddedChannel channel=player.enablePacketLoop();
        try{
            Inmis.CONFIG.requireArmorTrinketToOpen=false;ItemStack original=bag("frayed");inventory(original).setInventorySlotContents(0,new ItemStack(Items.diamond,7));player.inventory.setInventorySlotContents(0,original);player.inventory.currentItem=0;
            player.playerNetServerHandler.processPlayerBlockPlacement(airUsePacket(original));
            ItemStack current=player.inventory.getStackInSlot(0);check(current!=original&&ItemStack.areItemStacksEqual(current,original),"Native air-use did not make its vanilla held-stack copy");check(player.openContainer==player.inventoryContainer,"Backpack opened before native held-stack replacement");
            new draylar.inmis.augment.BackpackAugmentEvents().serverTick(new cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent(cpw.mods.fml.common.gameevent.TickEvent.Phase.END));
            check(player.openContainer instanceof BackpackScreenHandler,"Deferred native right click did not open backpack");BackpackScreenHandler menu=(BackpackScreenHandler)player.openContainer;check(menu.getBackpackStack()==current&&menu.canInteractWith(player),"Native right click opened an orphaned stack");
            check(!menu.getSlot(9+27).canTakeStack(player),"Deferred native opening lost active bag lock");menu.slotClick(0,0,1,player);check(Inmis.getBackpackContents(current).isEmpty(),"Native menu edit did not persist to copied owned stack");check(java.util.Arrays.stream(player.inventory.mainInventory).filter(stack->stack!=null&&stack.getItem()==Items.diamond).mapToInt(stack->stack.stackSize).sum()==7,"Native right-click menu lost or duplicated contents");player.closeContainer();
            ItemStack fresh=bag("frayed");check(!fresh.hasTagCompound(),"Fresh-backpack fixture already has NBT");player.inventory.setInventorySlotContents(0,fresh);player.playerNetServerHandler.processPlayerBlockPlacement(airUsePacket(fresh));ItemStack liveFresh=player.inventory.getStackInSlot(0);Inmis.getOrCreateAugments(liveFresh,((BackpackItem)liveFresh.getItem()).getTier());ServerNetworking.drain();check(player.openContainer instanceof BackpackScreenHandler&&((BackpackScreenHandler)player.openContainer).getBackpackStack()==liveFresh,"Augment initialization rejected a freshly crafted backpack");player.closeContainer();
            player.inventory.setInventorySlotContents(0,bag("frayed"));ItemStack held=player.inventory.getStackInSlot(0);held.getItem().onItemRightClick(held,world,player);player.inventory.currentItem=1;ServerNetworking.drain();check(player.openContainer==player.inventoryContainer,"Deferred opening accepted a switched held slot");
            player.inventory.currentItem=0;held=player.inventory.getStackInSlot(0);held.getItem().onItemRightClick(held,world,player);ItemStack replacement=bag("frayed");inventory(replacement).setInventorySlotContents(0,new ItemStack(Items.emerald));player.inventory.setInventorySlotContents(0,replacement);ServerNetworking.drain();check(player.openContainer==player.inventoryContainer,"Deferred opening accepted a different backpack");
            replacement.getItem().onItemRightClick(replacement,world,player);Inmis.CONFIG.requireArmorTrinketToOpen=true;ServerNetworking.drain();check(player.openContainer==player.inventoryContainer,"Deferred opening ignored current equipped-only configuration");
        }finally{ServerNetworking.drain();Inmis.CONFIG.requireArmorTrinketToOpen=equippedOnly;channel.finish();}
    }
    private static net.minecraft.network.play.client.C08PacketPlayerBlockPlacement airUsePacket(ItemStack stack) throws java.io.IOException {
        // Decode the genuine packet wire format; its convenience constructors are client-only.
        io.netty.buffer.ByteBuf bytes=Unpooled.buffer();
        try{net.minecraft.network.PacketBuffer buffer=new net.minecraft.network.PacketBuffer(bytes);buffer.writeInt(-1);buffer.writeByte(255);buffer.writeInt(-1);buffer.writeByte(255);buffer.writeItemStackToBuffer(stack);buffer.writeByte(0);buffer.writeByte(0);buffer.writeByte(0);
            net.minecraft.network.play.client.C08PacketPlayerBlockPlacement packet=new net.minecraft.network.play.client.C08PacketPlayerBlockPlacement();packet.readPacketData(buffer);return packet;
        }finally{bytes.release();}
    }
}
