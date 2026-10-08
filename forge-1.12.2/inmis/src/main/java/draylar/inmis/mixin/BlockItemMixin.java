package draylar.inmis.mixin;
import draylar.inmis.augment.PlaceSoundControls;
import net.minecraft.item.ItemBlock;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ItemBlock.class)
public abstract class BlockItemMixin {
    @Inject(method = "onItemUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;playSound(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/SoundEvent;Lnet/minecraft/util/SoundCategory;FF)V"))
    private void inmis$sound(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing face, float x, float y, float z, CallbackInfoReturnable<EnumActionResult> cir) { PlaceSoundControls.markAboutToPlay(); }
}
