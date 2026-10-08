package draylar.inmis.smoketest;
import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.augment.BackpackInventory;
import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.*;
import java.util.*;
public final class EquipmentDeathChecks {
    public static void baublesExactNativeDropDoesNotSpillIdenticalUnrelatedBags(SmokeContext helper) {
        boolean oldSpill=Inmis.CONFIG.spillArmorBackpacksOnDeath; boolean oldCompat=Inmis.CONFIG.enableTrinketCompatibility;
        NativeTestPlayer player=player(helper); IBaublesItemHandler handler=BaublesApi.getBaublesHandler(player);
        List<EntityItem> drops=new ArrayList<>(); ItemStack bag=bag(); handler.setStackInSlot(5,bag);
        EntityItem unrelated=drop(helper,bag.copy()); drops.add(unrelated); EntityItem otherMod=drop(helper,bag.copy());drops.add(otherMod);
        player.inventory.setInventorySlotContents(1,bag.copy());
        try {
            Inmis.CONFIG.spillArmorBackpacksOnDeath=true;Inmis.CONFIG.enableTrinketCompatibility=true;
            new baubles.common.event.EventHandlerEntity().dropItemsAt(player,drops,player);
            check(handler.getStackInSlot(5).isEmpty(),"Native Baubles source slot was not cleared");
            check(packed(unrelated.getItem())==7 && packed(otherMod.getItem())==7,"Baubles spilling changed identical unrelated drops");
            check(packed(player.inventory.getStackInSlot(1))==7,"Baubles spilling changed identical main inventory backpack");
            check(loose(drops)==7 && bags(drops)==3,"Native Baubles spill lost or duplicated items");
            check(drops.stream().filter(d -> d!=unrelated && d!=otherMod && d.getItem().getItem() instanceof BackpackItem).allMatch(d -> packed(d.getItem())==0),"Exact native selected backpack was not emptied");
        } finally {Inmis.CONFIG.spillArmorBackpacksOnDeath=oldSpill;Inmis.CONFIG.enableTrinketCompatibility=oldCompat;player.setDead();}
        helper.succeed();
    }
    public static void baublesKeepInventoryRetainsFullEquippedBag(SmokeContext helper) {
        boolean old=helper.getLevel().getGameRules().getBoolean("keepInventory"); NativeTestPlayer player=player(helper);
        IBaublesItemHandler handler=BaublesApi.getBaublesHandler(player);ItemStack bag=bag();handler.setStackInSlot(5,bag);List<EntityItem> drops=new ArrayList<>();
        try {
            helper.getLevel().getGameRules().setOrCreateGameRule("keepInventory","true");
            new baubles.common.event.EventHandlerEntity().playerDeath(new PlayerDropsEvent(player,DamageSource.GENERIC,drops,false));
            check(handler.getStackInSlot(5)==bag && packed(bag)==7 && drops.isEmpty(),"Native keepInventory was bypassed");
        } finally {helper.getLevel().getGameRules().setOrCreateGameRule("keepInventory",Boolean.toString(old));player.setDead();}
        helper.succeed();
    }
    public static void baublesDisabledSpillAndCompatibilityKeepPackedDrops(SmokeContext helper) {
        boolean spill=Inmis.CONFIG.spillArmorBackpacksOnDeath;boolean compat=Inmis.CONFIG.enableTrinketCompatibility;
        try {
            for(int scenario=0;scenario<2;scenario++) {
                NativeTestPlayer player=player(helper);IBaublesItemHandler handler=BaublesApi.getBaublesHandler(player);handler.setStackInSlot(5,bag());List<EntityItem> drops=new ArrayList<>();
                Inmis.CONFIG.spillArmorBackpacksOnDeath=scenario==1;Inmis.CONFIG.enableTrinketCompatibility=scenario==0;
                new baubles.common.event.EventHandlerEntity().dropItemsAt(player,drops,player);
                check(handler.getStackInSlot(5).isEmpty() && bags(drops)==1 && loose(drops)==0 && packed(drops.get(0).getItem())==7,"Disabled Baubles spill changed selected native drop");player.setDead();
            }
        } finally {Inmis.CONFIG.spillArmorBackpacksOnDeath=spill;Inmis.CONFIG.enableTrinketCompatibility=compat;}
        helper.succeed();
    }
    public static void baublesVanishingFollowsLegacyNativeDropSemantics(SmokeContext helper) {
        boolean spill=Inmis.CONFIG.spillArmorBackpacksOnDeath;NativeTestPlayer player=player(helper);ItemStack bag=bag();bag.addEnchantment(net.minecraft.init.Enchantments.VANISHING_CURSE,1);
        BaublesApi.getBaublesHandler(player).setStackInSlot(5,bag);List<EntityItem> drops=new ArrayList<>();
        try { Inmis.CONFIG.spillArmorBackpacksOnDeath=false;new baubles.common.event.EventHandlerEntity().dropItemsAt(player,drops,player);
            check(bags(drops)==1 && packed(drops.get(0).getItem())==7 && net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(net.minecraft.init.Enchantments.VANISHING_CURSE,drops.get(0).getItem())==1,"Inmis changed Baubles native cursed-drop policy");
        } finally {Inmis.CONFIG.spillArmorBackpacksOnDeath=spill;player.setDead();}helper.succeed();
    }
    public static void baublesNativePlayerDropsCancellationCapturesSpilledContents(SmokeContext helper) {
        boolean spill=Inmis.CONFIG.spillArmorBackpacksOnDeath;boolean compat=Inmis.CONFIG.enableTrinketCompatibility;boolean keep=helper.getLevel().getGameRules().getBoolean("keepInventory");
        NativeTestPlayer player=player(helper);BaublesApi.getBaublesHandler(player).setStackInSlot(5,bag());List<EntityItem> captured=new ArrayList<>();
        Object listener=new Object() {
            @SubscribeEvent(priority=EventPriority.LOWEST) public void cancel(PlayerDropsEvent event) {if(event.getEntityPlayer()==player){captured.addAll(event.getDrops());event.setCanceled(true);}}
        };
        MinecraftForge.EVENT_BUS.register(listener);
        try {
            helper.getLevel().getGameRules().setOrCreateGameRule("keepInventory","false");Inmis.CONFIG.spillArmorBackpacksOnDeath=true;Inmis.CONFIG.enableTrinketCompatibility=true;
            player.setHealth(1);player.hurtResistantTime=0;player.attackEntityFrom(DamageSource.GENERIC,100);
            check(player.getHealth()<=0,"Native Baubles death fixture did not reach death");
            check(bags(captured)==1 && loose(captured)==7 && captured.stream().filter(d -> d.getItem().getItem() instanceof BackpackItem).allMatch(d -> packed(d.getItem())==0),"Cancelled native PlayerDrops event missed empty bag and selected contents");
            check(captured.stream().noneMatch(helper.getLevel().loadedEntityList::contains),
                    "Cancelled native Baubles death drops leaked into world");
        } finally {MinecraftForge.EVENT_BUS.unregister(listener);helper.getLevel().getGameRules().setOrCreateGameRule("keepInventory",Boolean.toString(keep));Inmis.CONFIG.spillArmorBackpacksOnDeath=spill;Inmis.CONFIG.enableTrinketCompatibility=compat;player.setDead();}
        helper.succeed();
    }
    private static NativeTestPlayer player(SmokeContext helper) {NativeTestPlayer p=new NativeTestPlayer(helper.getLevel(),"InmisBaubles");p.setPosition(1.5,65,1.5);return p;}
    private static ItemStack bag() {BackpackItem item=Inmis.BACKPACKS.stream().map(e->e.get()).filter(i->i.getTier().getName().equals("frayed")).findFirst().orElseThrow(AssertionError::new);ItemStack bag=new ItemStack(item);new BackpackInventory(bag,item.getTier()).setInventorySlotContents(0,new ItemStack(Items.DIAMOND,7));return bag;}
    private static EntityItem drop(SmokeContext helper,ItemStack stack){return new EntityItem(helper.getLevel(),1.5,65,1.5,stack);}
    private static int packed(ItemStack bag){return Inmis.getBackpackContents(bag).stream().filter(s->s.getItem()==Items.DIAMOND).mapToInt(ItemStack::getCount).sum();}
    private static int loose(List<EntityItem> drops){return drops.stream().filter(d->d.getItem().getItem()==Items.DIAMOND).mapToInt(d->d.getItem().getCount()).sum();}
    private static int bags(List<EntityItem> drops){return drops.stream().filter(d->d.getItem().getItem() instanceof BackpackItem).mapToInt(d->d.getItem().getCount()).sum();}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
