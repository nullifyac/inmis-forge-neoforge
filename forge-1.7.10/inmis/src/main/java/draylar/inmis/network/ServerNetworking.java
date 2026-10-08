package draylar.inmis.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackAugmentHelper;
import draylar.inmis.compat.BaublesCompat;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.ui.BackpackScreenHandler;
import draylar.inmis.util.BackpackStorage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

/** All server inventory mutations are drained on the server tick thread. */
public final class ServerNetworking {
    private static final SimpleNetworkWrapper CHANNEL=NetworkRegistry.INSTANCE.newSimpleChannel("inmis");
    private static final ArrayBlockingQueue<Runnable> TASKS=new ArrayBlockingQueue<>(1024);
    private static final Map<UUID,ItemStack> SERVER_OPENINGS=new HashMap<>();
    private static volatile OpeningData clientOpening;
    private ServerNetworking(){ }
    public static void init(){
        CHANNEL.registerMessage(OpenHandler.class,OpenRequest.class,0,Side.SERVER);
        CHANNEL.registerMessage(UpdateHandler.class,UpdateRequest.class,1,Side.SERVER);
        CHANNEL.registerMessage(OpeningHandler.class,OpeningPacket.class,2,Side.CLIENT);
    }
    public static void drain(){Runnable task;while((task=TASKS.poll())!=null)task.run();}
    public static void sendOpenBackpack(){CHANNEL.sendToServer(new OpenRequest());}
    public static void sendUpdateBackpackAugments(int windowId,BackpackAugmentsComponent component){CHANNEL.sendToServer(new UpdateRequest(windowId,component));}
    public static void setClientOpening(OpeningData data){clientOpening=data;}
    public static OpeningData consumeClientOpening(){OpeningData data=clientOpening;clientOpening=null;return data;}
    public static BackpackScreenHandler createServerMenu(EntityPlayer player){ItemStack stack=SERVER_OPENINGS.remove(player.getUniqueID());return stack==null?null:new BackpackScreenHandler(player.inventory,stack);}
    /** Vanilla's air-use packet copies the held stack after Item.onItemRightClick returns. */
    public static void deferHeldOpening(EntityPlayer player,ItemStack stack){
        if(!(player instanceof EntityPlayerMP)||stack==null||player.inventory.getCurrentItem()!=stack)return;
        final EntityPlayerMP serverPlayer=(EntityPlayerMP)player;
        final int slot=player.inventory.currentItem;
        // Automatic augment reads also initialize this compound on fresh backpacks.
        Inmis.tag(stack);
        final ItemStack expected=stack.copy();
        TASKS.offer(()->{
            if(player.isDead||!serverPlayer.playerNetServerHandler.netManager.isChannelOpen()||player.inventory.currentItem!=slot)return;
            ItemStack current=player.inventory.getStackInSlot(slot);
            if(ItemStack.areItemStacksEqual(current,expected))openBackpack(player,current);
        });
    }
    public static void requestOpenBackpack(EntityPlayerMP player){
        TASKS.offer(()->{if(player.isDead)return;ItemStack stack=player.inventory.armorInventory[2];
            if(!(stack!=null&&stack.getItem() instanceof BackpackItem))stack=Inmis.CONFIG.enableTrinketCompatibility?BaublesCompat.findFirstEquippedBackpack(player):null;
            if(stack==null&&!Inmis.CONFIG.requireArmorTrinketToOpen)for(ItemStack candidate:BackpackAugmentHelper.getBackpackStacks(player)){stack=candidate;break;}
            if(stack!=null)openBackpack(player,stack);
        });
    }
    public static void openBackpack(EntityPlayer player,ItemStack stack){
        if(!(player instanceof EntityPlayerMP)||stack==null||!(stack.getItem() instanceof BackpackItem)||!owns(player,stack))return;
        if(Inmis.CONFIG.requireArmorTrinketToOpen&&!isEquipped(player,stack))return;
        try{
            OpeningData data=OpeningData.forStack(player.inventory,stack);data.validate();
            ItemStack display=stack.copy();if(display.hasTagCompound())display.getTagCompound().removeTag("Inventory");
            CHANNEL.sendTo(new OpeningPacket(new OpeningData(display,data.width,data.rows,data.configuredSize,data.lockedPlayerSlot)),(EntityPlayerMP)player);
            SERVER_OPENINGS.put(player.getUniqueID(),stack);
            player.openGui(Inmis.INSTANCE,0,player.worldObj,0,0,0);
            SERVER_OPENINGS.remove(player.getUniqueID());
            if(Inmis.CONFIG.playSound)player.worldObj.playSoundAtEntity(player,((BackpackItem)stack.getItem()).getTier().getOpenSound(),1F,1F);
        }catch(IllegalArgumentException e){player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("inmis.error.backpack_capacity"));Inmis.LOGGER.warn("Cannot open backpack safely; contents preserved",e);}
    }
    private static boolean owns(EntityPlayer player,ItemStack target){for(ItemStack stack:BackpackAugmentHelper.getBackpackStacks(player))if(stack==target)return true;return false;}
    private static boolean isEquipped(EntityPlayer player,ItemStack stack){return player.inventory.armorInventory[2]==stack||BaublesCompat.getEquippedBackpacks(player).contains(stack);}
    public static boolean applyUpdate(EntityPlayer player,int windowId,BackpackAugmentsComponent component){
        if(!(player.openContainer instanceof BackpackScreenHandler)||player.openContainer.windowId!=windowId)return false;
        BackpackScreenHandler menu=(BackpackScreenHandler)player.openContainer;
        if(!menu.canInteractWith(player))return false;
        Inmis.setAugments(menu.getBackpackStack(),menu.getItem().getTier(),component);player.inventory.markDirty();menu.detectAndSendChanges();return true;
    }
    public static final class OpeningData {
        public final ItemStack stack;public final int width,rows,configuredSize,lockedPlayerSlot;
        public OpeningData(ItemStack stack,int width,int rows,int configuredSize,int lockedPlayerSlot){this.stack=stack;this.width=width;this.rows=rows;this.configuredSize=configuredSize;this.lockedPlayerSlot=lockedPlayerSlot;}
        public static OpeningData forStack(InventoryPlayer inventory,ItemStack stack){
            BackpackItem item=(BackpackItem)stack.getItem();int slot=-1;for(int i=0;i<inventory.getSizeInventory();i++)if(inventory.getStackInSlot(i)==stack){slot=i;break;}
            return new OpeningData(stack,item.getTier().getRowWidth(),BackpackStorage.getRequiredRows(stack,item.getTier()),Math.multiplyExact(item.getTier().getRowWidth(),item.getTier().getNumberOfRows()),slot);
        }
        public void validate(){if(stack==null||!(stack.getItem() instanceof BackpackItem)||width<=0||rows<=0||configuredSize<=0
                ||(long)width*rows>Short.MAX_VALUE-36L||configuredSize>(long)width*rows||lockedPlayerSlot< -1||lockedPlayerSlot>=40)
            throw new IllegalArgumentException("Invalid backpack opening descriptor");}
    }
    public static final class OpenRequest implements IMessage {
        public void fromBytes(ByteBuf buffer){if(buffer.isReadable())throw new IllegalArgumentException("Unexpected opening payload");}
        public void toBytes(ByteBuf buffer){ }
    }
    public static final class OpenHandler implements IMessageHandler<OpenRequest,IMessage> {
        public IMessage onMessage(OpenRequest request,MessageContext context){requestOpenBackpack(context.getServerHandler().playerEntity);return null;}
    }
    public static final class UpdateRequest implements IMessage {
        int windowId;BackpackAugmentsComponent component;
        public UpdateRequest(){ }
        UpdateRequest(int windowId,BackpackAugmentsComponent component){this.windowId=windowId;this.component=component;}
        public void fromBytes(ByteBuf buffer){windowId=buffer.readUnsignedByte();component=BackpackAugmentsComponent.read(new LegacyBuffer(buffer));if(buffer.isReadable())throw new IllegalArgumentException("Trailing augment payload");}
        public void toBytes(ByteBuf buffer){buffer.writeByte(windowId);component.write(new LegacyBuffer(buffer));}
    }
    public static final class UpdateHandler implements IMessageHandler<UpdateRequest,IMessage> {
        public IMessage onMessage(UpdateRequest request,MessageContext context){final EntityPlayerMP player=context.getServerHandler().playerEntity;TASKS.offer(()->applyUpdate(player,request.windowId,request.component));return null;}
    }
    public static final class OpeningPacket implements IMessage {
        OpeningData data;public OpeningPacket(){ }OpeningPacket(OpeningData data){this.data=data;}
        public void fromBytes(ByteBuf buffer){ItemStack stack=ByteBufUtils.readItemStack(buffer);data=new OpeningData(stack,buffer.readInt(),buffer.readInt(),buffer.readInt(),buffer.readInt());data.validate();}
        public void toBytes(ByteBuf buffer){ByteBufUtils.writeItemStack(buffer,data.stack);buffer.writeInt(data.width);buffer.writeInt(data.rows);buffer.writeInt(data.configuredSize);buffer.writeInt(data.lockedPlayerSlot);}
    }
    public static final class OpeningHandler implements IMessageHandler<OpeningPacket,IMessage> {
        public IMessage onMessage(OpeningPacket packet,MessageContext context){Inmis.PROXY.receiveOpening(packet.data);return null;}
    }
}
