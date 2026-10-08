package draylar.inmis.smoketest;
import draylar.inmis.Inmis;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.ui.BackpackScreenHandler;
import draylar.inmis.network.ServerNetworking;
import draylar.inmis.util.InventoryUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import java.lang.reflect.Constructor;

public final class SecurityChecks {
    private SecurityChecks() {}
    public static void nativeFmlSidesAndOptionalAvailabilityMatchLoader(SmokeContext context) {
        check(Side.values().length==2&&Side.values()[0]==Side.CLIENT&&Side.values()[1]==Side.SERVER,"Development FML loaded an incompatible MergeTool Side API");
        check(Inmis.BACKPACKS.size()==8&&Inmis.ENDER_POUCH.isPresent(),"Native registration omitted a tier or pouch");
        check(Inmis.BAUBLES_LOADED==net.minecraftforge.fml.common.Loader.isModLoaded("baubles"),"Optional Baubles detection disagrees with real loader");
        context.succeed();
    }
    public static void malformedFilterCountsRejectBeforeReadingLists(SmokeContext context) {
        for(int kind=0;kind<3;kind++) for(int count:new int[]{-1,Integer.MAX_VALUE}) {
            PacketBuffer buffer=new PacketBuffer(Unpooled.buffer());
            buffer.writeBoolean(true);
            if(kind==0)buffer.writeEnumValue(BackpackAugmentsComponent.FunnellingSettings.Mode.ALLOW);
            else {buffer.writeBoolean(true);buffer.writeBoolean(true);if(kind==2)buffer.writeEnumValue(BackpackAugmentsComponent.HopperBridgeSettings.FilterMode.OFF);}
            buffer.writeVarInt(count);
            boolean rejected=false;
            try {if(kind==0)BackpackAugmentsComponent.FunnellingSettings.read(buffer);else if(kind==1)BackpackAugmentsComponent.SeedflowSettings.read(buffer);else BackpackAugmentsComponent.HopperBridgeSettings.read(buffer);}
            catch(IllegalArgumentException expected){rejected=true;}
            finally {buffer.release();}
            check(rejected,"Native packet reader accepted malformed count for augment "+kind+": "+count);
        }
        context.succeed();
    }
    public static void duplicateOccupiedInventoryRejectedWithoutSourceOrTargetMutation(SmokeContext context) {
        NBTTagList data=new NBTTagList();
        for(ItemStack item:new ItemStack[]{new ItemStack(Items.DIAMOND,7),new ItemStack(Items.IRON_INGOT,3)}) {
            NBTTagCompound entry=new NBTTagCompound();entry.setInteger("Slot",0);entry.setTag("Stack",item.writeToNBT(new NBTTagCompound()));data.appendTag(entry);
        }
        NBTTagList original=data.copy();InventoryBasic target=new InventoryBasic("test",false,9);target.setInventorySlotContents(0,new ItemStack(Items.GOLD_INGOT,2));
        boolean rejected=false;try {InventoryUtils.fromTag(data,target);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected&&data.equals(original)&&target.getStackInSlot(0).getItem()==Items.GOLD_INGOT&&target.getStackInSlot(0).getCount()==2,"Duplicate occupied native NBT silently overwrote inventory or rewrote source");
        context.succeed();
    }
    public static void delayedAndUnownedSettingsPacketsCannotMutateOtherBackpack(SmokeContext context) throws Exception {
        NativeTestPlayer player=new NativeTestPlayer(context.getLevel(),"InmisPacket");
        ItemStack first=bag(),second=bag();player.inventory.setInventorySlotContents(0,first);player.inventory.setInventorySlotContents(1,second);
        BackpackScreenHandler firstMenu=new BackpackScreenHandler(17,player.inventory,first);player.openContainer=firstMenu;
        BackpackAugmentsComponent updated=BackpackAugmentsComponent.DEFAULT.withFarmhandEnabled(true);
        ServerNetworking.UpdateBackpackAugmentsPacket original=new ServerNetworking.UpdateBackpackAugmentsPacket(17,updated);
        PacketBuffer bytes=new PacketBuffer(Unpooled.buffer());original.toBytes(bytes);
        ServerNetworking.UpdateBackpackAugmentsPacket decoded=new ServerNetworking.UpdateBackpackAugmentsPacket();decoded.fromBytes(bytes);bytes.release();
        Constructor<MessageContext> constructor=MessageContext.class.getDeclaredConstructor(net.minecraft.network.INetHandler.class,Side.class);constructor.setAccessible(true);
        MessageContext messageContext=constructor.newInstance(player.connection,Side.SERVER);
        ServerNetworking.AugmentsHandler handler=new ServerNetworking.AugmentsHandler();
        try {
            handler.onMessage(decoded,messageContext);
            check(Inmis.getOrCreateAugments(first,((BackpackItem)first.getItem()).getTier()).farmhandEnabled(),"Owned matching native menu settings packet was rejected");
            BackpackScreenHandler replacement=new BackpackScreenHandler(18,player.inventory,second);player.openContainer=replacement;
            NBTTagCompound before=Inmis.tag(second).copy();handler.onMessage(decoded,messageContext);
            check(Inmis.tag(second).equals(before),"Delayed settings packet mutated newly opened native backpack");
            player.inventory.setInventorySlotContents(1,ItemStack.EMPTY);
            handler.onMessage(new ServerNetworking.UpdateBackpackAugmentsPacket(18,updated),messageContext);
            check(Inmis.tag(second).equals(before),"Native settings packet mutated backpack no longer owned by player");
        } finally {player.setDead();}
        context.succeed();
    }
    private static ItemStack bag(){return new ItemStack(Inmis.BACKPACKS.stream().map(e->e.get()).filter(b->b.getTier().getName().equals("endless")).findFirst().orElseThrow(()->new AssertionError("Tier missing")));}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
