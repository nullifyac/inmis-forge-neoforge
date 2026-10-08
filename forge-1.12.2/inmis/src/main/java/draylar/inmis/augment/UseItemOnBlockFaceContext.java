package draylar.inmis.augment;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
/** Executes native item placement with the stored item while preserving the held stack. */
public final class UseItemOnBlockFaceContext {
    public static EnumActionResult use(WorldServer world, EntityPlayerMP player, ItemStack stored, BlockPos pos, EnumFacing face) {
        ItemStack held = player.getHeldItem(EnumHand.MAIN_HAND);
        player.setHeldItem(EnumHand.MAIN_HAND, stored);
        try { return stored.onItemUse(player, world, pos, EnumHand.MAIN_HAND, face, 0.5f, 1, 0.5f); }
        finally { player.setHeldItem(EnumHand.MAIN_HAND, held); }
    }
}
