package draylar.inmis;

import draylar.inmis.config.BackpackInfo;
import draylar.inmis.config.ConfigManager;
import draylar.inmis.config.InmisConfig;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.DyeableBackpackItem;
import draylar.inmis.item.EnderBackpackItem;
import draylar.inmis.network.ServerNetworking;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
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

    public static final boolean CURIOS_LOADED = ModList.get().isLoaded("curios");
    public static InmisConfig CONFIG;

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);

    public static final RegistryObject<net.minecraft.world.inventory.MenuType<BackpackScreenHandler>> CONTAINER_TYPE =
            MENUS.register("backpack", () -> IForgeMenuType.create(BackpackScreenHandler::new));

    public static final List<RegistryObject<BackpackItem>> BACKPACKS = new ArrayList<>();
    public static final RegistryObject<Item> ENDER_POUCH = ITEMS.register("ender_pouch", EnderBackpackItem::new);

    public static final RegistryObject<CreativeModeTab> GROUP = TABS.register("backpack",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.inmis.backpack"))
                    .icon(() -> {
                        if (!BACKPACKS.isEmpty()) {
                            return new ItemStack(BACKPACKS.get(0).get());
                        }
                        return new ItemStack(Items.CHEST);
                    })
                    .build());

    public Inmis() {
        CONFIG = ConfigManager.load();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);

        registerBackpacks();
        ServerNetworking.init();
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
            RegistryObject<BackpackItem> registered =
                    ITEMS.register(backpack.getName().toLowerCase() + "_backpack", () -> item);
            BACKPACKS.add(registered);
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void buildCreativeTab(BuildCreativeModeTabContentsEvent event) {
            if (event.getTab() == GROUP.get()) {
                for (RegistryObject<BackpackItem> backpack : BACKPACKS) {
                    event.accept(backpack.get());
                }
                event.accept(ENDER_POUCH.get());
            }
        }

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
                    event.getDrops().removeIf(drop -> ItemStack.isSameItemSameTags(drop.getItem(), original));

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
    }

    public static boolean isBackpackEmpty(ItemStack stack) {
        ListTag tag = stack.getOrCreateTag().getList("Inventory", Tag.TAG_COMPOUND);

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
        ListTag tag = stack.getOrCreateTag().getList("Inventory", Tag.TAG_COMPOUND);

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

    public static ResourceLocation id(String name) {
        return new ResourceLocation(MOD_ID, name);
    }
}
