package draylar.inmis.mixin;
import draylar.inmis.Inmis;
import draylar.inmis.augment.*;
import draylar.inmis.item.BackpackItem;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EntityItem.class)
public class ItemEntityMixin {
    @Inject(method = "attackEntityFrom", at = @At("HEAD"), cancellable = true)
    private void inmis$fireImmune(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!source.isFireDamage()) return;
        ItemStack stack = ((EntityItem)(Object)this).getItem();
        if (stack.getItem() instanceof BackpackItem) {
            draylar.inmis.config.BackpackInfo tier = ((BackpackItem)stack.getItem()).getTier();
            boolean protectedItem = tier.isFireImmune() || (BackpackAugments.isUnlocked(tier, BackpackAugmentType.IMBUED_HIDE) && Inmis.getOrCreateAugments(stack, tier).imbuedHideEnabled());
            if (protectedItem) cir.setReturnValue(false);
        }
    }
}
