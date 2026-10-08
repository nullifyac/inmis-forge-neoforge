package draylar.inmis;

import draylar.inmis.client.BackpackFeature;
import draylar.inmis.client.InmisKeybinds;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.DyeableBackpackItem;
import draylar.inmis.network.ServerNetworking;
import draylar.inmis.ui.BackpackHandledScreen;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/** Loaded by the sided proxy only on a physical client. */
public class InmisClient extends CommonProxy {
    @Override
    public void preInit() {
        MinecraftForge.EVENT_BUS.register(this);
        InmisKeybinds.register();
        net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(InmisKeybinds.class);
    }

    @Override
    public void init() {
        Minecraft minecraft = Minecraft.getMinecraft();
        for (RenderPlayer renderer : minecraft.getRenderManager().getSkinMap().values()) {
            renderer.addLayer(new BackpackFeature(renderer));
        }
        for (draylar.inmis.LegacyRegistryEntry<BackpackItem> entry : Inmis.BACKPACKS) {
            BackpackItem backpack = entry.get();
            if (backpack instanceof DyeableBackpackItem) {
                minecraft.getItemColors().registerItemColorHandler(
                        (stack, tintIndex) -> tintIndex > 0 ? -1 : ((DyeableBackpackItem) backpack).getColor(stack), backpack);
            }
        }
    }

    @SubscribeEvent
    public void registerItemModels(ModelRegistryEvent event) {
        for (draylar.inmis.LegacyRegistryEntry<BackpackItem> entry : Inmis.BACKPACKS) registerItemModel(entry.get());
        registerItemModel(Inmis.ENDER_POUCH.get());
    }

    @SubscribeEvent
    public void connectedToServer(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        ServerNetworking.clearClientOpening();
    }

    @SubscribeEvent
    public void disconnectedFromServer(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ServerNetworking.clearClientOpening();
    }

    private static void registerItemModel(net.minecraft.item.Item item) {
        ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != 0) return null;
        PacketBuffer opening = ServerNetworking.consumeClientOpening();
        if (opening == null) return null;
        try {
            BackpackScreenHandler menu = new BackpackScreenHandler(0, player.inventory, opening);
            return new BackpackHandledScreen(menu, player.inventory, new TextComponentString(menu.getBackpackStack().getDisplayName()));
        } finally {
            opening.release();
        }
    }
}
