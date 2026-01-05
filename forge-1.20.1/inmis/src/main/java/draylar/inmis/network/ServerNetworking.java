package draylar.inmis.network;

import draylar.inmis.Inmis;
import draylar.inmis.compat.CuriosCompat;
import draylar.inmis.item.BackpackItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.function.Supplier;

public class ServerNetworking {

    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Inmis.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void init() {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenBackpackPacket.class, OpenBackpackPacket::encode, OpenBackpackPacket::decode, OpenBackpackPacket::handle);
    }

    public static void sendOpenBackpack() {
        CHANNEL.sendToServer(new OpenBackpackPacket());
    }

    private static ItemStack findFirstBackpack(List<ItemStack> items) {
        for (ItemStack stack : items) {
            if (stack.getItem() instanceof BackpackItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static class OpenBackpackPacket {

        public static void encode(OpenBackpackPacket msg, FriendlyByteBuf buf) {
        }

        public static OpenBackpackPacket decode(FriendlyByteBuf buf) {
            return new OpenBackpackPacket();
        }

        public static void handle(OpenBackpackPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) {
                    return;
                }

                if (Inmis.CURIOS_LOADED && Inmis.CONFIG.enableTrinketCompatibility) {
                    ItemStack curioBackpack = CuriosCompat.findFirstEquippedBackpack(player);
                    if (!curioBackpack.isEmpty()) {
                        BackpackItem.openScreen(player, curioBackpack);
                        return;
                    }
                }

                Inventory inventory = player.getInventory();
                ItemStack firstBackpackItemStack = ItemStack.EMPTY;

                if (!Inmis.CONFIG.requireArmorTrinketToOpen) {
                    firstBackpackItemStack = findFirstBackpack(inventory.offhand);
                    if (firstBackpackItemStack.isEmpty()) {
                        firstBackpackItemStack = findFirstBackpack(inventory.items);
                    }
                }

                if (firstBackpackItemStack.isEmpty()) {
                    firstBackpackItemStack = findFirstBackpack(inventory.armor);
                }

                if (!firstBackpackItemStack.isEmpty()) {
                    BackpackItem.openScreen(player, firstBackpackItemStack);
                }
            });
            ctx.setPacketHandled(true);
        }
    }
}
