package draylar.inmis;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import draylar.inmis.augment.BackpackAugmentEvents;
import draylar.inmis.augment.BackpackAugmentHandler;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.config.ConfigManager;
import draylar.inmis.config.InmisConfig;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.DyeableBackpackItem;
import draylar.inmis.item.EnderBackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.network.ServerNetworking;
import draylar.inmis.recipe.BackpackUpgradeRecipe;
import draylar.inmis.compat.BaublesCompat;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mod(modid=Inmis.MOD_ID,name="Inmis",version=Tags.VERSION,acceptedMinecraftVersions="[1.7.10]",dependencies="after:Baubles")
public final class Inmis {
    public static final String MOD_ID="inmis";
    public static final Logger LOGGER=LogManager.getLogger(MOD_ID);
    @Mod.Instance(MOD_ID) public static Inmis INSTANCE;
    @SidedProxy(clientSide="draylar.inmis.InmisClient",serverSide="draylar.inmis.CommonProxy") public static CommonProxy PROXY;
    public static InmisConfig CONFIG;
    public static final List<BackpackItem> BACKPACK_ITEMS=new ArrayList<>();
    public static EnderBackpackItem ENDER_POUCH;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        CONFIG=ConfigManager.load(event.getModConfigurationDirectory());
        for(BackpackInfo tier:CONFIG.backpacks) {
            if(tier.getOpenSound()==null)tier.setOpenSound("random.chestopen");
            BackpackItem item=BaublesCompat.createItem(tier);
            String name=tier.getName().toLowerCase(Locale.ROOT)+"_backpack";
            item.setUnlocalizedName("inmis."+name).setTextureName("inmis:"+name).setCreativeTab(CreativeTabs.tabMisc);
            GameRegistry.registerItem(item,name);BACKPACK_ITEMS.add(item);
        }
        ENDER_POUCH=new EnderBackpackItem();
        GameRegistry.registerItem(ENDER_POUCH,"ender_pouch");
        cpw.mods.fml.common.registry.EntityRegistry.registerModEntity(draylar.inmis.item.BackpackEntityItem.class,"backpack_item",0,this,32,10,true);
        ServerNetworking.init();
        NetworkRegistry.INSTANCE.registerGuiHandler(this,PROXY);
        BackpackAugmentEvents events=new BackpackAugmentEvents();
        MinecraftForge.EVENT_BUS.register(events);FMLCommonHandler.instance().bus().register(events);
        PROXY.preInit();
    }
    @Mod.EventHandler public void init(FMLInitializationEvent event) { BackpackUpgradeRecipe.register();PROXY.init(); }
    public static ResourceLocation id(String path){return new ResourceLocation(MOD_ID,path);}
    public static NBTTagCompound tag(ItemStack stack){if(!stack.hasTagCompound())stack.setTagCompound(new NBTTagCompound());return stack.getTagCompound();}
    public static NBTTagList getOrCreateInventory(ItemStack stack,BackpackInfo tier){
        NBTTagCompound tag=tag(stack);if(!tag.hasKey("Inventory",9))tag.setTag("Inventory",new NBTTagList());return tag.getTagList("Inventory",10);
    }
    public static BackpackAugmentsComponent getOrCreateAugments(ItemStack stack,BackpackInfo tier){
        NBTTagCompound tag=tag(stack);return tag.hasKey("Augments",10)?BackpackAugmentsComponent.fromTag(tag.getCompoundTag("Augments")):BackpackAugmentsComponent.DEFAULT;
    }
    public static void setAugments(ItemStack stack,BackpackInfo tier,BackpackAugmentsComponent component){tag(stack).setTag("Augments",component.toTag());}
    public static void wipeBackpack(ItemStack stack){if(stack.hasTagCompound())stack.getTagCompound().removeTag("Inventory");}
    public static List<ItemStack> getBackpackContents(ItemStack stack){
        List<ItemStack> items=new ArrayList<>();if(stack==null||!(stack.getItem() instanceof BackpackItem))return items;
        NBTTagList contents=getOrCreateInventory(stack,((BackpackItem)stack.getItem()).getTier());
        for(int i=0;i<contents.tagCount();i++){ItemStack item=ItemStack.loadItemStackFromNBT(contents.getCompoundTagAt(i).getCompoundTag("Stack"));if(item!=null&&item.stackSize>0)items.add(item);}
        return items;
    }
}
