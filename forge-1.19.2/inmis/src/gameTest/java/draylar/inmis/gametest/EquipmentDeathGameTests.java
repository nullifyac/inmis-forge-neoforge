package draylar.inmis.gametest;

import com.mojang.authlib.GameProfile;
import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.config.InmisConfig;
import draylar.inmis.item.BackpackItem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("inmis_game_tests")
@PrefixGameTestTemplate(false)
public final class EquipmentDeathGameTests {

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
                var handler = top.theillusivec4.curios.api.CuriosApi.getCuriosHelper().getCuriosHandler(player).resolve().orElseThrow(
                        () -> new AssertionError("Native Curios capability is missing"));
                var stacks = handler.getStacksHandler("back").orElseThrow(
                        () -> new AssertionError("Native Curios back slot was not registered")).getStacks();
                check(helper, stacks.getSlots() > 0, "Native Curios back slot has no capacity");
                ItemStack bag = backpack();
                check(helper, stacks.isItemValid(0, bag), "Native Curios back slot rejects the Inmis backpack");
                check(helper, stacks.insertItem(0, bag, false).isEmpty(), "Native Curios backpack admission failed");
                ItemStack equipped = stacks.getStackInSlot(0);
                check(helper, packed(equipped) == 7, "Equipped Curios backpack lost its contents");
                if (scenario == 0) player.getInventory().setItem(1, equipped.copy());
                if (scenario == 3) equipped.enchant(net.minecraft.world.item.enchantment.Enchantments.VANISHING_CURSE, 1);
                Inmis.CONFIG.spillArmorBackpacksOnDeath = scenario != 5;
                Inmis.CONFIG.enableTrinketCompatibility = scenario != 6;
                var rule = switch (scenario) {
                    case 1 -> top.theillusivec4.curios.api.type.capability.ICurio.DropRule.ALWAYS_KEEP;
                    case 2 -> top.theillusivec4.curios.api.type.capability.ICurio.DropRule.DESTROY;
                    case 7 -> top.theillusivec4.curios.api.type.capability.ICurio.DropRule.ALWAYS_DROP;
                    default -> top.theillusivec4.curios.api.type.capability.ICurio.DropRule.DEFAULT;
                };
                Consumer<top.theillusivec4.curios.api.event.DropRulesEvent> override = event -> {
                    if (event.getEntity() == player && rule != top.theillusivec4.curios.api.type.capability.ICurio.DropRule.DEFAULT) {
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
                MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, top.theillusivec4.curios.api.event.DropRulesEvent.class, override);
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, top.theillusivec4.curios.api.event.CurioDropsEvent.class, cancelNative);
                MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDropsEvent.class, cancelOuter);
                try {
                    kill(player);
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
                    MinecraftForge.EVENT_BUS.unregister(override);
                    MinecraftForge.EVENT_BUS.unregister(cancelNative);
                    MinecraftForge.EVENT_BUS.unregister(cancelOuter);
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
                .mapToInt(EquipmentDeathGameTests::packed).sum();
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
        if (!condition) {
            helper.fail(message);
        }
    }

    private static void kill(Player player) {
        player.setHealth(1);
        player.setAbsorptionAmount(0);
        player.invulnerableTime = 0;
        player.hurt(DamageSource.GENERIC, 100);
    }

    private static void withPlayer(GameTestHelper helper, boolean keepInventory, Consumer<Player> test) {
        InmisConfig previous = Inmis.CONFIG;
        var rule = helper.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
        boolean previousKeepInventory = rule.get();
        Inmis.CONFIG = new InmisConfig();
        Inmis.CONFIG.enableTrinketCompatibility = true;
        Inmis.CONFIG.spillArmorBackpacksOnDeath = true;
        Inmis.CONFIG.spillMainBackpacksOnDeath = false;
        rule.set(keepInventory, helper.getLevel().getServer());
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
            rule.set(previousKeepInventory, helper.getLevel().getServer());
            player.discard();
            helper.killAllEntities();
        }
    }

    private static Player makePlayer(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "InmisEquippedTest")) {
            @Override public boolean isInvulnerableTo(DamageSource source) { return false; }
            @Override public void die(DamageSource source) { dropAllDeathLoot(source); }
        };
        try {
            var spawnProtection = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawnProtection.setAccessible(true);
            spawnProtection.setInt(player, 0);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot remove test spawn protection", exception);
        }
        return player;
    }
}
