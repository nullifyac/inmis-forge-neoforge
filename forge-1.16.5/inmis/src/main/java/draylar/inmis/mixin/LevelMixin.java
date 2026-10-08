package draylar.inmis.mixin;

import draylar.inmis.augment.PlaceSoundControls;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundCategory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import javax.annotation.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class LevelMixin {
    @Shadow
    public abstract void playSound(@Nullable PlayerEntity player, double x, double y, double z, SoundEvent event, SoundCategory source, float volume, float pitch);

    @Inject(method = "playSound(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/SoundEvent;Lnet/minecraft/util/SoundCategory;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void inmis$placeSoundControls(PlayerEntity player, BlockPos pos, SoundEvent event, SoundCategory source, float volume, float pitch, CallbackInfo ci) {
        World level = (World) (Object) this;
        if (!(level instanceof ServerWorld)) {
            return;
        }
        if (!PlaceSoundControls.isAboutToPlay()) {
            return;
        }
        if (PlaceSoundControls.shouldPreventNextPlay()) {
            ci.cancel();
            return;
        }
        if (PlaceSoundControls.shouldSendToAllPlayers()) {
            this.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, event, source, volume, pitch);
            ci.cancel();
        }
    }
}
