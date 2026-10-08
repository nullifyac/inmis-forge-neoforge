package draylar.inmis.compat;

import draylar.inmis.Inmis;
import draylar.inmis.client.CuriosBackpackRenderer;
import draylar.inmis.item.BackpackItem;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

public final class CuriosClientCompat {

    private CuriosClientCompat() {
    }

    public static void registerRenderers() {
        if (!Inmis.CONFIG.trinketRendering) {
            return;
        }

        for (var backpack : Inmis.BACKPACKS) {
            BackpackItem item = backpack.get();
            CuriosRendererRegistry.register(item, CuriosBackpackRenderer::new);
        }
    }
}
