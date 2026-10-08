package draylar.inmis.recipe;

import cpw.mods.fml.common.registry.GameRegistry;
import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.DyeableBackpackItem;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;

/** Legacy native crafting, preserving saved contents and settings on upgrades. */
public final class BackpackUpgradeRecipe implements IRecipe {
    private final Item previous;private final ItemStack[] ingredients;private final Item output;
    private BackpackUpgradeRecipe(Item previous,ItemStack[] ingredients,Item output){this.previous=previous;this.ingredients=ingredients;this.output=output;}
    public static void register(){
        net.minecraftforge.oredict.RecipeSorter.register("inmis:upgrade",BackpackUpgradeRecipe.class,net.minecraftforge.oredict.RecipeSorter.Category.SHAPED,"after:minecraft:shaped before:minecraft:shapeless");
        net.minecraftforge.oredict.RecipeSorter.register("inmis:dye",DyeRecipe.class,net.minecraftforge.oredict.RecipeSorter.Category.SHAPELESS,"after:minecraft:shapeless");
        BackpackItem baby=find("baby"),frayed=find("frayed");
        if(baby!=null)GameRegistry.addRecipe(new ItemStack(baby)," L ","LCL"," L ",'L',Items.leather,'C',Blocks.chest);
        if(frayed!=null)GameRegistry.addRecipe(new ItemStack(frayed),"LLL","LCL","LLL",'L',Items.leather,'C',Blocks.chest);
        addRing("frayed","plated",new ItemStack(Items.iron_ingot),new ItemStack(Items.iron_ingot));
        addRing("plated","gilded",new ItemStack(Items.gold_ingot),new ItemStack(Items.gold_ingot));
        addRing("gilded","bejeweled",new ItemStack(Items.diamond),new ItemStack(Items.emerald));
        // These Minecraft versions predate Netherite, magma blocks, and dragon heads.
        addRing("bejeweled","blazing",new ItemStack(Items.diamond),new ItemStack(Items.magma_cream));
        addTop("bejeweled","withered",new ItemStack(Blocks.soul_sand),new ItemStack(Items.nether_star));
        addTop("withered","endless",new ItemStack(Blocks.end_stone),new ItemStack(Items.ender_eye));
        GameRegistry.addRecipe(new ItemStack(Inmis.ENDER_POUCH),"LLL","LCL","LLL",'L',Items.leather,'C',Blocks.ender_chest);
        GameRegistry.addRecipe(new DyeRecipe());
    }
    private static BackpackItem find(String name){for(BackpackItem item:Inmis.BACKPACK_ITEMS)if(item.getTier().getName().equals(name))return item;return null;}
    private static void addRing(String from,String to,ItemStack corners,ItemStack edges){ItemStack[] ingredients=new ItemStack[9];for(int i=0;i<9;i++)if(i!=4)ingredients[i]=(i%2==0?corners:edges);add(from,to,ingredients);}
    private static void addTop(String from,String to,ItemStack outer,ItemStack top){ItemStack[] ingredients=new ItemStack[9];for(int i=0;i<9;i++)if(i!=4)ingredients[i]=i==1?top:outer;add(from,to,ingredients);}
    private static void add(String from,String to,ItemStack[] ingredients){BackpackItem before=find(from),after=find(to);if(before!=null&&after!=null)GameRegistry.addRecipe(new BackpackUpgradeRecipe(before,ingredients,after));}
    public boolean matches(InventoryCrafting inventory,World world){
        if(inventory.getSizeInventory()!=9)return false;
        for(int i=0;i<9;i++){ItemStack stack=inventory.getStackInSlot(i);if(stack==null)return false;
            if(i==4){if(stack.getItem()!=previous||stack.stackSize!=1)return false;}else if(!stack.isItemEqual(ingredients[i]))return false;}
        return true;
    }
    public ItemStack getCraftingResult(InventoryCrafting inventory){ItemStack result=new ItemStack(output);ItemStack before=inventory.getStackInSlot(4);if(before!=null&&before.hasTagCompound())result.setTagCompound((net.minecraft.nbt.NBTTagCompound)before.getTagCompound().copy());return result;}
    public int getRecipeSize(){return 9;}
    public ItemStack getRecipeOutput(){return new ItemStack(output);}
    private static final class DyeRecipe implements IRecipe {
        public boolean matches(InventoryCrafting inventory,World world){int bags=0,dyes=0;for(int i=0;i<inventory.getSizeInventory();i++){ItemStack stack=inventory.getStackInSlot(i);if(stack==null)continue;
            if(stack.getItem() instanceof DyeableBackpackItem&&((BackpackItem)stack.getItem()).getTier().isDyeable())bags++;else if(stack.getItem()==Items.dye)dyes++;else return false;}return bags==1&&dyes>0;}
        public ItemStack getCraftingResult(InventoryCrafting inventory){ItemStack bag=null;int red=0,green=0,blue=0,count=0;
            for(int i=0;i<inventory.getSizeInventory();i++){ItemStack stack=inventory.getStackInSlot(i);if(stack==null)continue;
                int color;if(stack.getItem() instanceof DyeableBackpackItem){bag=stack.copy();color=((DyeableBackpackItem)stack.getItem()).getColor(stack);}else color=net.minecraft.item.ItemDye.field_150922_c[stack.getItemDamage()];
                red+=(color>>16)&255;green+=(color>>8)&255;blue+=color&255;count++;}
            if(bag==null||count==0)return null;bag.stackSize=1;((DyeableBackpackItem)bag.getItem()).setColor(bag,((red/count)<<16)|((green/count)<<8)|(blue/count));return bag;
        }
        public int getRecipeSize(){return 10;}public ItemStack getRecipeOutput(){return null;}
    }
}
