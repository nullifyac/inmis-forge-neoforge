package draylar.inmis.smoketest;

import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.config.InmisConfig;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.world.BlockEvent;
import java.util.Collections;
import java.util.List;

/** Executes native bow, hopper, crop, placement, damage, XP and death paths. */
public final class AugmentGameplayChecks {
    private AugmentGameplayChecks() {}

    public static void quiverlinkBowConsumesStoredArrowsAndRespectsPriority(SmokeContext context) {
        NativeTestPlayer player=player(context); ItemStack bag=bag("gilded");
        try {
            player.setItemStackToSlot(EntityEquipmentSlot.CHEST,bag);
            ItemStack bow=new ItemStack(Items.BOW); player.inventory.setInventorySlotContents(0,bow);
            inventory(bag).setInventorySlotContents(0,new ItemStack(Items.ARROW,3));
            int arrows=context.getLevel().getEntitiesWithinAABB(EntityArrow.class,box()).size();
            Items.BOW.onPlayerStoppedUsing(bow,context.getLevel(),player,72000-20);
            check(packed(bag,Items.ARROW)==2&&context.getLevel().getEntitiesWithinAABB(EntityArrow.class,box()).size()==arrows+1,"Native bow failed stored-arrow consumption/spawn");
            Inmis.setBackpackAugments(bag,augments(bag).withQuiverlink(new BackpackAugmentsComponent.QuiverlinkSettings(true,BackpackAugmentsComponent.QuiverlinkSettings.Priority.INVENTORY)));
            player.inventory.setInventorySlotContents(1,new ItemStack(Items.ARROW,2));
            Items.BOW.onPlayerStoppedUsing(bow,context.getLevel(),player,72000-20);
            check(packed(bag,Items.ARROW)==2&&player.inventory.getStackInSlot(1).getCount()==1,"Quiverlink ignored native carried-ammo priority");
            Inmis.setBackpackAugments(bag,augments(bag).withQuiverlink(new BackpackAugmentsComponent.QuiverlinkSettings(false,BackpackAugmentsComponent.QuiverlinkSettings.Priority.BACKPACK)));
            player.inventory.setInventorySlotContents(1,ItemStack.EMPTY);
            int before=context.getLevel().getEntitiesWithinAABB(EntityArrow.class,box()).size();
            Items.BOW.onPlayerStoppedUsing(bow,context.getLevel(),player,72000-20);
            check(packed(bag,Items.ARROW)==2&&context.getLevel().getEntitiesWithinAABB(EntityArrow.class,box()).size()==before,"Disabled Quiverlink supplied an arrow");
        } finally { context.getLevel().getEntitiesWithinAABB(EntityArrow.class,box()).forEach(EntityArrow::setDead); player.setDead(); }
        context.succeed();
    }

    public static void hopperBridgeUsesRealHopperAdmissionFiltersAndExtraction(SmokeContext context) {
        NativeTestPlayer player=player(context); ItemStack bag=bag("endless"); BlockPos hopperPos=new BlockPos(12,63,12);
        IBlockState previous=context.getLevel().getBlockState(hopperPos);
        try {
            player.setPosition(12.5,64,12.5); player.setItemStackToSlot(EntityEquipmentSlot.CHEST,bag);
            context.getLevel().spawnEntity(player);
            context.getLevel().setBlockState(hopperPos,Blocks.HOPPER.getDefaultState());
            TileEntityHopper hopper=(TileEntityHopper)context.getLevel().getTileEntity(hopperPos);
            IInventory target=TileEntityHopper.getInventoryAtPosition(context.getLevel(),12.5,64.5,12.5);
            check(target instanceof BackpackInventory,"Native hopper did not find nearby equipped backpack");
            ItemStack remainder=TileEntityHopper.putStackInInventoryAllSlots(hopper,target,new ItemStack(Items.DIAMOND,3),EnumFacing.DOWN);
            check(remainder.isEmpty()&&packed(bag,Items.DIAMOND)==3,"Native hopper insertion lost/rejected allowed items");
            Inmis.setBackpackAugments(bag,augments(bag).withHopperBridge(new BackpackAugmentsComponent.HopperBridgeSettings(true,true,true,BackpackAugmentsComponent.HopperBridgeSettings.FilterMode.INSERT,Collections.singletonList(new ResourceLocation("minecraft:diamond")))));
            target=TileEntityHopper.getInventoryAtPosition(context.getLevel(),12.5,64.5,12.5);
            remainder=TileEntityHopper.putStackInInventoryAllSlots(hopper,target,new ItemStack(Items.IRON_INGOT,2),EnumFacing.DOWN);
            check(remainder.getCount()==2&&packed(bag,Items.IRON_INGOT)==0,"Native hopper ignored insert filter");
            check(TileEntityHopper.pullItems(hopper),"Native hopper did not pull from equipped backpack");
            check(packed(bag,Items.DIAMOND)==2&&hopper.getStackInSlot(0).getItem()==Items.DIAMOND&&hopper.getStackInSlot(0).getCount()==1,"Native extraction did not persist exactly one moved item");
            Inmis.setBackpackAugments(bag,augments(bag).withHopperBridge(augments(bag).hopperBridge().withExtract(false)));
            check(!TileEntityHopper.pullItems(hopper)&&packed(bag,Items.DIAMOND)==2,"Disabled extraction still removed stored items");
        } finally { context.getLevel().setBlockState(hopperPos,previous); context.getLevel().removeEntity(player); player.setDead(); }
        context.succeed();
    }

    public static void farmhandSeedflowAndPlacementVetoConsumeExactlyPlantedSeeds(SmokeContext context) {
        NativeTestPlayer player=player(context); ItemStack bag=bag("endless"); BlockPos p=new BlockPos(8,65,8);
        IBlockState old=context.getLevel().getBlockState(p), below=context.getLevel().getBlockState(p.down());
        try {
            player.setItemStackToSlot(EntityEquipmentSlot.CHEST,bag); player.setPosition(8.5,65,7.5); player.rotationYaw=0; player.ticksExisted=5;
            Inmis.setBackpackAugments(bag,augments(bag).withFarmhandEnabled(true).withSeedflow(new BackpackAugmentsComponent.SeedflowSettings(false,false,false,Collections.emptyList())));
            inventory(bag).setInventorySlotContents(0,new ItemStack(Items.WHEAT_SEEDS,5));
            context.getLevel().setBlockState(p.down(),Blocks.FARMLAND.getDefaultState());
            IBlockState mature=((net.minecraft.block.BlockCrops)Blocks.WHEAT).withAge(7); context.getLevel().setBlockState(p,mature);
            // Execute native harvesting, then its actual deferred end-of-server-tick hook.
            check(player.interactionManager.tryHarvestBlock(p),"Native mature-crop harvesting failed");
            MinecraftForge.EVENT_BUS.post(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
            check(context.getLevel().getBlockState(p).getBlock()==Blocks.WHEAT&&packed(bag,Items.WHEAT_SEEDS)==4,"Farmhand failed real crop replacement/one-seed persistence");
            context.getLevel().setBlockToAir(p);
            Inmis.setBackpackAugments(bag,augments(bag).withSeedflow(new BackpackAugmentsComponent.SeedflowSettings(true,false,true,Collections.singletonList(new ResourceLocation("minecraft:wheat_seeds")))));
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));
            check(context.getLevel().getBlockState(p).getBlock()==Blocks.WHEAT&&packed(bag,Items.WHEAT_SEEDS)==3,"Seedflow failed native player-tick crop placement");
            context.getLevel().setBlockToAir(p);
            Object veto=new Object(){@SubscribeEvent(priority=EventPriority.HIGHEST) public void cancel(BlockEvent.PlaceEvent event){if(event.getPlayer()==player)event.setCanceled(true);}};
            MinecraftForge.EVENT_BUS.register(veto);
            try { MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player)); }
            finally { MinecraftForge.EVENT_BUS.unregister(veto); }
            check(context.getLevel().isAirBlock(p)&&packed(bag,Items.WHEAT_SEEDS)==3,"Canceled native placement consumed seeds or left crop");
        } finally { context.getLevel().setBlockState(p,old);context.getLevel().setBlockState(p.down(),below);player.setDead(); }
        context.succeed();
    }

    public static void lightweaverPlacesStoredTorchThroughNativeItemUse(SmokeContext context) {
        NativeTestPlayer player=player(context); ItemStack bag=bag("endless"); BlockPos p=new BlockPos(6,65,6);
        IBlockState old=context.getLevel().getBlockState(p),below=context.getLevel().getBlockState(p.down());
        try {
            player.setPosition(6.5,65,6.5);player.ticksExisted=5;player.setItemStackToSlot(EntityEquipmentSlot.CHEST,bag);
            ItemStack held=new ItemStack(Items.DIAMOND,2);player.inventory.setInventorySlotContents(0,held);
            Inmis.setBackpackAugments(bag,augments(bag).withLightweaver(new BackpackAugmentsComponent.LightweaverSettings(true,15,false)));
            inventory(bag).setInventorySlotContents(0,new ItemStack(Item.getItemFromBlock(Blocks.TORCH),3));
            context.getLevel().setBlockState(p.down(),Blocks.STONE.getDefaultState());context.getLevel().setBlockToAir(p);
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));
            check(context.getLevel().getBlockState(p).getBlock()==Blocks.TORCH&&packed(bag,Item.getItemFromBlock(Blocks.TORCH))==2,"Native Lightweaver did not place/consume one torch");
            check(player.getHeldItemMainhand()==held&&held.getCount()==2,"Torch placement replaced/consumed player's held item");
        } finally {context.getLevel().setBlockState(p,old);context.getLevel().setBlockState(p.down(),below);player.setDead();}
        context.succeed();
    }

    public static void lootboundNativeMobDeathAndBlockHarvestConserveDrops(SmokeContext context) {
        NativeTestPlayer player=player(context);ItemStack bag=bag("endless");EntityZombie zombie=new EntityZombie(context.getLevel());
        try {
            player.setItemStackToSlot(EntityEquipmentSlot.CHEST,bag);
            Inmis.setBackpackAugments(bag,augments(bag).withFunnelling(new BackpackAugmentsComponent.FunnellingSettings(true,BackpackAugmentsComponent.FunnellingSettings.Mode.ALLOW,Collections.emptyList())).withLootbound(new BackpackAugmentsComponent.LootboundSettings(true,true,true)));
            zombie.setPosition(4,65,4);zombie.setItemStackToSlot(EntityEquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND,5));zombie.setDropChance(EntityEquipmentSlot.MAINHAND,2.0f);
            context.getLevel().spawnEntity(zombie);zombie.attackEntityFrom(DamageSource.causePlayerDamage(player),1000);
            check(packed(bag,Items.DIAMOND)==5,"Native LivingDropsEvent did not funnel guaranteed mob equipment drop");
            java.util.ArrayList<ItemStack> drops=new java.util.ArrayList<>();drops.add(new ItemStack(Items.IRON_INGOT,3));
            BlockEvent.HarvestDropsEvent event=new BlockEvent.HarvestDropsEvent(context.getLevel(),new BlockPos(4,65,4),Blocks.IRON_ORE.getDefaultState(),0,1,drops,player,false);
            MinecraftForge.EVENT_BUS.post(event);
            check(drops.isEmpty()&&packed(bag,Items.IRON_INGOT)==3,"Native block harvest did not funnel/conserve drops");
        } finally {zombie.setDead();player.setDead();}
        context.succeed();
    }

    public static void imbuedHideAndReforgeUseNativeDamageAndXp(SmokeContext context) {
        NativeTestPlayer player=player(context);ItemStack bag=bag("endless");EntityItem protectedDrop=new EntityItem(context.getLevel(),2,65,2,bag);EntityItem ordinary=new EntityItem(context.getLevel(),2,65,2,new ItemStack(Items.DIAMOND));EntityXPOrb orb=new EntityXPOrb(context.getLevel(),2,65,2,3);
        try {
            player.setItemStackToSlot(EntityEquipmentSlot.CHEST,bag);
            Inmis.setBackpackAugments(bag,augments(bag).withImbuedHideEnabled(true).withReforgeEnabled(true));
            check(!protectedDrop.attackEntityFrom(DamageSource.LAVA,100)&&!protectedDrop.isDead,"Imbued Hide did not protect actual item entity fire damage");
            ordinary.attackEntityFrom(DamageSource.LAVA,100);check(ordinary.isDead,"Vanilla unprotected control survived lethal fire damage");
            ItemStack tool=new ItemStack(Items.DIAMOND_PICKAXE);tool.setItemDamage(20);tool.addEnchantment(Enchantments.MENDING,1);inventory(bag).setInventorySlotContents(0,tool);
            orb.onCollideWithPlayer(player);
            ItemStack repaired=Inmis.getBackpackContents(bag).stream().filter(s->s.getItem()==Items.DIAMOND_PICKAXE).findFirst().orElseThrow(()->new AssertionError("Mending item lost"));
            check(orb.isDead&&repaired.getItemDamage()==14,"Native XP pickup did not repair/persist stored Mending item");
        } finally {protectedDrop.setDead();ordinary.setDead();orb.setDead();player.setDead();}
        context.succeed();
    }

    private static NativeTestPlayer player(SmokeContext context) {NativeTestPlayer p=new NativeTestPlayer(context.getLevel(),"InmisAugments");p.setPosition(2.5,65,2.5);p.inventory.clear();p.capabilities.isCreativeMode=false;return p;}
    private static ItemStack bag(String tier){return new ItemStack(Inmis.BACKPACKS.stream().map(e->e.get()).filter(b->b.getTier().getName().equals(tier)).findFirst().orElseThrow(()->new AssertionError("Missing tier "+tier)));}
    private static BackpackAugmentsComponent augments(ItemStack bag){return Inmis.getOrCreateAugments(bag,((BackpackItem)bag.getItem()).getTier());}
    private static BackpackInventory inventory(ItemStack bag){return new BackpackInventory(bag,((BackpackItem)bag.getItem()).getTier());}
    private static int packed(ItemStack bag,Item item){return Inmis.getBackpackContents(bag).stream().filter(s->s.getItem()==item).mapToInt(ItemStack::getCount).sum();}
    private static AxisAlignedBB box(){return new AxisAlignedBB(-32,0,-32,32,256,32);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
