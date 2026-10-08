package draylar.inmis.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public final class EnderBackpackItem extends Item {
    public EnderBackpackItem(){setMaxStackSize(1);setUnlocalizedName("inmis.ender_pouch");setTextureName("inmis:ender_pouch");setCreativeTab(CreativeTabs.tabMisc);}
    @Override public ItemStack onItemRightClick(ItemStack stack,World world,EntityPlayer player){if(!world.isRemote)player.displayGUIChest(player.getInventoryEnderChest());return stack;}
}
