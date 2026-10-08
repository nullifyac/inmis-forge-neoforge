package draylar.inmis.item;

import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackAugments;
import draylar.inmis.augment.BackpackAugmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/** Applies dropped-item fire protection without modifying unrelated item entities. */
public final class BackpackEntityItem extends EntityItem {
    public BackpackEntityItem(World world){super(world);}
    public BackpackEntityItem(World world,Entity old,ItemStack stack){
        super(world,old.posX,old.posY,old.posZ,stack);motionX=old.motionX;motionY=old.motionY;motionZ=old.motionZ;
        if(old instanceof EntityItem){
            net.minecraft.nbt.NBTTagCompound saved=new net.minecraft.nbt.NBTTagCompound();old.writeToNBT(saved);readFromNBT(saved);setEntityItemStack(stack);
            delayBeforeCanPickup=((EntityItem)old).delayBeforeCanPickup;age=((EntityItem)old).age;
        }
    }
    @Override public boolean attackEntityFrom(DamageSource source,float amount){
        ItemStack stack=getEntityItem();
        if(stack!=null&&stack.getItem() instanceof BackpackItem){BackpackItem item=(BackpackItem)stack.getItem();
            if(source.isFireDamage()&&(item.getTier().isFireImmune()||(BackpackAugments.isUnlocked(item.getTier(),BackpackAugmentType.IMBUED_HIDE)
                    &&Inmis.getOrCreateAugments(stack,item.getTier()).imbuedHideEnabled())))return false;
        }
        return super.attackEntityFrom(source,amount);
    }
}
