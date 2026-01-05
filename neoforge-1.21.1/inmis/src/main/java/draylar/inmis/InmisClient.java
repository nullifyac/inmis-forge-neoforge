package draylar.inmis;

import draylar.inmis.client.BackpackFeature;
import draylar.inmis.client.InmisKeybinds;
import draylar.inmis.compat.CuriosClientCompat;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.DyeableBackpackItem;
import draylar.inmis.ui.BackpackHandledScreen;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Inmis.MOD_ID, dist = Dist.CLIENT)
public class InmisClient {

    public InmisClient(IEventBus eventBus, ModContainer modContainer) {
        eventBus.addListener(this::setupClient);
        eventBus.addListener(this::registerMenuScreens);
        eventBus.addListener(this::registerKeyMappings);
        eventBus.addListener(this::registerItemColors);
        eventBus.addListener(this::addLayers);
    }

    private void setupClient(final FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(InmisKeybinds::onClientTick);

        if (Inmis.CURIOS_LOADED && Inmis.CONFIG.enableTrinketCompatibility) {
            event.enqueueWork(CuriosClientCompat::registerRenderers);
        }
    }

    private void registerMenuScreens(final RegisterMenuScreensEvent event) {
        event.register(Inmis.CONTAINER_TYPE.get(), BackpackHandledScreen::new);
    }

    private void registerKeyMappings(final RegisterKeyMappingsEvent event) {
        InmisKeybinds.register(event);
    }

    private void registerItemColors(final RegisterColorHandlersEvent.Item event) {
        for (var backpackEntry : Inmis.BACKPACKS) {
            BackpackItem backpack = backpackEntry.get();
            if (backpack instanceof DyeableBackpackItem) {
                event.register((stack, tintIndex) -> tintIndex > 0
                        ? -1
                        : DyedItemColor.getOrDefault(stack, DyedItemColor.LEATHER_COLOR), backpack);
            }
        }
    }

    private void addLayers(final EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new BackpackFeature(renderer));
            }
        }
    }
}
