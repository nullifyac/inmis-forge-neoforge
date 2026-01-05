package draylar.inmis;

import draylar.inmis.config.BackpackInfo;
import draylar.inmis.config.ConfigManager;
import draylar.inmis.config.InmisConfig;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.DyeableBackpackItem;
import draylar.inmis.item.EnderBackpackItem;
import draylar.inmis.item.component.BackpackComponent;
import draylar.inmis.network.ServerNetworking;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
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
                    .cacheEncoding()
                    .build());

    public static final List<Supplier<BackpackItem>> BACKPACKS = new ArrayList<>();
    public static final Supplier<Item> ENDER_POUCH = ITEMS.register("ender_pouch", EnderBackpackItem::new);

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

        ITEMS.register(eventBus);
        MENUS.register(eventBus);
        TABS.register(eventBus);
        DATA_COMPONENTS.register(eventBus);

        registerBackpacks();

        eventBus.addListener(this::commonSetup);
        eventBus.addListener(Inmis::buildCreativeTab);
        eventBus.addListener(ServerNetworking::registerPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(Inmis::onLivingDrops);
    }

    private void registerBackpacks() {
        InmisConfig defaultConfig = new InmisConfig();

        for (BackpackInfo backpack : CONFIG.backpacks) {
            Item.Properties properties = new Item.Properties().stacksTo(1);

            if (backpack.isFireImmune()) {
                properties.fireResistant();
            }

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

            BackpackItem item = backpack.isDyeable()
                    ? new DyeableBackpackItem(backpack, properties)
                    : new BackpackItem(backpack, properties);
            Supplier<BackpackItem> registered =
                    ITEMS.register(backpack.getName().toLowerCase() + "_backpack", () -> item);
            BACKPACKS.add(registered);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        if (CURIOS_LOADED && CONFIG.enableTrinketCompatibility) {
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

    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
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
                event.getDrops().removeIf(drop -> ItemStack.isSameItemSameComponents(drop.getItem(), original));

                for (ItemStack contents : Inmis.getBackpackContents(stack)) {
                    if (!contents.isEmpty()) {
                        event.getDrops().add(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), contents));
                    }
                }

                Inmis.wipeBackpack(stack);
                event.getDrops().add(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack.copy()));
                items.set(i, ItemStack.EMPTY);
            }
        }
    }

    public static boolean isBackpackEmpty(ItemStack stack) {
        BackpackComponent component = stack.get(BACKPACK_COMPONENT.get());
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
        BackpackComponent component = stack.get(BACKPACK_COMPONENT.get());
        return component != null ? component.stacks() : List.of();
    }

    public static void wipeBackpack(ItemStack stack) {
        int size = 0;
        BackpackComponent component = stack.get(BACKPACK_COMPONENT.get());
        if (component != null) {
            size = component.stacks().size();
        } else if (stack.getItem() instanceof BackpackItem backpackItem) {
            BackpackInfo info = backpackItem.getTier();
            size = info.getRowWidth() * info.getNumberOfRows();
        }

        if (size > 0) {
            List<ItemStack> empty = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                empty.add(ItemStack.EMPTY);
            }
            stack.set(BACKPACK_COMPONENT.get(), new BackpackComponent(empty));
        }
    }

    public static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
    }
}
