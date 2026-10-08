package draylar.inmis.compat;

import com.google.gson.JsonElement;
import draylar.inmis.Inmis;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Set;

public class BackpackedRecipeFilter {

    private static final Set<Identifier> INMIS$BACKPACKED_RECIPES = Set.of(
            Identifier.fromNamespaceAndPath("backpacked", "backpack"),
            Identifier.fromNamespaceAndPath("backpacked", "acacia_backpack_shelf"),
            Identifier.fromNamespaceAndPath("backpacked", "birch_backpack_shelf"),
            Identifier.fromNamespaceAndPath("backpacked", "cherry_backpack_shelf"),
            Identifier.fromNamespaceAndPath("backpacked", "crimson_backpack_shelf"),
            Identifier.fromNamespaceAndPath("backpacked", "dark_oak_backpack_shelf"),
            Identifier.fromNamespaceAndPath("backpacked", "jungle_backpack_shelf"),
            Identifier.fromNamespaceAndPath("backpacked", "oak_backpack_shelf"),
            Identifier.fromNamespaceAndPath("backpacked", "spruce_backpack_shelf"),
            Identifier.fromNamespaceAndPath("backpacked", "warped_backpack_shelf")
    );


    public static void blockBackpackedRecipes(net.neoforged.neoforge.event.ModifyRecipeJsonsEvent event) {
        Map<Identifier, JsonElement> map = event.getRecipeJsons();
        if (map.isEmpty() || Inmis.CONFIG == null || !Inmis.CONFIG.importBackpackedItems) {
            return;
        }

        int removed = 0;
        for (Identifier id : INMIS$BACKPACKED_RECIPES) {
            if (map.remove(id) != null) {
                removed++;
            }
        }

        if (removed > 0) {
            Inmis.LOGGER.debug("Blocked {} Backpacked recipe(s) because importBackpackedItems is enabled.", removed);
        }
    }
}
