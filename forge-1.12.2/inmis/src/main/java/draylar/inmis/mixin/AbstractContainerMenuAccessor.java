package draylar.inmis.mixin;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Container.class)
public interface AbstractContainerMenuAccessor {

    @Accessor("inventorySlots")
    java.util.List<Slot> inmis$getSlots();
}
