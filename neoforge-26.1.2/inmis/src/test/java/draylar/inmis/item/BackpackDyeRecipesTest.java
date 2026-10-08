package draylar.inmis.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BackpackDyeRecipesTest {
    @Test
    void enabledTiersReceiveNativeDyeRecipesAndDisabledTiersDoNot() {
        Map<Identifier, JsonElement> recipes = new HashMap<>();
        BackpackDyeRecipes.populateRecipes(recipes, List.of(
                tier("frayed", true), tier("plated", true), tier("gilded", false)));

        assertEquals(2, recipes.size());
        for (String tier : List.of("frayed", "plated")) {
            JsonObject recipe = recipes.get(Inmis.id(tier + "_backpack_dyed")).getAsJsonObject();
            assertEquals("minecraft:crafting_dye", recipe.get("type").getAsString());
            assertEquals("#minecraft:dyes", recipe.get("dye").getAsString());
            assertEquals("inmis:" + tier + "_backpack", recipe.get("target").getAsString());
            assertEquals(recipe.get("target"), recipe.getAsJsonObject("result").get("id"));
        }
        assertFalse(recipes.containsKey(Inmis.id("gilded_backpack_dyed")));
    }

    @Test
    void nondefaultNamesFollowRegisteredLowercaseItemNames() {
        Map<Identifier, JsonElement> recipes = new HashMap<>();
        BackpackDyeRecipes.populateRecipes(recipes, List.of(tier("Custom", true)));

        assertEquals("inmis:custom_backpack", recipes.get(Inmis.id("custom_backpack_dyed"))
                .getAsJsonObject().get("target").getAsString());
    }

    @Test
    void explicitDatapackOverrideIsPreserved() {
        Map<Identifier, JsonElement> recipes = new HashMap<>();
        JsonObject override = new JsonObject();
        override.addProperty("type", "custom:dye_recipe");
        recipes.put(Inmis.id("frayed_backpack_dyed"), override);

        BackpackDyeRecipes.populateRecipes(recipes, List.of(tier("frayed", true)));

        assertSame(override, recipes.get(Inmis.id("frayed_backpack_dyed")));
    }

    private static BackpackInfo tier(String name, boolean dyeable) {
        return new BackpackInfo(name, 9, 1, false, "minecraft:item.armor.equip_leather", dyeable);
    }
}
