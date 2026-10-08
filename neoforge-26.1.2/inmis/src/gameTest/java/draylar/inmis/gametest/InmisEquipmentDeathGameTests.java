package draylar.inmis.gametest;

import com.mojang.authlib.GameProfile;
import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.config.InmisConfig;
import draylar.inmis.item.BackpackItem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class InmisEquipmentDeathGameTests {

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void curiosNativeDropsSpillWithoutDeletingIdenticalMainBackpack(GameTestHelper helper) {
        if (Inmis.CURIOS_LOADED) CuriosChecks.scenario(helper, 0);
        helper.succeed();
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void curiosKeepDestroyAndVanishingRulesRemainNative(GameTestHelper helper) {
        if (Inmis.CURIOS_LOADED) for (int scenario : new int[]{1, 2, 3}) CuriosChecks.scenario(helper, scenario);
        helper.succeed();
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void curiosRetentionAndDisabledSpillControlsRemainNative(GameTestHelper helper) {
        if (Inmis.CURIOS_LOADED) for (int scenario : new int[]{4, 5, 6}) CuriosChecks.scenario(helper, scenario);
        helper.succeed();
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void curiosExplicitDropRuleCanOverrideKeepInventory(GameTestHelper helper) {
        if (Inmis.CURIOS_LOADED) CuriosChecks.scenario(helper, 7);
        helper.succeed();
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void curiosNativeDropCancellationDoesNotLeakOrWipeCancelledBags(GameTestHelper helper) {
        if (Inmis.CURIOS_LOADED) CuriosChecks.scenario(helper, 8);
        helper.succeed();
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void curiosSpilledContentsRespectLivingDropsCancellation(GameTestHelper helper) {
        if (Inmis.CURIOS_LOADED) CuriosChecks.scenario(helper, 9);
        helper.succeed();
    }


    private static final class CuriosChecks {
        private static void scenario(GameTestHelper helper, int scenario) {
            withPlayer(helper, scenario == 4 || scenario == 7, player -> {
                var handler = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).orElseThrow(
                        () -> new AssertionError("Native Curios capability is missing"));
                // Fresh GameTest players have not passed Curios player-data/login initialization.
                handler.loadDatapacks();
                var stacks = handler.getStacksHandler("back").orElseThrow(
                        () -> new AssertionError("Native Curios back slot was not registered")).getStacks();
                check(helper, stacks.getSlots() > 0, "Native Curios back slot has no capacity");
                ItemStack bag = backpack();
                check(helper, stacks.isItemValid(0, bag), "Native Curios back slot rejects the Inmis backpack");
                check(helper, stacks.insertItem(0, bag, false).isEmpty(), "Native Curios backpack admission failed");
                ItemStack equipped = stacks.getStackInSlot(0);
                check(helper, packed(equipped) == 7, "Equipped Curios backpack lost its contents");
                if (scenario == 0) player.getInventory().setItem(1, equipped.copy());
                if (scenario == 3) equipped.enchant(helper.getLevel().holderLookup(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.VANISHING_CURSE), 1);
                Inmis.CONFIG.spillArmorBackpacksOnDeath = scenario != 5;
                Inmis.CONFIG.enableTrinketCompatibility = scenario != 6;
                var rule = switch (scenario) {
                    case 1 -> top.theillusivec4.curios.api.common.DropRule.ALWAYS_KEEP;
                    case 2 -> top.theillusivec4.curios.api.common.DropRule.DESTROY;
                    case 7 -> top.theillusivec4.curios.api.common.DropRule.ALWAYS_DROP;
                    default -> top.theillusivec4.curios.api.common.DropRule.DEFAULT;
                };
                Consumer<top.theillusivec4.curios.api.event.DropRulesEvent> override = event -> {
                    if (event.getEntity() == player && rule != top.theillusivec4.curios.api.common.DropRule.DEFAULT) {
                        event.addOverride(stack -> stack.getItem() instanceof BackpackItem, rule);
                    }
                };
                List<ItemEntity> captured = new ArrayList<>();
                Consumer<top.theillusivec4.curios.api.event.CurioDropsEvent> cancelNative = event -> {
                    if (event.getEntity() == player && scenario == 8) {
                        captured.addAll(event.getDrops());
                        event.setCanceled(true);
                    }
                };
                Consumer<LivingDropsEvent> cancelOuter = event -> {
                    if (event.getEntity() == player && scenario == 9) {
                        captured.addAll(event.getDrops());
                        event.setCanceled(true);
                    }
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, top.theillusivec4.curios.api.event.DropRulesEvent.class, override);
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, top.theillusivec4.curios.api.event.CurioDropsEvent.class, cancelNative);
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDropsEvent.class, cancelOuter);
                try {
                    kill(helper, player);
                    check(helper, player.isDeadOrDying(), "Fixture did not reach death");
                    List<ItemEntity> drops = worldDrops(helper, player);
                    if (scenario == 1 || scenario == 4) {
                        check(helper, stacks.getStackInSlot(0) == equipped && packed(equipped) == 7,
                                "Native retained Curios backpack was cleared or spilled");
                        check(helper, drops.isEmpty(), "Native retained Curios backpack produced drops");
                    } else if (scenario == 2 || scenario == 3) {
                        check(helper, stacks.getStackInSlot(0).isEmpty() && drops.isEmpty(),
                                "Native destroyed Curios backpack was recreated or spilled");
                    } else if (scenario == 8 || scenario == 9) {
                        check(helper, drops.isEmpty(), "Cancelled equipped death drops leaked into the world");
                        check(helper, bagCount(captured) == 1, "Cancelled event lost its selected backpack");
                        check(helper, loose(captured) == (scenario == 9 ? 7 : 0)
                                        && packed(captured) == (scenario == 9 ? 0 : 7),
                                "Cancelled native/outer event changed backpack contents unexpectedly");
                        check(helper, stacks.getStackInSlot(0).isEmpty(), "Native selected drop retained its source slot");
                    } else {
                        boolean spill = scenario != 5 && scenario != 6;
                        int mainCopy = scenario == 0 ? 1 : 0;
                        check(helper, stacks.getStackInSlot(0).isEmpty(), "Native dropping Curios slot was not cleared");
                        check(helper, bagCount(drops) == 1 + mainCopy, "Equal backpack was deleted or duplicated");
                        check(helper, loose(drops) == (spill ? 7 : 0), "Curios spill produced incorrect loose count");
                        check(helper, packed(drops) == (spill ? 0 : 7) + mainCopy * 7,
                                "Curios spill altered an unrelated backpack or lost stored items");
                    }
                } finally {
                    NeoForge.EVENT_BUS.unregister(override);
                    NeoForge.EVENT_BUS.unregister(cancelNative);
                    NeoForge.EVENT_BUS.unregister(cancelOuter);
                }
            });
        }
    }

    private static ItemStack backpack() {
        BackpackItem item = Inmis.BACKPACKS.stream().map(entry -> entry.get())
                .filter(entry -> entry.getTier().getName().equals("frayed")).findFirst().orElseThrow();
        ItemStack stack = new ItemStack(item);
        new BackpackInventory(stack, item.getTier()).setItem(0, new ItemStack(Items.DIAMOND, 7));
        return stack;
    }

    private static int packed(ItemStack stack) {
        return Inmis.getBackpackContents(stack).stream().filter(contents -> contents.is(Items.DIAMOND))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static int packed(List<ItemEntity> drops) {
        return drops.stream().map(ItemEntity::getItem).filter(stack -> stack.getItem() instanceof BackpackItem)
                .mapToInt(InmisEquipmentDeathGameTests::packed).sum();
    }

    private static int loose(List<ItemEntity> drops) {
        return drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(Items.DIAMOND))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static int bagCount(List<ItemEntity> drops) {
        return drops.stream().map(ItemEntity::getItem).filter(stack -> stack.getItem() instanceof BackpackItem)
                .mapToInt(ItemStack::getCount).sum();
    }

    private static List<ItemEntity> worldDrops(GameTestHelper helper, Player player) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(2),
                entity -> !entity.isRemoved());
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }

    private static void kill(GameTestHelper helper, Player player) {
        player.setHealth(1);
        player.setAbsorptionAmount(0);
        player.invulnerableTime = 0;
        player.hurtServer(helper.getLevel(), player.damageSources().generic(), 100);
    }

    private static void withPlayer(GameTestHelper helper, boolean keepInventory, Consumer<Player> test) {
        InmisConfig previous = Inmis.CONFIG;
        var rule = helper.getLevel().getGameRules();
        boolean previousKeepInventory = rule.get(GameRules.KEEP_INVENTORY);
        Inmis.CONFIG = new InmisConfig();
        Inmis.CONFIG.enableTrinketCompatibility = true;
        Inmis.CONFIG.spillArmorBackpacksOnDeath = true;
        Inmis.CONFIG.spillMainBackpacksOnDeath = false;
        rule.set(GameRules.KEEP_INVENTORY, keepInventory, helper.getLevel().getServer());
        Player player = makePlayer(helper);
        BlockPos position = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        player.getInventory().clearContent();
        player.getAbilities().invulnerable = false;
        player.getAbilities().instabuild = false;
        try {
            test.accept(player);
        } finally {
            Inmis.CONFIG = previous;
            rule.set(GameRules.KEEP_INVENTORY, previousKeepInventory, helper.getLevel().getServer());
            player.discard();
            helper.killAllEntities();
        }
    }

    private static Player makePlayer(GameTestHelper helper) {
        return new Player(helper.getLevel(), new GameProfile(UUID.randomUUID(), "InmisEquippedTest")) {
            @Override public net.minecraft.world.level.GameType gameMode() { return net.minecraft.world.level.GameType.SURVIVAL; }

                    };
    }
}
