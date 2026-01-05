package draylar.inmis.compat;

import draylar.inmis.Inmis;
import draylar.inmis.client.CuriosBackpackRenderer;
import draylar.inmis.item.BackpackItem;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

public final class CuriosClientCompat {

    private CuriosClientCompat() {
    }

    public static void registerRenderers() {
        if (!Inmis.CONFIG.trinketRendering) {
            return;
        }

        for (RegistryObject<BackpackItem> backpack : Inmis.BACKPACKS) {
            CuriosRendererRegistry.register(backpack.get(), CuriosBackpackRenderer::new);
        }
    }
}
