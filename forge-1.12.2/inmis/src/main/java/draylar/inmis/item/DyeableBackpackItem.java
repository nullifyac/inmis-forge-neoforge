package draylar.inmis.item;
import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
public class DyeableBackpackItem extends BackpackItem {
    public DyeableBackpackItem(BackpackInfo info) { super(info); }
    public boolean hasColor(ItemStack stack) { return stack.hasTagCompound() && stack.getTagCompound().getCompoundTag("display").hasKey("color", 99); }
    public int getColor(ItemStack stack) { return hasColor(stack) ? stack.getTagCompound().getCompoundTag("display").getInteger("color") : 10511680; }
    public void setColor(ItemStack stack, int color) {
        NBTTagCompound tag = Inmis.tag(stack); NBTTagCompound display = tag.getCompoundTag("display");
        display.setInteger("color", color); tag.setTag("display", display);
    }
    public void removeColor(ItemStack stack) { if (stack.hasTagCompound()) stack.getTagCompound().getCompoundTag("display").removeTag("color"); }
}
