package draylar.inmis.mixin;

import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Container.class)
public interface AbstractContainerMenuAccessor {

    @Accessor("slots")
    java.util.List<Slot> inmis$getSlots();
}
