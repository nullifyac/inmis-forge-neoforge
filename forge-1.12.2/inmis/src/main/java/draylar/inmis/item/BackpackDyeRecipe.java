package draylar.inmis.item;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.*;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;
/** Native 1.12 dye metadata, using vanilla leather-armor brightness mixing. */
public final class BackpackDyeRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
    public boolean matches(InventoryCrafting grid, World world) {
        int bags=0,dyes=0;
        for (int i=0;i<grid.getSizeInventory();i++) {
            ItemStack stack=grid.getStackInSlot(i); if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof DyeableBackpackItem) bags++;
            else if (stack.getItem() == net.minecraft.init.Items.DYE) dyes++;
            else return false;
        }
        return bags==1 && dyes>0;
    }
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        ItemStack result=ItemStack.EMPTY; int[] sum=new int[3]; int count=0,brightness=0;
        for (int i=0;i<grid.getSizeInventory();i++) {
            ItemStack stack=grid.getStackInSlot(i); if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof DyeableBackpackItem) {
                if (!result.isEmpty()) return ItemStack.EMPTY;
                result=stack.copy(); result.setCount(1); DyeableBackpackItem item=(DyeableBackpackItem)stack.getItem();
                if (item.hasColor(stack)) { int color=item.getColor(stack); int r=color>>16&255,g=color>>8&255,b=color&255; sum[0]+=r;sum[1]+=g;sum[2]+=b;brightness+=Math.max(r,Math.max(g,b));count++; }
            } else if (stack.getItem()==net.minecraft.init.Items.DYE) {
                float[] color=EnumDyeColor.byDyeDamage(stack.getMetadata()).getColorComponentValues();
                int r=(int)(color[0]*255),g=(int)(color[1]*255),b=(int)(color[2]*255);sum[0]+=r;sum[1]+=g;sum[2]+=b;brightness+=Math.max(r,Math.max(g,b));count++;
            } else return ItemStack.EMPTY;
        }
        if (result.isEmpty() || count==0) return ItemStack.EMPTY;
        int r=sum[0]/count,g=sum[1]/count,b=sum[2]/count; float brightest=Math.max(r,Math.max(g,b));float average=(float)brightness/count;
        if (brightest>0) {r=(int)(r*average/brightest);g=(int)(g*average/brightest);b=(int)(b*average/brightest);}
        ((DyeableBackpackItem)result.getItem()).setColor(result,(r<<16)|(g<<8)|b);return result;
    }
    public boolean canFit(int width,int height) { return width*height>=2; }
    public ItemStack getRecipeOutput() { return ItemStack.EMPTY; }
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting grid) { return net.minecraftforge.common.ForgeHooks.defaultRecipeGetRemainingItems(grid); }
    public boolean isDynamic() { return true; }
}
