package draylar.inmis.mixin;

import draylar.inmis.augment.PlaceSoundControls;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public abstract class LevelMixin {
    @Shadow
    public abstract void playSound(@Nullable Player player, double x, double y, double z, SoundEvent event, SoundSource source, float volume, float pitch);

    @Inject(method = "playSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void inmis$placeSoundControls(Player player, BlockPos pos, SoundEvent event, SoundSource source, float volume, float pitch, CallbackInfo ci) {
        Level level = (Level) (Object) this;
        if (!(level instanceof ServerLevel)) {
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
