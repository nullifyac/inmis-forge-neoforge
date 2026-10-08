package draylar.inmis;

import draylar.inmis.command.BackpackedConversionCommand;
import draylar.inmis.compat.BackpackedDataImporter;
import draylar.inmis.compat.BackpackedImportController;
import draylar.inmis.compat.BackpackedMigrationManager;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.config.ConfigManager;
import draylar.inmis.config.InmisConfig;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.DyeableBackpackItem;
import draylar.inmis.item.EnderBackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.item.component.BackpackComponent;
import draylar.inmis.network.ServerNetworking;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Mod(Inmis.MOD_ID)
public class Inmis {

    public static final String MOD_ID = "inmis";
    public static final Logger LOGGER = LogManager.getLogger();

    public static final boolean CURIOS_LOADED = ModList.get().isLoaded("curios");
    public static InmisConfig CONFIG;

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID);

    public static final Supplier<MenuType<BackpackScreenHandler>> CONTAINER_TYPE =
            MENUS.register("backpack", () -> IMenuTypeExtension.create(BackpackScreenHandler::new));

    public static final Supplier<DataComponentType<BackpackComponent>> BACKPACK_COMPONENT =
            DATA_COMPONENTS.register("backpack", () -> DataComponentType.<BackpackComponent>builder()
                    .persistent(BackpackComponent.CODEC)
                    .networkSynchronized(BackpackComponent.STREAM_CODEC)
                    .build());
    public static final Supplier<DataComponentType<BackpackAugmentsComponent>> BACKPACK_AUGMENTS =
            DATA_COMPONENTS.register("backpack_augments", () -> DataComponentType.<BackpackAugmentsComponent>builder()
                    .persistent(BackpackAugmentsComponent.CODEC)
                    .networkSynchronized(BackpackAugmentsComponent.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    public static final List<Supplier<BackpackItem>> BACKPACKS = new ArrayList<>();
    public static final Supplier<Item> ENDER_POUCH = ITEMS.register("ender_pouch", () -> new EnderBackpackItem(new Item.Properties().stacksTo(1).setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, id("ender_pouch")))));

    public static final Supplier<CreativeModeTab> GROUP = TABS.register("backpack",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.inmis.backpack"))
                    .icon(() -> {
                        if (!BACKPACKS.isEmpty()) {
                            return new ItemStack(BACKPACKS.get(0).get());
                        }
                        return new ItemStack(Items.CHEST);
                    })
                    .build());

    public Inmis(IEventBus eventBus, ModContainer modContainer) {
        CONFIG = ConfigManager.load();
        BackpackedMigrationManager.bootstrapFromConfig();

        ITEMS.register(eventBus);
        MENUS.register(eventBus);
        TABS.register(eventBus);
        DATA_COMPONENTS.register(eventBus);

        registerBackpacks();

        eventBus.addListener(this::commonSetup);
        eventBus.addListener(Inmis::buildCreativeTab);
        eventBus.addListener(ServerNetworking::registerPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(Inmis::registerCommands);
        NeoForge.EVENT_BUS.addListener(draylar.inmis.compat.BackpackedRecipeFilter::blockBackpackedRecipes);
        NeoForge.EVENT_BUS.addListener(draylar.inmis.item.BackpackDyeRecipes::addRecipes);
        NeoForge.EVENT_BUS.addListener(BackpackedMigrationManager::onPlayerLogin);
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

            Supplier<BackpackItem> registered =
                    ITEMS.register(backpack.getName().toLowerCase(java.util.Locale.ROOT) + "_backpack", () -> createBackpackItem(backpack));
            BACKPACKS.add(registered);
        }
    }

    private static BackpackItem createBackpackItem(BackpackInfo backpack) {
        Item.Properties properties = new Item.Properties().stacksTo(1).setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, id(backpack.getName().toLowerCase(java.util.Locale.ROOT) + "_backpack")));
        if (backpack.isFireImmune()) {
            properties.fireResistant();
        }
        return backpack.isDyeable()
                ? new DyeableBackpackItem(backpack, properties)
                : new BackpackItem(backpack, properties);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        BackpackedConversionCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        if (CURIOS_LOADED) {
            event.enqueueWork(draylar.inmis.compat.CuriosCompat::registerCurios);
        }
    }

    public static void buildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == GROUP.get()) {
            for (Supplier<BackpackItem> backpack : BACKPACKS) {
                event.accept(backpack.get());
            }
            event.accept(ENDER_POUCH.get());
        }
    }

    public static void spillVanillaInventoryOnDeath(Player player) {
        if (player.level().isClientSide() || ((net.minecraft.server.level.ServerLevel) player.level()).getGameRules().get(GameRules.KEEP_INVENTORY)) {
            return;
        }
        if (CONFIG.spillArmorBackpacksOnDeath) {
            spillInventoryBeforeDrop(player, draylar.inmis.util.PlayerInventorySections.armor(player.getInventory()));
        }
        if (CONFIG.spillMainBackpacksOnDeath) {
            spillInventoryBeforeDrop(player, player.getInventory().getNonEquipmentItems());
            spillInventoryBeforeDrop(player, draylar.inmis.util.PlayerInventorySections.offhand(player.getInventory()));
        }
    }

    private static void spillInventoryBeforeDrop(Player player, List<ItemStack> items) {
        for (ItemStack stack : items) {
            if (stack.getItem() instanceof BackpackItem) {
                for (ItemStack contents : Inmis.getBackpackContents(stack)) {
                    if (!contents.isEmpty()) {
                        player.drop(contents, true, false);
                    }
                }
                Inmis.wipeBackpack(stack);
                // Leave the empty source backpack for vanilla Inventory.dropAll. Both the bag and
                // its contents then share the normal captured LivingDrops cancellation pipeline.
            }
        }
    }

    public static boolean isBackpackEmpty(ItemStack stack) {
        BackpackComponent component = getOrCreateComponent(stack);
        if (component == null) {
            return true;
        }

        for (ItemStack itemStack : component.stacks()) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static List<ItemStack> getBackpackContents(ItemStack stack) {
        BackpackComponent component = getOrCreateComponent(stack);
        return component != null ? component.stacks() : List.of();
    }

    public static void wipeBackpack(ItemStack stack) {
        BackpackComponent component = getOrCreateComponent(stack);
        if (component == null) {
            return;
        }

        int size = component.stacks().size();
        if (size <= 0) {
            return;
        }

        stack.set(BACKPACK_COMPONENT.get(), new BackpackComponent(createEmptyContents(size)));
    }

    public static BackpackComponent getOrCreateComponent(ItemStack stack) {
        if (stack.getItem() instanceof BackpackItem backpackItem) {
            return getOrCreateComponent(stack, backpackItem.getTier());
        }

        return stack.get(BACKPACK_COMPONENT.get());
    }

    public static BackpackComponent getOrCreateComponent(ItemStack stack, BackpackInfo tier) {
        int size = Math.max(0, tier.getRowWidth() * tier.getNumberOfRows());
        if (size == 0) {
            return stack.get(BACKPACK_COMPONENT.get());
        }

        BackpackComponent component = stack.get(BACKPACK_COMPONENT.get());
        if (component == null && BackpackedImportController.isImportEnabled()) {
            component = BackpackedDataImporter.tryImport(stack, tier);
        }

        BackpackComponent normalized = normalizeComponent(component, size);
        if (normalized != null) {
            stack.set(BACKPACK_COMPONENT.get(), normalized);
        }
        return normalized;
    }

    private static BackpackComponent normalizeComponent(BackpackComponent component, int size) {
        if (size <= 0) {
            return component;
        }

        if (component == null) {
            return new BackpackComponent(createEmptyContents(size));
        }

        List<ItemStack> stacks = component.stacks();
        // Saved overflow remains available through recovery rows after a config capacity reduction.
        if (stacks.size() >= size) {
            return component;
        }

        List<ItemStack> resized = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            resized.add(i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
        }
        return new BackpackComponent(resized);
    }

    private static List<ItemStack> createEmptyContents(int size) {
        List<ItemStack> empty = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            empty.add(ItemStack.EMPTY);
        }
        return empty;
    }

    public static BackpackAugmentsComponent getOrCreateAugments(ItemStack stack, BackpackInfo tier) {
        BackpackAugmentsComponent component = stack.get(BACKPACK_AUGMENTS.get());
        if (component == null) {
            component = BackpackAugmentsComponent.DEFAULT;
            stack.set(BACKPACK_AUGMENTS.get(), component);
        }
        return component;
    }

    public static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(MOD_ID, name);
    }
}
