package draylar.inmis.mixin;

import draylar.inmis.item.BackpackItem;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShapedRecipe.class)
public abstract class ShapedRecipeMixin {

    @Shadow
    public abstract ItemStack getResultItem();

    @Inject(method = "assemble", at = @At("HEAD"), cancellable = true)
    private void onCraft(CraftingContainer craftingContainer, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack centerSlot = craftingContainer.getItem(4);

        if (centerSlot.getItem() instanceof BackpackItem) {
            ItemStack newBackpack = this.getResultItem().copy();

            if (newBackpack.getItem() instanceof BackpackItem) {
                ListTag oldTag = centerSlot.getOrCreateTag().getList("Inventory", Tag.TAG_COMPOUND);
                newBackpack.getOrCreateTag().put("Inventory", oldTag.copy());
                cir.setReturnValue(newBackpack);
            }
        }
    }
}
