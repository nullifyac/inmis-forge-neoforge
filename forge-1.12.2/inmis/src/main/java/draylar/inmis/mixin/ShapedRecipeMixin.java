package draylar.inmis.mixin;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapedRecipes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShapedRecipes.class)
public abstract class ShapedRecipeMixin {

    @Shadow
    public abstract ItemStack getRecipeOutput();

    @Inject(method = "getCraftingResult", at = @At("HEAD"), cancellable = true)
    private void onCraft(InventoryCrafting craftingContainer, CallbackInfoReturnable<ItemStack> cir) {
        if (craftingContainer.getSizeInventory() <= 4) {
            return;
        }

        ItemStack centerSlot = craftingContainer.getStackInSlot(4);
        if (!(centerSlot.getItem() instanceof BackpackItem)) {
            return;
        }

        ItemStack newBackpack = this.getRecipeOutput().copy();
        if (newBackpack.getItem() instanceof BackpackItem) {
            if (centerSlot.hasTagCompound()) newBackpack.setTagCompound(centerSlot.getTagCompound().copy());
            // Config reductions must not discard recovery slots when this backpack is upgraded.
            NBTTagList newTag = Inmis.getOrCreateInventory(centerSlot).copy();
            Inmis.tag(newBackpack).setTag("Inventory", newTag);

            NBTTagCompound tag = centerSlot.getTagCompound();
            if (tag != null && tag.hasKey("Augments", 10)) {
                Inmis.tag(newBackpack).setTag("Augments", tag.getCompoundTag("Augments").copy());
            }
            cir.setReturnValue(newBackpack);
        }
    }
}
