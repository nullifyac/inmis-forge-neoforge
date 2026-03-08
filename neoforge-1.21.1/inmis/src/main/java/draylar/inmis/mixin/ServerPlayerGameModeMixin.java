package draylar.inmis.mixin;

import draylar.inmis.augment.BackpackAugmentHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {

    @Shadow
    @Final
    protected ServerPlayer player;

    @Unique
    private BlockState inmis$capturedMinedState;

    @Unique
    private BlockPos inmis$capturedMinedPos;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void inmis$captureMinedBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        this.inmis$capturedMinedState = this.player.serverLevel().getBlockState(pos);
        this.inmis$capturedMinedPos = pos.immutable();
    }

    @Inject(
            method = "destroyAndAck",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayerGameMode;debugLogging(Lnet/minecraft/core/BlockPos;ZILjava/lang/String;)V",
                    ordinal = 0
            )
    )
    private void inmis$afterSuccessfulDestroy(BlockPos pos, int action, String reason, CallbackInfo ci) {
        if (this.inmis$capturedMinedState != null && this.inmis$capturedMinedPos != null) {
            BackpackAugmentHandler.onBlockBroken(this.player, this.inmis$capturedMinedState, this.inmis$capturedMinedPos);
        }
    }

    @Inject(method = "destroyAndAck", at = @At("TAIL"))
    private void inmis$clearCapturedMinedBlock(BlockPos pos, int action, String reason, CallbackInfo ci) {
        this.inmis$capturedMinedState = null;
        this.inmis$capturedMinedPos = null;
    }
}

