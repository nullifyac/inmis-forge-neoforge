package draylar.inmis.item;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import net.minecraft.item.ItemStack;

public class DyeableBackpackItem extends BackpackItem {
    public DyeableBackpackItem(BackpackInfo tier){super(tier);}
    public int getColor(ItemStack stack){return stack.hasTagCompound()&&stack.getTagCompound().hasKey("display",10)
            &&stack.getTagCompound().getCompoundTag("display").hasKey("color",3)?stack.getTagCompound().getCompoundTag("display").getInteger("color"):0xA06540;}
    public void setColor(ItemStack stack,int color){net.minecraft.nbt.NBTTagCompound display=Inmis.tag(stack).getCompoundTag("display");display.setInteger("color",color);Inmis.tag(stack).setTag("display",display);}
    @Override public int getColorFromItemStack(ItemStack stack,int pass){return getColor(stack);}
}
