package draylar.inmis.item;
import draylar.inmis.Inmis;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraft.init.SoundEvents;
import net.minecraft.stats.StatList;
public final class EnderBackpackItem extends Item {
    public EnderBackpackItem() { setMaxStackSize(1); setCreativeTab(Inmis.GROUP); }
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (Inmis.CONFIG.playSound && world.isRemote) world.playSound(player, player.getPosition(), SoundEvents.BLOCK_ENDERCHEST_OPEN, SoundCategory.PLAYERS, 1, 1);
        if (!world.isRemote) { player.displayGUIChest(player.getInventoryEnderChest()); player.addStat(StatList.ENDERCHEST_OPENED); }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }
}
