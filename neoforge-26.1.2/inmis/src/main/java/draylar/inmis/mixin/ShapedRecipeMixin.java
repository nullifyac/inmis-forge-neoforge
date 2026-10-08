package draylar.inmis.mixin;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.item.component.BackpackComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ShapedRecipe.class)
public abstract class ShapedRecipeMixin {

    @Inject(method = "assemble", at = @At("RETURN"), cancellable = true)
    private void onCraft(CraftingInput craftingInput, CallbackInfoReturnable<ItemStack> cir) {
        if (craftingInput.size() <= 4) {
            return;
        }

        ItemStack centerSlot = craftingInput.getItem(4);
        if (!(centerSlot.getItem() instanceof BackpackItem) || Inmis.isBackpackEmpty(centerSlot)) {
            return;
        }

        ItemStack newBackpack = cir.getReturnValue();
        if (newBackpack.getItem() instanceof BackpackItem backpackItem) {
            int size = backpackItem.getTier().getRowWidth() * backpackItem.getTier().getNumberOfRows();
            List<ItemStack> contents = Inmis.getBackpackContents(centerSlot);
            // Preserve recovery slots even if the target tier's configured capacity is smaller.
            size = Math.max(size, contents.size());
            List<ItemStack> newContents = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                if (i < contents.size()) {
                    newContents.add(contents.get(i).copy());
                } else {
                    newContents.add(ItemStack.EMPTY);
                }
            }
            newBackpack.set(Inmis.BACKPACK_COMPONENT.get(), new BackpackComponent(newContents));
            BackpackAugmentsComponent augments = centerSlot.get(Inmis.BACKPACK_AUGMENTS.get());
            if (augments != null) {
                newBackpack.set(Inmis.BACKPACK_AUGMENTS.get(), augments);
            }
            cir.setReturnValue(newBackpack);
        }
    }
}
