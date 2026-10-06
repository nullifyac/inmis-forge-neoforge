package draylar.inmis.mixin;

import draylar.inmis.Inmis;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "dropEquipment", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;dropAll()V", shift = At.Shift.BEFORE))
    private void inmis$spillBackpacksBeforeInventoryDrops(CallbackInfo ci) {
        // Vanilla reaches this call only after its keepInventory check and Vanishing Curse purge.
        Inmis.spillVanillaInventoryOnDeath((Player) (Object) this);
    }
}
