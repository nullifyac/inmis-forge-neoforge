package draylar.inmis.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;

import java.util.Locale;
import java.util.Map;

/** Minecraft 26 dye crafting requires an explicit recipe for each target item. */
public final class BackpackDyeRecipes {
    private BackpackDyeRecipes() {
    }

    public static void addRecipes(ModifyRecipeJsonsEvent event) {
        populateRecipes(event.getRecipeJsons(), Inmis.CONFIG.backpacks);
    }

    static void populateRecipes(Map<Identifier, JsonElement> recipes, Iterable<BackpackInfo> tiers) {
        for (BackpackInfo tier : tiers) {
            if (!tier.isDyeable()) {
                continue;
            }
            String itemName = tier.getName().toLowerCase(Locale.ROOT) + "_backpack";
            JsonObject recipe = new JsonObject();
            recipe.addProperty("type", "minecraft:crafting_dye");
            recipe.addProperty("category", "misc");
            recipe.addProperty("group", "inmis_dyed_backpacks");
            recipe.addProperty("target", Inmis.id(itemName).toString());
            recipe.addProperty("dye", "#minecraft:dyes");
            JsonObject result = new JsonObject();
            result.addProperty("id", Inmis.id(itemName).toString());
            recipe.add("result", result);
            // A datapack can replace the generated recipe using the same identifier.
            recipes.putIfAbsent(Inmis.id(itemName + "_dyed"), recipe);
        }
    }
}
