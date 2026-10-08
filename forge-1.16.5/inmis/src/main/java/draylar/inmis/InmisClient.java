package draylar.inmis;

import draylar.inmis.client.BackpackFeature;
import draylar.inmis.client.InmisKeybinds;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.ui.BackpackHandledScreen;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.renderer.entity.PlayerRenderer;
import net.minecraft.item.IDyeableArmorItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Inmis.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class InmisClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ScreenManager.register(Inmis.CONTAINER_TYPE.get(), BackpackHandledScreen::new);
            InmisKeybinds.register();
            for (PlayerRenderer renderer : net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getSkinMap().values()) {
                renderer.addLayer(new BackpackFeature(renderer));
            }
        });
        MinecraftForge.EVENT_BUS.addListener(InmisKeybinds::onClientTick);
    }

    @SubscribeEvent
    public static void registerItemColors(ColorHandlerEvent.Item event) {
        for (net.minecraftforge.fml.RegistryObject<BackpackItem> backpackEntry : Inmis.BACKPACKS) {
            BackpackItem backpack = backpackEntry.get();
            if (backpack instanceof IDyeableArmorItem) {
                IDyeableArmorItem dyeable = (IDyeableArmorItem) backpack;
                event.getItemColors().register(
                        (stack, tintIndex) -> tintIndex > 0 ? -1 : dyeable.getColor(stack),
                        backpack);
            }
        }
    }

}
