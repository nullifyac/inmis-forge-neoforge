/*
 * Backpacked-derived portions by MrCrayfish and contributors (GNU LGPL v2.1).
 * Source: https://github.com/MrCrayfish/Backpacked/blob/73db4d7cd729ebcd05b4cab8d7c0f7be9eeaa19a/common/src/main/java/com/mrcrayfish/backpacked/mixin/common/BlockItemMixin.java
 * Adapted for Inmis packages and Minecraft/loader APIs.
 * Attribution notice added 2026-10-08; see THIRD-PARTY-NOTICES.txt and
 * LICENSES/Backpacked-LGPL-2.1.txt in the main resources.
 */
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
