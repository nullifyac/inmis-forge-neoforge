package draylar.inmis.item;

import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackAugmentType;
import draylar.inmis.augment.BackpackAugments;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.network.ServerNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.util.StatCollector;
import java.util.List;

public class BackpackItem extends Item {
    private final BackpackInfo tier;
    public BackpackItem(BackpackInfo tier){this.tier=tier;setMaxStackSize(1);}
    public BackpackInfo getTier(){return tier;}
    @Override public ItemStack onItemRightClick(ItemStack stack,World world,EntityPlayer player){
        if(!world.isRemote&&!Inmis.CONFIG.requireArmorTrinketToOpen)ServerNetworking.deferHeldOpening(player,stack);return stack;
    }
    public void openBackpack(EntityPlayer player,ItemStack stack){ServerNetworking.openBackpack(player,stack);}
    @Override public boolean isValidArmor(ItemStack stack,int type,Entity entity){return type==1&&Inmis.CONFIG.allowBackpacksInChestplate;}
    @Override public void addInformation(ItemStack stack,EntityPlayer player,List tooltip,boolean advanced){
        for(BackpackAugmentType type:BackpackAugments.getUnlocked(tier))tooltip.add(StatCollector.translateToLocal("augment.backpacked."+type.id()));
    }
    @Override public boolean hasCustomEntity(ItemStack stack){return true;}
    @Override public Entity createEntity(World world,Entity location,ItemStack stack){return new BackpackEntityItem(world,location,stack);}
}
