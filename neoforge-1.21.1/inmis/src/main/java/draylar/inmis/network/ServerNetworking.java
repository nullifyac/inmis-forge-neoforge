package draylar.inmis.network;

import draylar.inmis.Inmis;
import draylar.inmis.compat.CuriosCompat;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.EnderBackpackItem;
import net.minecraft.stats.Stats;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public final class ServerNetworking {

    private ServerNetworking() {
    }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                OpenBackpackPayload.TYPE,
                OpenBackpackPayload.STREAM_CODEC,
                ServerNetworking::handleOpenBackpack);
    }

    public static void sendOpenBackpack() {
        PacketDistributor.sendToServer(new OpenBackpackPayload());
    }

    private static void handleOpenBackpack(OpenBackpackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
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
                return;
            }

            if (findFirstEnderPouch(inventory) != ItemStack.EMPTY) {
                openEnderPouch(player);
            }
        });
    }

    private static ItemStack findFirstBackpack(List<ItemStack> items) {
        for (ItemStack stack : items) {
            if (stack.getItem() instanceof BackpackItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findFirstEnderPouch(Inventory inventory) {
        for (ItemStack stack : inventory.items) {
            if (stack.getItem() == Inmis.ENDER_POUCH.get()) {
                return stack;
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (stack.getItem() == Inmis.ENDER_POUCH.get()) {
                return stack;
            }
        }
        for (ItemStack stack : inventory.armor) {
            if (stack.getItem() == Inmis.ENDER_POUCH.get()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void openEnderPouch(Player player) {
        if (player.level().isClientSide) {
            return;
        }
        PlayerEnderChestContainer enderChestInventory = player.getEnderChestInventory();
        if (enderChestInventory == null) {
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (id, playerInventory, playerEntity) -> ChestMenu.threeRows(id, playerInventory, enderChestInventory),
                EnderBackpackItem.CONTAINER_NAME));
        player.awardStat(Stats.OPEN_ENDERCHEST);
    }
}
