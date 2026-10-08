package draylar.inmis.augment;

import draylar.inmis.Inmis;
import draylar.inmis.compat.BaublesCompat;
import draylar.inmis.item.BackpackItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

public final class BackpackAugmentHelper {
    private BackpackAugmentHelper(){ }
    public static List<ItemStack> getBackpackStacks(EntityPlayer player){
        List<ItemStack> result=new ArrayList<>();collect(player.inventory.mainInventory,result);collect(player.inventory.armorInventory,result);
        if(Inmis.CONFIG.enableTrinketCompatibility)for(ItemStack stack:BaublesCompat.getEquippedBackpacks(player))if(!result.contains(stack))result.add(stack);
        return result;
    }
    private static void collect(ItemStack[] items,List<ItemStack> result){for(ItemStack stack:items)if(stack!=null&&stack.stackSize>0&&stack.getItem() instanceof BackpackItem)result.add(stack);}
}
