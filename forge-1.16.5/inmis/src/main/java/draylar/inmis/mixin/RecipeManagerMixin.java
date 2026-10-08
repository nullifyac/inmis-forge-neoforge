package draylar.inmis.mixin;

import com.google.gson.JsonElement;
import draylar.inmis.Inmis;
import net.minecraft.util.ResourceLocation;
import net.minecraft.resources.IResourceManager;
import net.minecraft.profiler.IProfiler;
import net.minecraft.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin {

    private static final Set<ResourceLocation> INMIS$BACKPACKED_RECIPES = java.util.Collections.unmodifiableSet(new java.util.HashSet<>(java.util.Arrays.asList(
            new ResourceLocation("backpacked", "backpack"),
            new ResourceLocation("backpacked", "acacia_backpack_shelf"),
            new ResourceLocation("backpacked", "birch_backpack_shelf"),
            new ResourceLocation("backpacked", "cherry_backpack_shelf"),
            new ResourceLocation("backpacked", "crimson_backpack_shelf"),
            new ResourceLocation("backpacked", "dark_oak_backpack_shelf"),
            new ResourceLocation("backpacked", "jungle_backpack_shelf"),
            new ResourceLocation("backpacked", "oak_backpack_shelf"),
            new ResourceLocation("backpacked", "spruce_backpack_shelf"),
            new ResourceLocation("backpacked", "warped_backpack_shelf")
    )));

    @Inject(method = "apply", at = @At("HEAD"))
    private void inmis$blockBackpackedRecipes(Map<ResourceLocation, JsonElement> map, IResourceManager resourceManager,
                                              IProfiler profiler, CallbackInfo ci) {
        if (map.isEmpty() || Inmis.CONFIG == null || !Inmis.CONFIG.importBackpackedItems) {
            return;
        }

        int removed = 0;
        for (ResourceLocation id : INMIS$BACKPACKED_RECIPES) {
            if (map.remove(id) != null) {
                removed++;
            }
        }

        if (removed > 0) {
            Inmis.LOGGER.debug("Blocked {} Backpacked recipe(s) because importBackpackedItems is enabled.", removed);
        }
    }
}
