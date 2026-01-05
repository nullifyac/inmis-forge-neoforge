package draylar.inmis;

import draylar.inmis.client.BackpackFeature;
import draylar.inmis.client.InmisKeybinds;
import draylar.inmis.compat.CuriosClientCompat;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.ui.BackpackHandledScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.common.MinecraftForge;

@Mod.EventBusSubscriber(modid = Inmis.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class InmisClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(Inmis.CONTAINER_TYPE.get(), BackpackHandledScreen::new);
            if (Inmis.CURIOS_LOADED && Inmis.CONFIG.enableTrinketCompatibility) {
                CuriosClientCompat.registerRenderers();
            }
        });
        MinecraftForge.EVENT_BUS.addListener(InmisKeybinds::onClientTick);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        InmisKeybinds.register(event);
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        for (var backpackEntry : Inmis.BACKPACKS) {
            BackpackItem backpack = backpackEntry.get();
            if (backpack instanceof DyeableLeatherItem dyeable) {
                event.register((stack, tintIndex) -> tintIndex > 0 ? -1 : dyeable.getColor(stack), backpack);
            }
        }
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new BackpackFeature(renderer));
            }
        }
    }
}
