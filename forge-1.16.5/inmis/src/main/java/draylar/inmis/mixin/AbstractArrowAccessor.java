package draylar.inmis.mixin;

import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractArrowEntity.class)
public interface AbstractArrowAccessor {

    @Invoker("getPickupItem")
    ItemStack inmis$callGetPickupItem();
}
