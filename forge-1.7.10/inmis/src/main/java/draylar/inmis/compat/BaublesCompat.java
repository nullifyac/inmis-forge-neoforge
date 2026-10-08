package draylar.inmis.compat;

import cpw.mods.fml.common.Loader;
import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.DyeableBackpackItem;
import draylar.inmis.util.InventoryUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Original Baubles exposes no back/body slot; backpacks use its amulet slot. */
public final class BaublesCompat {
    private BaublesCompat(){ }
    public static boolean isLoaded(){return Loader.isModLoaded("Baubles");}
    public static BackpackItem createItem(BackpackInfo tier){
        if(isLoaded())return Integration.createItem(tier);
        return tier.isDyeable()?new DyeableBackpackItem(tier):new BackpackItem(tier);
    }
    public static List<ItemStack> getEquippedBackpacks(EntityPlayer player){return isLoaded()?Integration.getEquipped(player):java.util.Collections.<ItemStack>emptyList();}
    public static ItemStack findFirstEquippedBackpack(EntityPlayer player){List<ItemStack> stacks=getEquippedBackpacks(player);return stacks.isEmpty()?null:stacks.get(0);}
    private static final class Integration {
        static BackpackItem createItem(BackpackInfo tier){return tier.isDyeable()?new BaublesDyeableBackpackItem(tier):new BaublesBackpackItem(tier);}
        static List<ItemStack> getEquipped(EntityPlayer player){
            List<ItemStack> stacks=new ArrayList<>();net.minecraft.inventory.IInventory inventory=baubles.api.BaublesApi.getBaubles(player);
            if(inventory!=null)for(int i=0;i<inventory.getSizeInventory();i++){ItemStack stack=inventory.getStackInSlot(i);if(!InventoryUtils.isEmpty(stack)&&stack.getItem() instanceof BackpackItem)stacks.add(stack);}
            return stacks;
        }
    }
    static boolean canEquip(){return Inmis.CONFIG.enableTrinketCompatibility;}
    static boolean canUnequip(ItemStack stack,EntityPlayer player){
        if(player.openContainer instanceof draylar.inmis.ui.BackpackScreenHandler
                &&((draylar.inmis.ui.BackpackScreenHandler)player.openContainer).getBackpackStack()==stack)return false;
        return !Inmis.CONFIG.enableTrinketCompatibility||!Inmis.CONFIG.requireEmptyForUnequip||!Inmis.tag(stack).hasKey("Inventory",9)||Inmis.tag(stack).getTagList("Inventory",10).tagCount()==0;
    }
}
