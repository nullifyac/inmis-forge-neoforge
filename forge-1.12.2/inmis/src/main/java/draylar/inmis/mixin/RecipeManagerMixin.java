package draylar.inmis.mixin;
import draylar.inmis.Inmis;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(CraftingManager.class)
public class RecipeManagerMixin {
    @Redirect(method = {"findMatchingRecipe", "getRemainingItems"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/item/crafting/IRecipe;matches(Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraft/world/World;)Z"))
    private static boolean inmis$recipeFilter(IRecipe recipe, InventoryCrafting inventory, World world) {
        net.minecraft.util.ResourceLocation id = recipe.getRegistryName();
        if (id != null && "backpacked".equals(id.getResourceDomain()) && Inmis.CONFIG != null && Inmis.CONFIG.importBackpackedItems && ("backpack".equals(id.getResourcePath()) || id.getResourcePath().endsWith("backpack_shelf"))) return false;
        return recipe.matches(inventory, world);
    }
}
