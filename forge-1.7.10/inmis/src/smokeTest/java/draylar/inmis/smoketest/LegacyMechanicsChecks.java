package draylar.inmis.smoketest;

import draylar.inmis.item.BackpackItem;

import static draylar.inmis.smoketest.BackpackChecks.*;
import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackAugmentEvents;
import draylar.inmis.augment.BackpackAugmentHandler;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.item.BackpackEntityItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.world.BlockEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.List;

public final class LegacyMechanicsChecks {
    static NativeTestPlayer player(WorldServer world,ItemStack bag,BackpackAugmentsComponent settings){NativeTestPlayer player=new NativeTestPlayer(world);player.inventory.setInventorySlotContents(0,bag);Inmis.setAugments(bag,((draylar.inmis.item.BackpackItem)bag.getItem()).getTier(),settings);return player;}
    static BackpackAugmentsComponent funnelSettings(){return BackpackAugmentsComponent.DEFAULT.withFunnelling(BackpackAugmentsComponent.FunnellingSettings.DEFAULT.withEnabled(true));}
    static int loose(List<EntityItem> drops){return drops.stream().filter(d->d.getEntityItem().getItem()==Items.diamond).mapToInt(d->d.getEntityItem().stackSize).sum();}
    public static void funnelling_real_pickup(WorldServer world){ItemStack bag=bag("frayed");NativeTestPlayer player=player(world,bag,funnelSettings());EntityItem drop=new EntityItem(world,.5,64,.5,new ItemStack(Items.diamond,7));drop.onCollideWithPlayer(player);check(drop.isDead&&inventory(bag).getStackInSlot(0).stackSize==7,"Native pickup not funnelled exactly once");}
    public static void funnelling_owner_restriction(WorldServer world){ItemStack bag=bag("frayed");NativeTestPlayer player=player(world,bag,funnelSettings());EntityItem drop=new EntityItem(world,.5,64,.5,new ItemStack(Items.diamond,7));drop.func_145797_a("SomeoneElse");drop.onCollideWithPlayer(player);check(!drop.isDead&&inventory(bag).getStackInSlot(0)==null,"Reserved item was stolen");}
    public static void quiverlink_native_bow_consumption(WorldServer world){ItemStack bag=bag("gilded");NativeTestPlayer player=player(world,bag,BackpackAugmentsComponent.DEFAULT.withQuiverlink(BackpackAugmentsComponent.QuiverlinkSettings.DEFAULT.withEnabled(true)));inventory(bag).setInventorySlotContents(0,new ItemStack(Items.arrow,7));ItemStack bow=new ItemStack(Items.bow);
        Items.bow.onItemRightClick(bow,world,player);check(player.isUsingItem(),"Backpack arrow did not start native bow");Items.bow.onPlayerStoppedUsing(bow,world,player,71980);check(inventory(bag).getStackInSlot(0).stackSize==6&&bow.getItemDamage()==1,"Native bow failed arrow/durability accounting");
        for(Object raw:new ArrayList<>(world.loadedEntityList))if(raw instanceof EntityArrow)((EntityArrow)raw).setDead();
    }
    public static void lootbound_native_block_event(WorldServer world){ItemStack bag=bag("bejeweled");NativeTestPlayer player=player(world,bag,funnelSettings().withLootbound(BackpackAugmentsComponent.LootboundSettings.DEFAULT.withEnabled(true)));ArrayList<ItemStack> drops=new ArrayList<>();drops.add(new ItemStack(Items.diamond,7));
        MinecraftForge.EVENT_BUS.post(new BlockEvent.HarvestDropsEvent(0,64,0,world,Blocks.stone,0,0,1F,drops,player,false));check(drops.isEmpty()&&inventory(bag).getStackInSlot(0).stackSize==7,"Native harvest drops not conserved by Lootbound");
    }
    public static void lightweaver_native_placement_and_protection(WorldServer world){
        world.setBlock(4,63,4,Blocks.stone);world.setBlockToAir(4,64,4);ItemStack bag=bag("bejeweled");NativeTestPlayer player=player(world,bag,BackpackAugmentsComponent.DEFAULT.withLightweaver(new BackpackAugmentsComponent.LightweaverSettings(true,15,true)));player.setPosition(4.5,64,4.5);player.ticksExisted=5;inventory(bag).setInventorySlotContents(0,new ItemStack(Blocks.torch,2));
        final int[] sounds={0};net.minecraft.world.IWorldAccess access=(net.minecraft.world.IWorldAccess)java.lang.reflect.Proxy.newProxyInstance(LegacyMechanicsChecks.class.getClassLoader(),new Class<?>[]{net.minecraft.world.IWorldAccess.class},(proxy,method,args)->{
            if(method.getDeclaringClass()==Object.class){if(method.getName().equals("equals"))return proxy==args[0];if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);return "Native sound recorder";}
            Class<?>[] types=method.getParameterTypes();if(types.length==6&&types[0]==String.class&&types[1]==double.class)sounds[0]++;return null;
        });world.addWorldAccess(access);
        Object deny=new DenyPlacement(player);MinecraftForge.EVENT_BUS.register(deny);
        try{
            try{BackpackAugmentHandler.tick(player);check(world.isAirBlock(4,64,4)&&inventory(bag).getStackInSlot(0).stackSize==2&&sounds[0]==0,"Automatic torch bypassed protection or played cancelled sound");}finally{MinecraftForge.EVENT_BUS.unregister(deny);}
            Inmis.setAugments(bag,((BackpackItem)bag.getItem()).getTier(),BackpackAugmentsComponent.DEFAULT.withLightweaver(new BackpackAugmentsComponent.LightweaverSettings(true,15,false)));
            BackpackAugmentHandler.tick(player);check(world.getBlock(4,64,4)==Blocks.torch&&inventory(bag).getStackInSlot(0).stackSize==1&&sounds[0]==0,"Native silent torch placement failed");world.setBlockToAir(4,64,4);
            Inmis.setAugments(bag,((BackpackItem)bag.getItem()).getTier(),BackpackAugmentsComponent.DEFAULT.withLightweaver(new BackpackAugmentsComponent.LightweaverSettings(true,15,true)));
            BackpackAugmentHandler.tick(player);check(world.getBlock(4,64,4)==Blocks.torch&&inventory(bag).getStackInSlot(0)==null&&sounds[0]==1,"Native audible torch did not play exactly once");world.setBlockToAir(4,64,4);
            check(net.minecraftforge.common.ForgeHooks.onPlaceItemIntoWorld(new ItemStack(Blocks.torch),player,world,4,63,4,1,.5F,1F,.5F)&&sounds[0]==2,"Ordinary native torch placement sound changed");
        }finally{MinecraftForge.EVENT_BUS.unregister(deny);world.removeWorldAccess(access);world.setBlockToAir(4,64,4);}
    }
    public static void seedflow_native_seed_placement(WorldServer world){ItemStack bag=bag("plated");NativeTestPlayer player=player(world,bag,BackpackAugmentsComponent.DEFAULT.withSeedflow(BackpackAugmentsComponent.SeedflowSettings.DEFAULT.withEnabled(true)));player.setPosition(8.5,64,8.5);player.rotationYaw=0;player.ticksExisted=5;inventory(bag).setInventorySlotContents(0,new ItemStack(Items.wheat_seeds,4));
        for(int x=8;x<=9;x++)for(int z=9;z<=10;z++){world.setBlock(x,63,z,Blocks.farmland);world.setBlockToAir(x,64,z);}BackpackAugmentHandler.tick(player);
        int planted=0;for(int x=8;x<=9;x++)for(int z=9;z<=10;z++)if(world.getBlock(x,64,z)==Blocks.wheat)planted++;
        check(planted==4&&inventory(bag).getStackInSlot(0)==null,"Seedflow did not plant/consume exactly four native seeds");
    }
    public static void farmhand_native_harvest_then_replant(WorldServer world){ItemStack bag=bag("plated");NativeTestPlayer player=player(world,bag,BackpackAugmentsComponent.DEFAULT.withFarmhandEnabled(true));inventory(bag).setInventorySlotContents(0,new ItemStack(Items.wheat_seeds,2));world.setBlock(12,63,12,Blocks.farmland);world.setBlock(12,64,12,Blocks.wheat,7,3);
        BackpackAugmentEvents events=new BackpackAugmentEvents();events.harvest(new BlockEvent.HarvestDropsEvent(12,64,12,world,Blocks.wheat,7,0,1F,new ArrayList<>(),player,false));world.setBlockToAir(12,64,12);events.serverTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check(world.getBlock(12,64,12)==Blocks.wheat&&world.getBlockMetadata(12,64,12)==0&&inventory(bag).getStackInSlot(0).stackSize==1,"Farmhand failed native crop/seed transaction");
    }
    public static void hopper_bridge_native_inventory_transfer(WorldServer world){ItemStack bag=bag("endless");NativeTestPlayer player=player(world,bag,BackpackAugmentsComponent.DEFAULT.withHopperBridge(BackpackAugmentsComponent.HopperBridgeSettings.DEFAULT.withEnabled(true)));player.inventory.setInventorySlotContents(0,null);player.inventory.armorInventory[2]=bag;player.setPosition(16.5,64,16.5);player.ticksExisted=5;inventory(bag).setInventorySlotContents(0,new ItemStack(Items.diamond,2));world.setBlock(16,63,16,Blocks.hopper);TileEntityHopper hopper=(TileEntityHopper)world.getTileEntity(16,63,16);
        BackpackAugmentHandler.tick(player);check(hopper.getStackInSlot(0)!=null&&hopper.getStackInSlot(0).stackSize==1&&inventory(bag).getStackInSlot(0).stackSize==1,"Hopper extraction did not conserve one item");
        BackpackAugmentHandler.tick(player);check(hopper.getStackInSlot(0).stackSize==1,"Hopper native cooldown ignored");hopper.func_145896_c(0);
        BackpackAugmentsComponent.HopperBridgeSettings settings=BackpackAugmentsComponent.HopperBridgeSettings.DEFAULT.withFilterMode(BackpackAugmentsComponent.HopperBridgeSettings.FilterMode.EXTRACT).withFilters(java.util.Arrays.asList(new net.minecraft.util.ResourceLocation("minecraft:iron_ingot")));
        Inmis.setAugments(bag,((BackpackItem)bag.getItem()).getTier(),BackpackAugmentsComponent.DEFAULT.withHopperBridge(settings));BackpackAugmentHandler.tick(player);check(hopper.getStackInSlot(0).stackSize==1,"Hopper extraction filter ignored");
        Inmis.setAugments(bag,((BackpackItem)bag.getItem()).getTier(),BackpackAugmentsComponent.DEFAULT.withHopperBridge(BackpackAugmentsComponent.HopperBridgeSettings.DEFAULT.withExtract(false)));BackpackAugmentHandler.tick(player);check(hopper.getStackInSlot(0).stackSize==1,"Disabled hopper extraction ignored");
        Inmis.setAugments(bag,((BackpackItem)bag.getItem()).getTier(),BackpackAugmentsComponent.DEFAULT.withHopperBridge(BackpackAugmentsComponent.HopperBridgeSettings.DEFAULT));world.setBlockMetadataWithNotify(16,63,16,8,3);BackpackAugmentHandler.tick(player);check(hopper.getStackInSlot(0).stackSize==1,"Powered hopper transferred");world.setBlockToAir(16,63,16);
        world.setBlock(15,64,16,Blocks.hopper,5,3);TileEntityHopper source=(TileEntityHopper)world.getTileEntity(15,64,16);source.setInventorySlotContents(0,new ItemStack(Items.iron_ingot,2));
        settings=BackpackAugmentsComponent.HopperBridgeSettings.DEFAULT.withFilterMode(BackpackAugmentsComponent.HopperBridgeSettings.FilterMode.INSERT).withFilters(java.util.Arrays.asList(new net.minecraft.util.ResourceLocation("minecraft:diamond")));
        Inmis.setAugments(bag,((BackpackItem)bag.getItem()).getTier(),BackpackAugmentsComponent.DEFAULT.withHopperBridge(settings));BackpackAugmentHandler.tick(player);check(source.getStackInSlot(0).stackSize==2,"Hopper insertion filter ignored");
        Inmis.setAugments(bag,((BackpackItem)bag.getItem()).getTier(),BackpackAugmentsComponent.DEFAULT.withHopperBridge(settings.withFilters(java.util.Arrays.asList(new net.minecraft.util.ResourceLocation("minecraft:iron_ingot")))));BackpackAugmentHandler.tick(player);check(source.getStackInSlot(0).stackSize==1&&inventory(bag).getStackInSlot(1).getItem()==Items.iron_ingot&&inventory(bag).getStackInSlot(1).stackSize==1,"Native hopper insertion failed to conserve one item");world.setBlockToAir(15,64,16);
    }
    public static void imbued_hide_native_dropped_entity(WorldServer world){ItemStack protectedBag=bag("blazing");EntityItem original=new EntityItem(world,.5,64,.5,protectedBag);original.func_145797_a("Owner");BackpackEntityItem entity=(BackpackEntityItem)protectedBag.getItem().createEntity(world,original,protectedBag);check("Owner".equals(entity.func_145798_i()),"Custom item lost owner");check(!entity.attackEntityFrom(DamageSource.lava,100)&&!entity.isDead,"Intrinsic fire protection failed");
        ItemStack disabled=bag("endless");Inmis.setAugments(disabled,((BackpackItem)disabled.getItem()).getTier(),BackpackAugmentsComponent.DEFAULT.withImbuedHideEnabled(false));BackpackEntityItem vulnerable=new BackpackEntityItem(world,new EntityItem(world,.5,64,.5,disabled),disabled);vulnerable.attackEntityFrom(DamageSource.lava,100);check(vulnerable.isDead,"Disabled augment protected non-fire-immune tier");
        ItemStack enabled=bag("endless");Inmis.setAugments(enabled,((BackpackItem)enabled.getItem()).getTier(),BackpackAugmentsComponent.DEFAULT.withImbuedHideEnabled(true));BackpackEntityItem augmented=new BackpackEntityItem(world,new EntityItem(world,.5,64,.5,enabled),enabled);check(!augmented.attackEntityFrom(DamageSource.lava,100)&&!augmented.isDead,"Enabled Imbued Hide did not protect non-fire-immune tier");augmented.attackEntityFrom(DamageSource.generic,100);check(augmented.isDead,"Fire protection blocked unrelated damage");
        EntityItem unrelated=new EntityItem(world,.5,64,.5,new ItemStack(Items.diamond));unrelated.attackEntityFrom(DamageSource.lava,100);check(unrelated.isDead,"Unrelated native dropped item gained protection");
    }
    public static void open_menu_automatic_updates_share_inventory(WorldServer world){
        ItemStack bag=bag("gilded");NativeTestPlayer player=player(world,bag,BackpackAugmentsComponent.DEFAULT.withFunnelling(BackpackAugmentsComponent.FunnellingSettings.DEFAULT.withEnabled(true)));inventory(bag).setInventorySlotContents(0,new ItemStack(Items.arrow,2));
        draylar.inmis.ui.BackpackScreenHandler menu=new draylar.inmis.ui.BackpackScreenHandler(player.inventory,bag);player.openContainer=menu;
        EntityItem pickup=new EntityItem(world,player.posX,player.posY,player.posZ,new ItemStack(Items.diamond,7));pickup.delayBeforeCanPickup=0;pickup.onCollideWithPlayer(player);
        check(menu.getBackpackInventory().getStackInSlot(1)!=null&&menu.getBackpackInventory().getStackInSlot(1).stackSize==7,"Open menu did not observe automatic pickup");menu.getBackpackInventory().markDirty();check(inventory(bag).getStackInSlot(1).stackSize==7,"Menu save lost automatic pickup");
        ItemStack bow=new ItemStack(Items.bow);player.inventory.setInventorySlotContents(1,bow);player.inventory.currentItem=1;bow.getItem().onItemRightClick(bow,world,player);bow.getItem().onPlayerStoppedUsing(bow,world,player,72000-20);
        check(menu.getBackpackInventory().getStackInSlot(0).stackSize==1,"Open menu did not observe native bow consumption");menu.slotClick(1,0,0,player);menu.slotClick(2,0,0,player);
        check(inventory(bag).getStackInSlot(0).stackSize==1&&inventory(bag).getStackInSlot(1)==null&&inventory(bag).getStackInSlot(2).stackSize==7,"Native menu edit restored ammo or lost automatic loot");
    }
    public static void vanilla_death_exact_source_and_conservation(WorldServer world){boolean armor=Inmis.CONFIG.spillArmorBackpacksOnDeath,main=Inmis.CONFIG.spillMainBackpacksOnDeath;try{Inmis.CONFIG.spillArmorBackpacksOnDeath=true;Inmis.CONFIG.spillMainBackpacksOnDeath=false;NativeTestPlayer player=new NativeTestPlayer(world);ItemStack first=bag("frayed");inventory(first).setInventorySlotContents(0,new ItemStack(Items.diamond,7));ItemStack identical=first.copy();player.inventory.armorInventory[2]=first;player.inventory.setInventorySlotContents(0,identical);player.inventory.dropAllItems();check(loose(player.emitted)==7&&Inmis.getBackpackContents(first).isEmpty()&&Inmis.getBackpackContents(identical).get(0).stackSize==7,"Vanilla source selection changed unrelated identical main bag");check(draylar.inmis.core.BackpackDeathTransformer.vanillaHooks==2,"Native vanilla hook not loaded");}finally{Inmis.CONFIG.spillArmorBackpacksOnDeath=armor;Inmis.CONFIG.spillMainBackpacksOnDeath=main;}}
    public static void native_death_cancellation_and_keep_inventory(WorldServer world){boolean spill=Inmis.CONFIG.spillMainBackpacksOnDeath,keep=world.getGameRules().getGameRuleBooleanValue("keepInventory");NativeTestPlayer player=new NativeTestPlayer(world);ItemStack bag=bag("frayed");inventory(bag).setInventorySlotContents(0,new ItemStack(Items.diamond,7));player.inventory.setInventorySlotContents(0,bag);
        Object cancel=new CancelDeath(player);
        try{Inmis.CONFIG.spillMainBackpacksOnDeath=true;world.getGameRules().setOrCreateGameRule("keepInventory","true");player.onDeath(DamageSource.generic);check(player.inventory.getStackInSlot(0)==bag&&Inmis.getBackpackContents(bag).get(0).stackSize==7&&player.emitted.isEmpty(),"Native keepInventory bypassed");world.getGameRules().setOrCreateGameRule("keepInventory","false");MinecraftForge.EVENT_BUS.register(cancel);player.onDeath(DamageSource.generic);check(player.emitted.isEmpty()&&loose(player.capturedDrops)==7,"Cancelled death drops escaped native capture");}
        finally{MinecraftForge.EVENT_BUS.unregister(cancel);world.getGameRules().setOrCreateGameRule("keepInventory",Boolean.toString(keep));Inmis.CONFIG.spillMainBackpacksOnDeath=spill;}
    }
    public static final class DenyPlacement {
        private final net.minecraft.entity.player.EntityPlayer player;
        public DenyPlacement(net.minecraft.entity.player.EntityPlayer player){this.player=player;}
        @SubscribeEvent public void block(BlockEvent.PlaceEvent event){if(event.player==player)event.setCanceled(true);}
    }
    public static final class CancelDeath {
        private final net.minecraft.entity.player.EntityPlayer player;
        public CancelDeath(net.minecraft.entity.player.EntityPlayer player){this.player=player;}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void death(PlayerDropsEvent event){if(event.entityPlayer==player)event.setCanceled(true);}
    }
}
