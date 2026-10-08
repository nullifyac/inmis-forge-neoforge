package draylar.inmis.augment;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;

/** Invoked only at the owning inventory's native death-drop call. */
public final class BackpackDeathBridge {
    private BackpackDeathBridge(){ }
    public static EntityItem dropVanilla(EntityPlayer player,ItemStack stack,boolean random,boolean retainOwnership){
        boolean armor=false;for(ItemStack worn:player.inventory.armorInventory)if(worn==stack){armor=true;break;}
        if(stack!=null&&stack.getItem() instanceof BackpackItem&&(armor?Inmis.CONFIG.spillArmorBackpacksOnDeath:Inmis.CONFIG.spillMainBackpacksOnDeath)){
            for(ItemStack contents:Inmis.getBackpackContents(stack))player.func_146097_a(contents.copy(),random,retainOwnership);
            Inmis.wipeBackpack(stack);
        }
        return player.func_146097_a(stack,random,retainOwnership);
    }
    public static boolean addBaublesDrop(ArrayList drops,Object candidate){
        if(candidate instanceof EntityItem&&Inmis.CONFIG.enableTrinketCompatibility&&Inmis.CONFIG.spillArmorBackpacksOnDeath)BackpackAugmentHandler.spillSelectedDrop(drops,(EntityItem)candidate);
        return drops.add(candidate);
    }
    public static boolean canTakeArmor(net.minecraft.inventory.Slot slot,EntityPlayer player){
        ItemStack stack=slot.getStack();if(stack==null||!(stack.getItem() instanceof BackpackItem)||Inmis.CONFIG==null)return true;
        if(player.openContainer instanceof draylar.inmis.ui.BackpackScreenHandler&&((draylar.inmis.ui.BackpackScreenHandler)player.openContainer).getBackpackStack()==stack)return false;
        return !Inmis.CONFIG.requireEmptyForUnequip||Inmis.getBackpackContents(stack).isEmpty();
    }
}
