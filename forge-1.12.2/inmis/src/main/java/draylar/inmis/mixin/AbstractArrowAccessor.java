package draylar.inmis.mixin;

import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityArrow.class)
public interface AbstractArrowAccessor {

    @Invoker("getArrowStack")
    ItemStack inmis$callGetPickupItem();
}
