package draylar.inmis.mixin;
import draylar.inmis.compat.BaublesCompat;
import net.minecraft.entity.item.EntityItem;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Its config plugin skips this target when optional Baubles is absent. */
@Mixin(targets = "baubles.common.event.EventHandlerEntity", remap = false)
public abstract class BaublesDeathMixin {
    @Redirect(method = "dropItemsAt", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"), remap = false)
    private boolean inmis$nativeSelectedDrop(List<EntityItem> drops, Object selected) {
        EntityItem item = (EntityItem)selected;
        boolean added = drops.add(item);
        if (added) BaublesCompat.spillSelectedDrop(item,drops);
        return added;
    }
}
