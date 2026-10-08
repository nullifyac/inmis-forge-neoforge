package draylar.inmis.mixin;
import draylar.inmis.augment.BackpackAugmentHandler;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ItemBow.class)
public abstract class BowItemMixin {
    @Inject(method = "findAmmo", at = @At("RETURN"), cancellable = true)
    private void inmis$findAmmo(EntityPlayer player, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack stored = BackpackAugmentHandler.locateAmmunition(player, new ItemStack((ItemBow)(Object)this), cir.getReturnValue());
        if (!stored.isEmpty()) cir.setReturnValue(stored);
    }
    @Inject(method = "onPlayerStoppedUsing", at = @At("RETURN"))
    private void inmis$syncAmmo(ItemStack stack, World world, EntityLivingBase user, int ticks, CallbackInfo ci) { BackpackAugmentHandler.finishQuiverlinkUse(); }
}
