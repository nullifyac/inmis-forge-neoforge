package draylar.inmis.mixin;

import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackAugmentType;
import draylar.inmis.augment.BackpackAugments;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {

    @Inject(method = "fireImmune", at = @At("HEAD"), cancellable = true)
    private void inmis$fireImmune(CallbackInfoReturnable<Boolean> cir) {
        ItemEntity entity = (ItemEntity) (Object) this;
        ItemStack stack = entity.getItem();
        if (stack.getItem() instanceof BackpackItem backpackItem) {
            var tier = backpackItem.getTier();
            if (BackpackAugments.isUnlocked(tier, BackpackAugmentType.IMBUED_HIDE)) {
                BackpackAugmentsComponent augments = Inmis.getOrCreateAugments(stack, tier);
                cir.setReturnValue(augments.imbuedHideEnabled());
            }
        }
    }
}
