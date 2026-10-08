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
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeContainerType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mod(Inmis.MOD_ID)
public class Inmis {

    public static final String MOD_ID = "inmis";
    public static final Logger LOGGER = LogManager.getLogger();
    private static final String AUGMENTS_KEY = "Augments";

    // Forge 1.16 loads @Mod classes before ModList's indexed runtime registry exists.
    public static final boolean CURIOS_LOADED = LoadingModList.get().getMods().stream()
            .anyMatch(mod -> mod.getModId().equals("curios"));
    public static final ItemGroup GROUP = ItemGroup.TAB_MISC;
    public static InmisConfig CONFIG;

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<net.minecraft.inventory.container.ContainerType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.CONTAINERS, MOD_ID);

    public static final RegistryObject<net.minecraft.inventory.container.ContainerType<BackpackScreenHandler>> CONTAINER_TYPE =
            MENUS.register("backpack", () -> IForgeContainerType.create(BackpackScreenHandler::new));

    public static final List<RegistryObject<BackpackItem>> BACKPACKS = new ArrayList<>();
    public static final RegistryObject<Item> ENDER_POUCH = ITEMS.register("ender_pouch", EnderBackpackItem::new);

    public Inmis() {
        CONFIG = ConfigManager.load();
        draylar.inmis.compat.BackpackedMigrationManager.bootstrapFromConfig();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        MENUS.register(modBus);

        registerBackpacks();
        ServerNetworking.init();
    }

    private void registerBackpacks() {
        InmisConfig defaultConfig = new InmisConfig();

        for (BackpackInfo backpack : CONFIG.backpacks) {
            if (backpack.getOpenSound() == null) {
                Optional<BackpackInfo> any = defaultConfig.backpacks.stream()
                        .filter(info -> info.getName().equals(backpack.getName()))
                        .findAny();
                any.ifPresent(backpackInfo -> backpack.setOpenSound(backpackInfo.getOpenSound()));

                if (backpack.getOpenSound() == null) {
                    LOGGER.info(String.format("Could not find a sound event for %s in inmis.json config.", backpack.getName()));
                    LOGGER.info("Consider regenerating your config, or assigning the openSound value. Rolling with defaults for now.");
                    backpack.setOpenSound("minecraft:item.armor.equip_leather");
                }
            }

            RegistryObject<BackpackItem> registered =
                    ITEMS.register(backpack.getName().toLowerCase(java.util.Locale.ROOT) + "_backpack", () -> createBackpackItem(backpack));
            BACKPACKS.add(registered);
        }
    }

    private static BackpackItem createBackpackItem(BackpackInfo backpack) {
        Item.Properties properties = new Item.Properties().stacksTo(1).tab(GROUP);
        if (backpack.isFireImmune()) {
            properties.fireResistant();
        }
        return backpack.isDyeable()
                ? new DyeableBackpackItem(backpack, properties)
                : new BackpackItem(backpack, properties);
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void enqueueInterMod(net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent event) {
            if (CURIOS_LOADED) {
                draylar.inmis.compat.CuriosCompat.registerBackSlot();
            }
        }

        @SubscribeEvent
        public static void commonSetup(FMLCommonSetupEvent event) {
            if (CURIOS_LOADED) {
                event.enqueueWork(() -> draylar.inmis.compat.CuriosCompat.registerCurios());
            }
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID)
    public static class ForgeEvents {

        @SubscribeEvent
        public static void registerCommands(RegisterCommandsEvent event) {
            BackpackedConversionCommand.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            draylar.inmis.compat.BackpackedMigrationManager.onPlayerLogin(event);
        }
    }

    public static void spillVanillaInventoryOnDeath(PlayerEntity player) {
        if (CONFIG.spillArmorBackpacksOnDeath) {
            spillInventoryContents(player, player.inventory.armor);
        }
        if (CONFIG.spillMainBackpacksOnDeath) {
            spillInventoryContents(player, player.inventory.items);
            spillInventoryContents(player, player.inventory.offhand);
        }
    }

    private static void spillInventoryContents(PlayerEntity player, List<ItemStack> items) {
        for (ItemStack stack : items) {
            if (stack.getItem() instanceof BackpackItem) {
                for (ItemStack contents : getBackpackContents(stack)) {
                    if (!contents.isEmpty()) {
                        // Use vanilla's death-drop path so Forge captures and cancels these drops together.
                        player.drop(contents, true, false);
                    }
                }
                wipeBackpack(stack);
                // PlayerInventory.dropAll will drop this exact, now-empty backpack once.
            }
        }
    }

    public static boolean isBackpackEmpty(ItemStack stack) {
        ListNBT tag = getOrCreateInventory(stack);

        for (INBT element : tag) {
            CompoundNBT stackTag = (CompoundNBT) element;
            ItemStack backpackStack = ItemStack.of(stackTag.getCompound("Stack"));
            if (!backpackStack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public static List<ItemStack> getBackpackContents(ItemStack stack) {
        List<ItemStack> stacks = new ArrayList<>();
        ListNBT tag = getOrCreateInventory(stack);

        for (INBT element : tag) {
            CompoundNBT stackTag = (CompoundNBT) element;
            ItemStack backpackStack = ItemStack.of(stackTag.getCompound("Stack"));
            stacks.add(backpackStack);
        }

        return stacks;
    }

    public static void wipeBackpack(ItemStack stack) {
        stack.getOrCreateTag().remove("Inventory");
    }

    public static ListNBT getOrCreateInventory(ItemStack stack) {
        if (stack.getItem() instanceof BackpackItem) {
            BackpackItem backpackItem = (BackpackItem) stack.getItem();
            return getOrCreateInventory(stack, backpackItem.getTier());
        }

        return stack.getOrCreateTag().getList("Inventory", 10);
    }

    public static ListNBT getOrCreateInventory(ItemStack stack, BackpackInfo tier) {
        if (tier != null && BackpackedImportController.isImportEnabled()) {
            ListNBT imported = BackpackedDataImporter.tryImport(stack, tier);
            if (imported != null) {
                stack.getOrCreateTag().put("Inventory", imported);
            }
        }

        return stack.getOrCreateTag().getList("Inventory", 10);
    }

    public static BackpackAugmentsComponent getOrCreateAugments(ItemStack stack, BackpackInfo tier) {
        CompoundNBT tag = stack.getOrCreateTag();
        BackpackAugmentsComponent component = tag.contains(AUGMENTS_KEY, 10)
                ? BackpackAugmentsComponent.fromTag(tag.getCompound(AUGMENTS_KEY))
                : BackpackAugmentsComponent.DEFAULT;
        tag.put(AUGMENTS_KEY, component.toTag());
        return component;
    }

    public static void setBackpackAugments(ItemStack stack, BackpackAugmentsComponent augments) {
        if (stack.isEmpty()) {
            return;
        }
        stack.getOrCreateTag().put(AUGMENTS_KEY, augments.toTag());
    }

    public static ResourceLocation id(String name) {
        return new ResourceLocation(MOD_ID, name);
    }
}
