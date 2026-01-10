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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
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

    public static final boolean CURIOS_LOADED = ModList.get().isLoaded("curios");
    public static final CreativeModeTab GROUP = CreativeModeTab.TAB_MISC;
    public static InmisConfig CONFIG;

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);

    public static final RegistryObject<net.minecraft.world.inventory.MenuType<BackpackScreenHandler>> CONTAINER_TYPE =
            MENUS.register("backpack", () -> IForgeMenuType.create(BackpackScreenHandler::new));

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
                    ITEMS.register(backpack.getName().toLowerCase() + "_backpack", () -> createBackpackItem(backpack));
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
        public static void commonSetup(FMLCommonSetupEvent event) {
            if (CURIOS_LOADED && CONFIG.enableTrinketCompatibility) {
                event.enqueueWork(() -> draylar.inmis.compat.CuriosCompat.registerCurios());
            }
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID)
    public static class ForgeEvents {

        @SubscribeEvent
        public static void onLivingDrops(LivingDropsEvent event) {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }

            if (player.level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                return;
            }

            if (CONFIG.spillArmorBackpacksOnDeath) {
                spillInventory(player, player.getInventory().armor, event);
                if (CURIOS_LOADED && CONFIG.enableTrinketCompatibility) {
                    draylar.inmis.compat.CuriosCompat.spillCurios(player, event);
                }
            }

            if (CONFIG.spillMainBackpacksOnDeath) {
                spillInventory(player, player.getInventory().items, event);
                spillInventory(player, player.getInventory().offhand, event);
            }
        }

        private static void spillInventory(Player player, List<ItemStack> items, LivingDropsEvent event) {
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack.getItem() instanceof BackpackItem) {
                    ItemStack original = stack.copy();
                    event.getDrops().removeIf(drop -> ItemStack.isSameItemSameTags(drop.getItem(), original));

                    for (ItemStack contents : Inmis.getBackpackContents(stack)) {
                        if (!contents.isEmpty()) {
                            event.getDrops().add(new ItemEntity(player.level, player.getX(), player.getY(), player.getZ(), contents));
                        }
                    }

                    Inmis.wipeBackpack(stack);
                    event.getDrops().add(new ItemEntity(player.level, player.getX(), player.getY(), player.getZ(), stack.copy()));
                    items.set(i, ItemStack.EMPTY);
                }
            }
        }

        @SubscribeEvent
        public static void registerCommands(RegisterCommandsEvent event) {
            BackpackedConversionCommand.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            draylar.inmis.compat.BackpackedMigrationManager.onPlayerLogin(event);
        }
    }

    public static boolean isBackpackEmpty(ItemStack stack) {
        ListTag tag = getOrCreateInventory(stack);

        for (Tag element : tag) {
            CompoundTag stackTag = (CompoundTag) element;
            ItemStack backpackStack = ItemStack.of(stackTag.getCompound("Stack"));
            if (!backpackStack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public static List<ItemStack> getBackpackContents(ItemStack stack) {
        List<ItemStack> stacks = new ArrayList<>();
        ListTag tag = getOrCreateInventory(stack);

        for (Tag element : tag) {
            CompoundTag stackTag = (CompoundTag) element;
            ItemStack backpackStack = ItemStack.of(stackTag.getCompound("Stack"));
            stacks.add(backpackStack);
        }

        return stacks;
    }

    public static void wipeBackpack(ItemStack stack) {
        stack.getOrCreateTag().remove("Inventory");
    }

    public static ListTag getOrCreateInventory(ItemStack stack) {
        if (stack.getItem() instanceof BackpackItem backpackItem) {
            return getOrCreateInventory(stack, backpackItem.getTier());
        }

        return stack.getOrCreateTag().getList("Inventory", Tag.TAG_COMPOUND);
    }

    public static ListTag getOrCreateInventory(ItemStack stack, BackpackInfo tier) {
        if (tier != null && BackpackedImportController.isImportEnabled()) {
            ListTag imported = BackpackedDataImporter.tryImport(stack, tier);
            if (imported != null) {
                stack.getOrCreateTag().put("Inventory", imported);
            }
        }

        return stack.getOrCreateTag().getList("Inventory", Tag.TAG_COMPOUND);
    }

    public static BackpackAugmentsComponent getOrCreateAugments(ItemStack stack, BackpackInfo tier) {
        CompoundTag tag = stack.getOrCreateTag();
        BackpackAugmentsComponent component = tag.contains(AUGMENTS_KEY, Tag.TAG_COMPOUND)
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
