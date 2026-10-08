package draylar.inmis.gametest;

import com.mojang.authlib.GameProfile;
import draylar.inmis.Inmis;
import draylar.inmis.config.InmisConfig;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Exercises captured death drops through vanilla Player.hurt -> dropEquipment -> Inventory.dropAll. */
public final class InmisDeathGameTests {

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void mainSpillingIncludesOffhandAndLeavesArmorPacked(GameTestHelper helper) {
        assertDeathCategories(helper, true, false);
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void armorSpillingLeavesMainAndOffhandPacked(GameTestHelper helper) {
        assertDeathCategories(helper, false, true);
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void bothSpillingSettingsPreserveAllContentsAndBackpacks(GameTestHelper helper) {
        assertDeathCategories(helper, true, true);
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void disabledSpillingDropsIntactBackpacks(GameTestHelper helper) {
        assertDeathCategories(helper, false, false);
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void keepInventoryOverridesBothSpillingSettings(GameTestHelper helper) {
        withDeathState(helper, true, player -> {
            Inmis.CONFIG.spillMainBackpacksOnDeath = true;
            Inmis.CONFIG.spillArmorBackpacksOnDeath = true;
            equipBackpacks(player);

            player.hurtServer(helper.getLevel(), player.damageSources().generic(), 1_000);

            helper.assertTrue(player.isDeadOrDying(), "Damage did not trigger a real player death");
            helper.assertTrue(drops(player).isEmpty(), "keepInventory produced item drops");
            checkCount(helper, contentsCount(player.getInventory().getItem(1), Items.DIAMOND), 7, "Kept main contents");
            checkCount(helper, contentsCount(player.getOffhandItem(), Items.IRON_INGOT), 11, "Kept offhand contents");
            checkCount(helper, contentsCount(player.getItemBySlot(EquipmentSlot.CHEST), Items.EMERALD), 13, "Kept armor contents");
        });
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void identicalBackpacksSpillWithoutDeletingEitherBackpack(GameTestHelper helper) {
        withDeathState(helper, false, player -> {
            Inmis.CONFIG.spillMainBackpacksOnDeath = true;
            ItemStack first = namedBackpack("identical", Items.DIAMOND, 7);
            player.getInventory().setItem(1, first);
            player.setItemSlot(EquipmentSlot.OFFHAND, first.copy());

            player.hurtServer(helper.getLevel(), player.damageSources().generic(), 1_000);

            List<ItemEntity> drops = drops(player);
            checkCount(helper, backpackCount(drops), 2, "Identical backpacks were collapsed or duplicated");
            checkCount(helper, looseCount(drops, Items.DIAMOND), 14, "Identical backpack contents were lost or duplicated");
            checkCount(helper, packedCount(drops, Items.DIAMOND), 0, "Identical spilled backpacks retained their contents");
        });
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void vanishingCursedBackpacksDoNotSpillTheirContents(GameTestHelper helper) {
        withDeathState(helper, false, player -> {
            Inmis.CONFIG.spillMainBackpacksOnDeath = true;
            Inmis.CONFIG.spillArmorBackpacksOnDeath = true;
            equipBackpacks(player);
            var vanishing = helper.getLevel().holderLookup(Registries.ENCHANTMENT).getOrThrow(Enchantments.VANISHING_CURSE);
            player.getInventory().getItem(1).enchant(vanishing, 1);
            player.getOffhandItem().enchant(vanishing, 1);
            player.getItemBySlot(EquipmentSlot.CHEST).enchant(vanishing, 1);

            player.hurtServer(helper.getLevel(), player.damageSources().generic(), 1_000);

            helper.assertTrue(player.isDeadOrDying(), "Damage did not trigger a real player death");
            helper.assertTrue(drops(player).isEmpty(), "Spilling bypassed the Curse of Vanishing purge");
            helper.assertTrue(player.getInventory().isEmpty(), "Vanished backpacks remained in vanilla inventory");
        });
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void livingDropsCancellationCapturesBothBackpacksAndSpilledContents(GameTestHelper helper) {
        withDeathState(helper, false, player -> {
            Inmis.CONFIG.spillMainBackpacksOnDeath = true;
            Inmis.CONFIG.spillArmorBackpacksOnDeath = true;
            equipBackpacks(player);
            List<ItemEntity> captured = new ArrayList<>();
            Consumer<LivingDropsEvent> cancelDrops = event -> {
                if (event.getEntity() == player) {
                    captured.addAll(event.getDrops());
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDropsEvent.class, cancelDrops);
            try {
                player.hurtServer(helper.getLevel(), player.damageSources().generic(), 1_000);

                helper.assertTrue(player.isDeadOrDying(), "Damage did not trigger a real player death");
                checkCount(helper, backpackCount(captured), 3, "Cancelled event backpack conservation");
                assertMaterial(helper, captured, Items.DIAMOND, 7, true, "cancelled main");
                assertMaterial(helper, captured, Items.IRON_INGOT, 11, true, "cancelled offhand");
                assertMaterial(helper, captured, Items.EMERALD, 13, true, "cancelled armor");
                helper.assertTrue(drops(player).isEmpty(), "Spilling bypassed LivingDropsEvent cancellation");
                helper.assertTrue(player.getInventory().isEmpty(), "Cancelled vanilla death drops retained source inventory");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancelDrops);
            }
        });
    }

    private static void assertDeathCategories(GameTestHelper helper, boolean spillMain, boolean spillArmor) {
        withDeathState(helper, false, player -> {
            Inmis.CONFIG.spillMainBackpacksOnDeath = spillMain;
            Inmis.CONFIG.spillArmorBackpacksOnDeath = spillArmor;
            equipBackpacks(player);

            player.hurtServer(helper.getLevel(), player.damageSources().generic(), 1_000);

            helper.assertTrue(player.isDeadOrDying(), "Damage did not trigger a real player death");
            List<ItemEntity> drops = drops(player);
            checkCount(helper, backpackCount(drops), 3, "Death backpack conservation");
            assertMaterial(helper, drops, Items.DIAMOND, 7, spillMain, "main");
            assertMaterial(helper, drops, Items.IRON_INGOT, 11, spillMain, "offhand");
            assertMaterial(helper, drops, Items.EMERALD, 13, spillArmor, "armor");
            helper.assertTrue(player.getInventory().isEmpty(), "Death retained vanilla inventory while keepInventory was false");
        });
    }

    private static void assertMaterial(GameTestHelper helper, List<ItemEntity> drops, Item item, int original,
                                       boolean spill, String category) {
        checkCount(helper, looseCount(drops, item), spill ? original : 0, category + " loose contents");
        checkCount(helper, packedCount(drops, item), spill ? 0 : original, category + " packed contents");
        checkCount(helper, looseCount(drops, item) + packedCount(drops, item), original, category + " total item conservation");
    }

    private static void equipBackpacks(Player player) {
        player.getInventory().setItem(1, namedBackpack("main", Items.DIAMOND, 7));
        player.setItemSlot(EquipmentSlot.OFFHAND, namedBackpack("offhand", Items.IRON_INGOT, 11));
        player.setItemSlot(EquipmentSlot.CHEST, namedBackpack("armor", Items.EMERALD, 13));
    }

    private static ItemStack namedBackpack(String name, Item contents, int count) {
        ItemStack backpack = new ItemStack(BuiltInRegistries.ITEM.getValue(Inmis.id("frayed_backpack")));
        backpack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        backpack.set(Inmis.BACKPACK_COMPONENT.get(), new BackpackComponent(List.of(new ItemStack(contents, count))));
        return backpack;
    }

    private static List<ItemEntity> drops(Player player) {
        return player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(2), entity -> !entity.isRemoved());
    }

    private static int backpackCount(List<ItemEntity> drops) {
        return drops.stream().map(ItemEntity::getItem).filter(stack -> stack.getItem() instanceof BackpackItem)
                .mapToInt(ItemStack::getCount).sum();
    }

    private static int looseCount(List<ItemEntity> drops, Item item) {
        return drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static int packedCount(List<ItemEntity> drops, Item item) {
        return drops.stream().map(ItemEntity::getItem).filter(stack -> stack.getItem() instanceof BackpackItem)
                .mapToInt(stack -> contentsCount(stack, item)).sum();
    }

    private static int contentsCount(ItemStack backpack, Item item) {
        return Inmis.getBackpackContents(backpack).stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void checkCount(GameTestHelper helper, int actual, int expected, String message) {
        helper.assertTrue(actual == expected, message + ": expected " + expected + ", got " + actual);
    }

    private static void withDeathState(GameTestHelper helper, boolean keepInventory, Consumer<Player> test) {
        InmisConfig previousConfig = Inmis.CONFIG;
        var rule = helper.getLevel().getGameRules();
        boolean previousKeepInventory = rule.get(GameRules.KEEP_INVENTORY);
        Inmis.CONFIG = new InmisConfig();
        rule.set(GameRules.KEEP_INVENTORY, keepInventory, helper.getLevel().getServer());
        Player player = makeDeathPlayer(helper);
        BlockPos position = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        player.getInventory().clearContent();
        player.getAbilities().invulnerable = false;
        try {
            test.accept(player);
            helper.succeed();
        } finally {
            Inmis.CONFIG = previousConfig;
            rule.set(GameRules.KEEP_INVENTORY, previousKeepInventory, helper.getLevel().getServer());
            player.discard();
            helper.killAllEntities();
        }
    }

    private static Player makeDeathPlayer(GameTestHelper helper) {
        return new Player(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "inmis-death-test")) {
            @Override public net.minecraft.world.level.GameType gameMode() { return net.minecraft.world.level.GameType.SURVIVAL; }



                    };
    }
}
