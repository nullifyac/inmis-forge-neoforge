package draylar.inmis.compat;

import baubles.api.IBauble;
import baubles.api.BaubleType;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

final class BaublesBackpackItem extends BackpackItem implements IBauble {
    BaublesBackpackItem(BackpackInfo tier){super(tier);}
    public BaubleType getBaubleType(ItemStack stack){return BaubleType.AMULET;}
    public void onWornTick(ItemStack stack,EntityLivingBase wearer){ }
    public void onEquipped(ItemStack stack,EntityLivingBase wearer){ }
    public void onUnequipped(ItemStack stack,EntityLivingBase wearer){ }
    public boolean canEquip(ItemStack stack,EntityLivingBase wearer){return wearer instanceof EntityPlayer&&BaublesCompat.canEquip();}
    public boolean canUnequip(ItemStack stack,EntityLivingBase wearer){return wearer instanceof EntityPlayer&&BaublesCompat.canUnequip(stack,(EntityPlayer)wearer);}
}
