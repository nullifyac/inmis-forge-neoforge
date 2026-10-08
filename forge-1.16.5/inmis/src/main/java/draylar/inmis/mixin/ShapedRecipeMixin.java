package draylar.inmis.mixin;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapedRecipe;
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
    private void onCraft(CraftingInventory craftingContainer, CallbackInfoReturnable<ItemStack> cir) {
        if (craftingContainer.getContainerSize() <= 4) {
            return;
        }

        ItemStack centerSlot = craftingContainer.getItem(4);
        if (!(centerSlot.getItem() instanceof BackpackItem) || Inmis.isBackpackEmpty(centerSlot)) {
            return;
        }

        ItemStack newBackpack = this.getResultItem().copy();
        if (newBackpack.getItem() instanceof BackpackItem) {
            // Config reductions must not discard recovery slots when this backpack is upgraded.
            ListNBT newTag = Inmis.getOrCreateInventory(centerSlot).copy();
            newBackpack.getOrCreateTag().put("Inventory", newTag);

            CompoundNBT tag = centerSlot.getTag();
            if (tag != null && tag.contains("Augments", 10)) {
                newBackpack.getOrCreateTag().put("Augments", tag.getCompound("Augments").copy());
            }
            cir.setReturnValue(newBackpack);
        }
    }
}
