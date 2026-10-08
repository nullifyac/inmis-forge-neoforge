package draylar.inmis.mixin;
import draylar.inmis.Inmis;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EntityPlayerMP.class)
public abstract class PlayerMixin {
    @Inject(method = "onDeath", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/InventoryPlayer;dropAllItems()V"))
    private void inmis$spill(DamageSource source, CallbackInfo ci) { Inmis.spillVanillaInventoryOnDeath((EntityPlayerMP)(Object)this); }
}
