package draylar.inmis.network;
import draylar.inmis.Inmis;
import draylar.inmis.compat.BaublesCompat;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import java.util.*;
public final class ServerNetworking {
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("inmis");
    private static final Map<UUID,ItemStack> OPENING = new HashMap<>();
    private static final java.util.Queue<byte[]> CLIENT_OPENING = new java.util.concurrent.ConcurrentLinkedQueue<>();
    public static void init() {
        CHANNEL.registerMessage(OpenHandler.class, OpenBackpackPacket.class, 0, Side.SERVER);
        CHANNEL.registerMessage(AugmentsHandler.class, UpdateBackpackAugmentsPacket.class, 1, Side.SERVER);
        CHANNEL.registerMessage(OpeningHandler.class, OpeningPacket.class, 2, Side.CLIENT);
    }
    public static void sendOpenBackpack() { CHANNEL.sendToServer(new OpenBackpackPacket()); }
    public static void sendUpdateBackpackAugments(int windowId, BackpackAugmentsComponent value) { CHANNEL.sendToServer(new UpdateBackpackAugmentsPacket(windowId, value)); }
    public static void open(EntityPlayerMP player, ItemStack stack) {
        if (!(stack.getItem() instanceof BackpackItem)) return;
        boolean owned = draylar.inmis.augment.BackpackAugmentHelper.getBackpackStacks(player).stream().anyMatch(entry -> entry == stack);
        if (!owned) return;
        PacketBuffer data = new PacketBuffer(Unpooled.buffer());
        BackpackScreenHandler.writeOpeningData(data, player.inventory, stack);
        byte[] bytes = new byte[data.readableBytes()]; data.readBytes(bytes); data.release();
        OPENING.put(player.getUniqueID(), stack);
        try { CHANNEL.sendTo(new OpeningPacket(bytes), player); player.openGui(Inmis.INSTANCE, 0, player.world, 0, 0, 0); }
        finally { OPENING.remove(player.getUniqueID()); }
    }
    public static ItemStack getOpeningStack(EntityPlayer player) { return OPENING.getOrDefault(player.getUniqueID(), ItemStack.EMPTY); }
    public static void clearClientOpening() { CLIENT_OPENING.clear(); }
    public static PacketBuffer consumeClientOpening() {
        byte[] bytes = CLIENT_OPENING.poll();
        if (bytes == null) throw new IllegalStateException("Backpack opening data missing");
        return new PacketBuffer(Unpooled.wrappedBuffer(bytes));
    }
    private static ItemStack first(List<ItemStack> items) {
        for (ItemStack stack : items) if (stack.getItem() instanceof BackpackItem) return stack;
        return ItemStack.EMPTY;
    }
    public static final class OpenBackpackPacket implements IMessage {
        public void fromBytes(ByteBuf buf) {} public void toBytes(ByteBuf buf) {}
    }
    public static final class OpenHandler implements IMessageHandler<OpenBackpackPacket,IMessage> {
        public IMessage onMessage(OpenBackpackPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                ItemStack bag = ItemStack.EMPTY;
                if (Inmis.BAUBLES_LOADED && Inmis.CONFIG.enableTrinketCompatibility) bag = BaublesCompat.findFirstEquippedBackpack(player);
                if (bag.isEmpty() && !Inmis.CONFIG.requireArmorTrinketToOpen) bag = first(player.inventory.offHandInventory);
                if (bag.isEmpty() && !Inmis.CONFIG.requireArmorTrinketToOpen) bag = first(player.inventory.mainInventory);
                if (bag.isEmpty()) bag = first(player.inventory.armorInventory);
                if (!bag.isEmpty()) BackpackItem.openScreen(player, bag);
            }); return null;
        }
    }
    public static final class UpdateBackpackAugmentsPacket implements IMessage {
        private int windowId;
        private BackpackAugmentsComponent augments;
        public UpdateBackpackAugmentsPacket() {}
        public UpdateBackpackAugmentsPacket(int windowId, BackpackAugmentsComponent value) { this.windowId = windowId; augments = value; }
        public void fromBytes(ByteBuf buf) { PacketBuffer packet = new PacketBuffer(buf); windowId = packet.readVarInt(); augments = BackpackAugmentsComponent.read(packet); if (packet.isReadable()) throw new IllegalArgumentException("Trailing augment packet data"); }
        public void toBytes(ByteBuf buf) { PacketBuffer packet = new PacketBuffer(buf); packet.writeVarInt(windowId); augments.write(packet); }
    }
    public static final class AugmentsHandler implements IMessageHandler<UpdateBackpackAugmentsPacket,IMessage> {
        public IMessage onMessage(UpdateBackpackAugmentsPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.openContainer instanceof BackpackScreenHandler) {
                    BackpackScreenHandler menu = (BackpackScreenHandler)player.openContainer;
                    if (menu.windowId == message.windowId && menu.canInteractWith(player)) Inmis.setBackpackAugments(menu.getBackpackStack(), message.augments);
                }
            }); return null;
        }
    }
    public static final class OpeningPacket implements IMessage {
        private byte[] bytes;
        public OpeningPacket() {} public OpeningPacket(byte[] bytes) { this.bytes = bytes; }
        public void fromBytes(ByteBuf buf) {
            int length = new PacketBuffer(buf).readVarInt();
            if (length < 0 || length > 2097152 || length > buf.readableBytes()) throw new IllegalArgumentException("Invalid opening packet length");
            bytes = new byte[length]; buf.readBytes(bytes);
        }
        public void toBytes(ByteBuf buf) { new PacketBuffer(buf).writeVarInt(bytes.length); buf.writeBytes(bytes); }
    }
    public static final class OpeningHandler implements IMessageHandler<OpeningPacket,IMessage> {
        public IMessage onMessage(OpeningPacket message, MessageContext context) {
            // Store on receipt: FML's following GUI packet consumes this on the Minecraft thread.
            CLIENT_OPENING.add(message.bytes); return null;
        }
    }
}
