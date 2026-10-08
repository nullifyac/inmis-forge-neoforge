package draylar.inmis;

import draylar.inmis.client.BackpackFeature;
import draylar.inmis.client.BackpackTintSource;
import draylar.inmis.client.InmisKeybinds;
import draylar.inmis.compat.CuriosClientCompat;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.ui.BackpackHandledScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Inmis.MOD_ID, dist = Dist.CLIENT)
public class InmisClient {
    public InmisClient(IEventBus eventBus, ModContainer modContainer) {
        eventBus.addListener(this::setupClient);
        eventBus.addListener(this::registerMenuScreens);
        eventBus.addListener(this::registerKeyMappings);
        eventBus.addListener(this::addLayers);
        eventBus.addListener(this::registerRenderStateModifiers);
        eventBus.addListener(this::registerTintSources);
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

    private void registerTintSources(final RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(Identifier.fromNamespaceAndPath("inmis", "backpack"), BackpackTintSource.CODEC);
    }

    private void registerKeyMappings(final RegisterKeyMappingsEvent event) {
        InmisKeybinds.register(event);
    }

    private void addLayers(final EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            var renderer = event.getPlayerRenderer(skin);
            if (renderer != null) {
                renderer.addLayer(new BackpackFeature(renderer));
            }
        }
    }

    private void registerRenderStateModifiers(final RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                var stack = avatar.getItemBySlot(EquipmentSlot.CHEST);
                ItemStackRenderState backpack = null;
                if (stack.getItem() instanceof BackpackItem) {
                    backpack = new ItemStackRenderState();
                    Minecraft.getInstance().getItemModelResolver()
                            .updateForLiving(backpack, stack, ItemDisplayContext.FIXED, avatar);
                }
                state.setRenderData(BackpackFeature.BACKPACK, backpack);
            }
        });
    }
}
