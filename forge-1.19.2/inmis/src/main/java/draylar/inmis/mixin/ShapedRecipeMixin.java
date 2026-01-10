package draylar.inmis.mixin;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.util.InventoryUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
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
        if (craftingContainer.getContainerSize() <= 4) {
            return;
        }

        ItemStack centerSlot = craftingContainer.getItem(4);
        if (!(centerSlot.getItem() instanceof BackpackItem oldBackpack) || Inmis.isBackpackEmpty(centerSlot)) {
            return;
        }

        ItemStack newBackpack = this.getResultItem().copy();
        if (newBackpack.getItem() instanceof BackpackItem newBackpackItem) {
            int oldSize = oldBackpack.getTier().getRowWidth() * oldBackpack.getTier().getNumberOfRows();
            int newSize = newBackpackItem.getTier().getRowWidth() * newBackpackItem.getTier().getNumberOfRows();
            SimpleContainer oldInventory = new SimpleContainer(oldSize);
            SimpleContainer newInventory = new SimpleContainer(newSize);
            InventoryUtils.fromTag(Inmis.getOrCreateInventory(centerSlot), oldInventory);
            for (int i = 0; i < newSize; i++) {
                newInventory.setItem(i, i < oldSize ? oldInventory.getItem(i).copy() : ItemStack.EMPTY);
            }
            ListTag newTag = InventoryUtils.toTag(newInventory);
            newBackpack.getOrCreateTag().put("Inventory", newTag);

            CompoundTag tag = centerSlot.getTag();
            if (tag != null && tag.contains("Augments", Tag.TAG_COMPOUND)) {
                newBackpack.getOrCreateTag().put("Augments", tag.getCompound("Augments").copy());
            }
            cir.setReturnValue(newBackpack);
        }
    }
}
