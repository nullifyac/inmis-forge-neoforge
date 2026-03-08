package draylar.inmis.compat;

import draylar.inmis.Inmis;
import draylar.inmis.client.AccessoriesBackpackRenderer;
import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;

public final class AccessoriesClientCompat {

    private AccessoriesClientCompat() {
    }

    public static void registerRenderers() {
        for (var backpack : Inmis.BACKPACKS) {
            AccessoriesRendererRegistry.registerRenderer(backpack.get(), AccessoriesBackpackRenderer::new);
        }
        AccessoriesRendererRegistry.registerRenderer(Inmis.ENDER_POUCH.get(), AccessoriesBackpackRenderer::new);
    }
}
