package draylar.inmis;

import draylar.inmis.command.BackpackedConversionCommand;
import draylar.inmis.compat.BackpackedDataImporter;
import draylar.inmis.compat.BackpackedImportController;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.config.ConfigManager;
import draylar.inmis.config.InmisConfig;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.DyeableBackpackItem;
import draylar.inmis.item.EnderBackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.network.ServerNetworking;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mod(modid = Inmis.MOD_ID, name = "Inmis", version = "2.6.0-2.9.4-1.12.2", acceptedMinecraftVersions = "[1.12.2]", dependencies = "after:baubles")
public class Inmis {

    public static final String MOD_ID = "inmis";
    public static final Logger LOGGER = LogManager.getLogger();
    private static final String AUGMENTS_KEY = "Augments";

    public static boolean BAUBLES_LOADED;
    public static final net.minecraft.creativetab.CreativeTabs GROUP = net.minecraft.creativetab.CreativeTabs.MISC;
    public static InmisConfig CONFIG;
    public static final List<LegacyRegistryEntry<BackpackItem>> BACKPACKS = new ArrayList<>();
    public static final LegacyRegistryEntry<Item> ENDER_POUCH = new LegacyRegistryEntry<>();
    @net.minecraftforge.fml.common.Mod.Instance(MOD_ID) public static Inmis INSTANCE;
    @net.minecraftforge.fml.common.SidedProxy(clientSide = "draylar.inmis.InmisClient", serverSide = "draylar.inmis.CommonProxy")
    public static CommonProxy PROXY;

    @net.minecraftforge.fml.common.Mod.EventHandler
    public void preInit(net.minecraftforge.fml.common.event.FMLPreInitializationEvent event) {
        BAUBLES_LOADED = net.minecraftforge.fml.common.Loader.isModLoaded("baubles");
        CONFIG = ConfigManager.load(event.getModConfigurationDirectory().toPath());
        draylar.inmis.compat.BackpackedMigrationManager.bootstrapFromConfig();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new draylar.inmis.augment.BackpackAugmentEvents());
        ServerNetworking.init();
        if (BAUBLES_LOADED) draylar.inmis.compat.BaublesCompat.register();
        net.minecraftforge.fml.common.network.NetworkRegistry.INSTANCE.registerGuiHandler(this, PROXY);
        PROXY.preInit();
    }
    @net.minecraftforge.fml.common.Mod.EventHandler
    public void init(net.minecraftforge.fml.common.event.FMLInitializationEvent event) { PROXY.init(); }
    @net.minecraftforge.fml.common.Mod.EventHandler
    public void serverStarting(net.minecraftforge.fml.common.event.FMLServerStartingEvent event) {
        event.registerServerCommand(new BackpackedConversionCommand());
    }
    @Mod.EventBusSubscriber(modid = MOD_ID)
    public static class Registration {
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public static void items(net.minecraftforge.event.RegistryEvent.Register<Item> event) {
            ENDER_POUCH.set(new EnderBackpackItem().setRegistryName(id("ender_pouch")).setUnlocalizedName("inmis.ender_pouch"));
            event.getRegistry().register(ENDER_POUCH.get());
            for (BackpackInfo tier : CONFIG.backpacks) {
                BackpackItem item = tier.isDyeable() ? new DyeableBackpackItem(tier) : new BackpackItem(tier);
                item.setRegistryName(id(tier.getName().toLowerCase(java.util.Locale.ROOT) + "_backpack"));
                item.setUnlocalizedName("inmis." + tier.getName().toLowerCase(java.util.Locale.ROOT) + "_backpack");
                event.getRegistry().register(item);
                BACKPACKS.add(new LegacyRegistryEntry<>(item));
            }
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new LoginEvents());
        }
    }
    @Mod.EventBusSubscriber(modid = MOD_ID)
    public static class Recipes {
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public static void register(net.minecraftforge.event.RegistryEvent.Register<net.minecraft.item.crafting.IRecipe> event) {
            event.getRegistry().register(new draylar.inmis.item.BackpackDyeRecipe().setRegistryName(id("backpack_dye")));
        }
    }
    public static final class LoginEvents {
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public void login(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
            draylar.inmis.compat.BackpackedMigrationManager.onPlayerLogin(event);
        }
    }
    public static NBTTagCompound tag(ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        return stack.getTagCompound();
    }
    public static java.util.List<NBTBase> elements(NBTTagList list) {
        java.util.List<NBTBase> result = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) result.add(list.get(i));
        return result;
    }
    public static void spillVanillaInventoryOnDeath(EntityPlayer player) {
        if (CONFIG.spillArmorBackpacksOnDeath) {
            spillInventoryContents(player, player.inventory.armorInventory);
        }
        if (CONFIG.spillMainBackpacksOnDeath) {
            spillInventoryContents(player, player.inventory.mainInventory);
            spillInventoryContents(player, player.inventory.offHandInventory);
        }
    }

    private static void spillInventoryContents(EntityPlayer player, List<ItemStack> items) {
        for (ItemStack stack : items) {
            if (stack.getItem() instanceof BackpackItem) {
                for (ItemStack contents : getBackpackContents(stack)) {
                    if (!contents.isEmpty()) {
                        // Use vanilla's death-drop path so Forge captures and cancels these drops together.
                        player.dropItem(contents, true, false);
                    }
                }
                wipeBackpack(stack);
                // InventoryPlayer.dropAll will drop this exact, now-empty backpack once.
            }
        }
    }

    public static boolean isBackpackEmpty(ItemStack stack) {
        NBTTagList tag = getOrCreateInventory(stack);

        for (NBTBase element : Inmis.elements(tag)) {
            NBTTagCompound stackTag = (NBTTagCompound) element;
            ItemStack backpackStack = new ItemStack(stackTag.getCompoundTag("Stack"));
            if (!backpackStack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public static List<ItemStack> getBackpackContents(ItemStack stack) {
        List<ItemStack> stacks = new ArrayList<>();
        NBTTagList tag = getOrCreateInventory(stack);

        for (NBTBase element : Inmis.elements(tag)) {
            NBTTagCompound stackTag = (NBTTagCompound) element;
            ItemStack backpackStack = new ItemStack(stackTag.getCompoundTag("Stack"));
            stacks.add(backpackStack);
        }

        return stacks;
    }

    public static void wipeBackpack(ItemStack stack) {
        Inmis.tag(stack).removeTag("Inventory");
    }

    public static NBTTagList getOrCreateInventory(ItemStack stack) {
        if (stack.getItem() instanceof BackpackItem) {
            BackpackItem backpackItem = (BackpackItem) stack.getItem();
            return getOrCreateInventory(stack, backpackItem.getTier());
        }

        return Inmis.tag(stack).getTagList("Inventory", 10);
    }

    public static NBTTagList getOrCreateInventory(ItemStack stack, BackpackInfo tier) {
        if (tier != null && BackpackedImportController.isImportEnabled()) {
            NBTTagList imported = BackpackedDataImporter.tryImport(stack, tier);
            if (imported != null) {
                Inmis.tag(stack).setTag("Inventory", imported);
            }
        }

        return Inmis.tag(stack).getTagList("Inventory", 10);
    }

    public static BackpackAugmentsComponent getOrCreateAugments(ItemStack stack, BackpackInfo tier) {
        NBTTagCompound tag = Inmis.tag(stack);
        BackpackAugmentsComponent component = tag.hasKey(AUGMENTS_KEY, 10)
                ? BackpackAugmentsComponent.fromTag(tag.getCompoundTag(AUGMENTS_KEY))
                : BackpackAugmentsComponent.DEFAULT;
        tag.setTag(AUGMENTS_KEY, component.toTag());
        return component;
    }

    public static void setBackpackAugments(ItemStack stack, BackpackAugmentsComponent augments) {
        if (stack.isEmpty()) {
            return;
        }
        Inmis.tag(stack).setTag(AUGMENTS_KEY, augments.toTag());
    }

    public static ResourceLocation id(String name) {
        return new ResourceLocation(MOD_ID, name);
    }
}
