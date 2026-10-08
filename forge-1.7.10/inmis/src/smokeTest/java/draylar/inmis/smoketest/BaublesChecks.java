package draylar.inmis.smoketest;

import static draylar.inmis.smoketest.BackpackChecks.*;
import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.world.WorldServer;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import baubles.api.IBauble;
import baubles.api.BaublesApi;
import baubles.common.lib.PlayerHandler;
import baubles.common.container.InventoryBaubles;
import java.util.ArrayList;

public final class BaublesChecks {
    static ItemStack packed(){ItemStack bag=bag("frayed");inventory(bag).setInventorySlotContents(0,new ItemStack(Items.diamond,7));return bag;}
    static int packedCount(ItemStack bag){return Inmis.getBackpackContents(bag).stream().filter(s->s.getItem()==Items.diamond).mapToInt(s->s.stackSize).sum();}
    static int loose(ArrayList<EntityItem> drops){return drops.stream().filter(d->d.getEntityItem().getItem()==Items.diamond).mapToInt(d->d.getEntityItem().stackSize).sum();}
    public static void baubles_native_amulet_equip_and_lock(WorldServer world){NativeTestPlayer player=new NativeTestPlayer(world);ItemStack bag=packed();IInventory worn=BaublesApi.getBaubles(player);check(worn.isItemValidForSlot(0,bag),"Original Baubles amulet slot rejects backpack");worn.setInventorySlotContents(0,bag);player.openContainer=new BackpackScreenHandler(player.inventory,bag);check(!((IBauble)bag.getItem()).canUnequip(bag,player),"Open Baubles bag can be removed");player.openContainer=player.inventoryContainer;check(((IBauble)bag.getItem()).canUnequip(bag,player),"Normally removable Baubles bag locked");}
    public static void baubles_require_empty_and_disabled_equip(WorldServer world){boolean empty=Inmis.CONFIG.requireEmptyForUnequip,compat=Inmis.CONFIG.enableTrinketCompatibility;try{NativeTestPlayer player=new NativeTestPlayer(world);ItemStack bag=packed();Inmis.CONFIG.requireEmptyForUnequip=true;check(!((IBauble)bag.getItem()).canUnequip(bag,player),"Filled required-empty bag removable");Inmis.wipeBackpack(bag);check(((IBauble)bag.getItem()).canUnequip(bag,player),"Empty bag not removable");Inmis.CONFIG.enableTrinketCompatibility=false;check(!((IBauble)bag.getItem()).canEquip(bag,player),"Disabled Baubles compatibility permits equip");
        ItemStack disabledFilled=packed();BaublesApi.getBaubles(player).setInventorySlotContents(0,disabledFilled);check(((IBauble)disabledFilled.getItem()).canUnequip(disabledFilled,player),"Disabled compatibility traps an already equipped filled backpack");player.openContainer=new BackpackScreenHandler(player.inventory,disabledFilled);check(!((IBauble)disabledFilled.getItem()).canUnequip(disabledFilled,player),"Disabled compatibility bypasses the active backpack lock");player.openContainer=player.inventoryContainer;check(((IBauble)disabledFilled.getItem()).canUnequip(disabledFilled,player),"Closed filled backpack remains locked after disabling compatibility");
        ItemStack bridge=bag("endless");inventory(bridge).setInventorySlotContents(0,new ItemStack(Items.diamond,2));BaublesApi.getBaubles(player).setInventorySlotContents(0,bridge);player.setPosition(20.5,64,20.5);player.ticksExisted=5;world.setBlock(20,63,20,net.minecraft.init.Blocks.hopper);net.minecraft.tileentity.TileEntityHopper hopper=(net.minecraft.tileentity.TileEntityHopper)world.getTileEntity(20,63,20);
        draylar.inmis.augment.BackpackAugmentHandler.tick(player);check(hopper.getStackInSlot(0)==null&&inventory(bridge).getStackInSlot(0).stackSize==2,"Disabled equipment compatibility still transfers Baubles hopper contents");
        io.netty.channel.embedded.EmbeddedChannel network=player.enablePacketLoop();try{ItemStack carried=bag("frayed");player.inventory.setInventorySlotContents(0,carried);draylar.inmis.network.ServerNetworking.requestOpenBackpack(player);draylar.inmis.network.ServerNetworking.drain();check(player.openContainer instanceof BackpackScreenHandler&&((BackpackScreenHandler)player.openContainer).getBackpackStack()==carried,"Disabled Baubles compatibility shadowed an owned inventory backpack");player.closeContainer();}finally{network.finish();}
        Inmis.CONFIG.enableTrinketCompatibility=true;draylar.inmis.augment.BackpackAugmentHandler.tick(player);check(hopper.getStackInSlot(0)!=null&&hopper.getStackInSlot(0).stackSize==1&&inventory(bridge).getStackInSlot(0).stackSize==1,"Enabled native Baubles hopper transfer failed");
    }finally{world.setBlockToAir(20,63,20);Inmis.CONFIG.requireEmptyForUnequip=empty;Inmis.CONFIG.enableTrinketCompatibility=compat;}}
    public static void baubles_exact_native_drop_ignores_identical_unrelated(WorldServer world){boolean old=Inmis.CONFIG.spillArmorBackpacksOnDeath;try{Inmis.CONFIG.spillArmorBackpacksOnDeath=true;NativeTestPlayer player=new NativeTestPlayer(world);ItemStack bag=packed();InventoryBaubles worn=PlayerHandler.getPlayerBaubles(player);worn.setInventorySlotContents(0,bag);ArrayList<EntityItem> drops=new ArrayList<>();EntityItem unrelated=new EntityItem(world,.5,64,.5,bag.copy());drops.add(unrelated);player.inventory.setInventorySlotContents(1,bag.copy());worn.dropItemsAt(drops,player);
        check(worn.getStackInSlot(0)==null&&loose(drops)==7&&drops.size()==3,"Baubles actual selected drop lost/duplicated items");check(packedCount(unrelated.getEntityItem())==7&&packedCount(player.inventory.getStackInSlot(1))==7,"Identical unrelated bags changed");check(draylar.inmis.core.BackpackDeathTransformer.baublesHooks==2,"Native Baubles hook not transformed");
    }finally{Inmis.CONFIG.spillArmorBackpacksOnDeath=old;}}
    public static void baubles_disabled_spill_native_drop_keeps_contents(WorldServer world){boolean spill=Inmis.CONFIG.spillArmorBackpacksOnDeath,compat=Inmis.CONFIG.enableTrinketCompatibility;try{for(int scenario=0;scenario<2;scenario++){NativeTestPlayer player=new NativeTestPlayer(world);InventoryBaubles worn=PlayerHandler.getPlayerBaubles(player);worn.setInventorySlotContents(0,packed());ArrayList<EntityItem> drops=new ArrayList<>();Inmis.CONFIG.spillArmorBackpacksOnDeath=scenario==1;Inmis.CONFIG.enableTrinketCompatibility=scenario==0;worn.dropItemsAt(drops,player);check(drops.size()==1&&packedCount(drops.get(0).getEntityItem())==7,"Disabled spill changed native packed drop");}}finally{Inmis.CONFIG.spillArmorBackpacksOnDeath=spill;Inmis.CONFIG.enableTrinketCompatibility=compat;}}
    public static void baubles_native_keep_inventory_and_cancel(WorldServer world){boolean spill=Inmis.CONFIG.spillArmorBackpacksOnDeath,keep=world.getGameRules().getGameRuleBooleanValue("keepInventory");NativeTestPlayer player=new NativeTestPlayer(world);InventoryBaubles worn=PlayerHandler.getPlayerBaubles(player);ItemStack bag=packed();worn.setInventorySlotContents(0,bag);Object cancel=new LegacyMechanicsChecks.CancelDeath(player);
        try{Inmis.CONFIG.spillArmorBackpacksOnDeath=true;world.getGameRules().setOrCreateGameRule("keepInventory","true");player.onDeath(DamageSource.generic);check(worn.getStackInSlot(0)==bag&&packedCount(bag)==7&&player.emitted.isEmpty(),"Keep inventory bypassed Baubles source");world.getGameRules().setOrCreateGameRule("keepInventory","false");MinecraftForge.EVENT_BUS.register(cancel);player.onDeath(DamageSource.generic);check(worn.getStackInSlot(0)==null&&loose(player.capturedDrops)==7&&player.emitted.isEmpty(),"Cancelled native Baubles drops lost capture/escaped world");}
        finally{MinecraftForge.EVENT_BUS.unregister(cancel);world.getGameRules().setOrCreateGameRule("keepInventory",Boolean.toString(keep));Inmis.CONFIG.spillArmorBackpacksOnDeath=spill;}
    }
}
