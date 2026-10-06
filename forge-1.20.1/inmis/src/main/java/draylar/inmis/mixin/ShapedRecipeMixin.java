package draylar.inmis.mixin;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
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
    public abstract ItemStack getResultItem(RegistryAccess registryAccess);

    @Inject(method = "assemble", at = @At("HEAD"), cancellable = true)
    private void onCraft(CraftingContainer craftingContainer, RegistryAccess registryAccess, CallbackInfoReturnable<ItemStack> cir) {
        if (craftingContainer.getContainerSize() <= 4) {
            return;
        }

        ItemStack centerSlot = craftingContainer.getItem(4);
        if (!(centerSlot.getItem() instanceof BackpackItem) || Inmis.isBackpackEmpty(centerSlot)) {
            return;
        }

        ItemStack newBackpack = this.getResultItem(registryAccess).copy();
        if (newBackpack.getItem() instanceof BackpackItem) {
            // Config reductions must not discard recovery slots when this backpack is upgraded.
            ListTag newTag = Inmis.getOrCreateInventory(centerSlot).copy();
            newBackpack.getOrCreateTag().put("Inventory", newTag);

            CompoundTag tag = centerSlot.getTag();
            if (tag != null && tag.contains("Augments", Tag.TAG_COMPOUND)) {
                newBackpack.getOrCreateTag().put("Augments", tag.getCompound("Augments").copy());
            }
            cir.setReturnValue(newBackpack);
        }
    }
}
