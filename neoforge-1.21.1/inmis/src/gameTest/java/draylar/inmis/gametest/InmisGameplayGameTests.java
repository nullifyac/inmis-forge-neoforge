package draylar.inmis.gametest;

import draylar.inmis.Inmis;
import draylar.inmis.config.InmisConfig;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.item.component.BackpackComponent;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Real ServerLevel pickup, damage, menu, and recipe paths with production mixins enabled. */
@GameTestHolder(InmisGameTestsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InmisGameplayGameTests {

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void immortalWorksWithOccupiedShieldAndConsumesExactlyOnce(GameTestHelper helper) {
        withSurvivalPlayer(helper, player -> {
            ItemStack backpack = immortalBackpack(true);
            player.getInventory().setItem(9, backpack);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));

            player.hurt(player.damageSources().generic(), 1_000);

            helper.assertTrue(player.isAlive() && player.getHealth() == 1, "Backpack totem failed with occupied offhand");
            checkCount(helper, contentsCount(backpack, Items.TOTEM_OF_UNDYING), 0, "Consumed backpack totems");
            helper.assertTrue(player.getOffhandItem().is(Items.SHIELD), "Shield was overwritten");

            resetDamageProtection(player);
            player.hurt(player.damageSources().generic(), 1_000);

            helper.assertTrue(player.isDeadOrDying(), "Consumed totem was reusable after the first resurrection");
        });
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void heldMainHandTotemHasPriorityOverBackpack(GameTestHelper helper) {
        withSurvivalPlayer(helper, player -> assertHeldTotemPriority(helper, player, InteractionHand.MAIN_HAND));
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void heldOffhandTotemHasPriorityOverBackpack(GameTestHelper helper) {
        withSurvivalPlayer(helper, player -> assertHeldTotemPriority(helper, player, InteractionHand.OFF_HAND));
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void disabledImmortalLeavesBackpackTotemUnused(GameTestHelper helper) {
        withSurvivalPlayer(helper, player -> {
            ItemStack backpack = immortalBackpack(false);
            player.getInventory().setItem(9, backpack);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));

            player.hurt(player.damageSources().generic(), 1_000);

            helper.assertTrue(player.isDeadOrDying(), "Disabled Immortal prevented lethal damage");
            checkCount(helper, contentsCount(backpack, Items.TOTEM_OF_UNDYING), 1, "Disabled augment changed its totem");
        });
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void bypassDamageDoesNotUseBackpackTotem(GameTestHelper helper) {
        withSurvivalPlayer(helper, player -> {
            ItemStack backpack = immortalBackpack(true);
            player.getInventory().setItem(9, backpack);

            player.hurt(player.damageSources().fellOutOfWorld(), 1_000);

            helper.assertTrue(player.isDeadOrDying(), "Backpack totem incorrectly protected against void damage");
            checkCount(helper, contentsCount(backpack, Items.TOTEM_OF_UNDYING), 1, "Bypassing damage consumed a totem");
        });
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void pickupWithoutFunnellingUsesTheNormalPlayerInventory(GameTestHelper helper) {
        withSurvivalPlayer(helper, player -> {
            ItemEntity drop = drop(helper, new ItemStack(Items.DIAMOND, 7));

            drop.playerTouch(player);

            checkCount(helper, playerCount(player, Items.DIAMOND), 7, "Vanilla pickup lost items without an eligible backpack");
            helper.assertTrue(drop.isRemoved(), "Fully picked up vanilla item was left in the world");
        });
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void funnellingCannotNestAnEqualTierDroppedBackpack(GameTestHelper helper) {
        withSurvivalPlayer(helper, player -> {
            ItemStack equipped = funnellingBackpack();
            player.getInventory().setItem(9, equipped);
            ItemStack droppedBackpack = backpack("frayed");
            droppedBackpack.set(Inmis.BACKPACK_COMPONENT.get(),
                    new BackpackComponent(List.of(new ItemStack(Items.DIAMOND, 32))));
            ItemEntity drop = drop(helper, droppedBackpack);

            drop.playerTouch(player);

            helper.assertTrue(drop.isRemoved(), "An otherwise available vanilla inventory failed to collect the backpack");
            checkCount(helper, contentsCount(equipped, droppedBackpack.getItem()), 0, "Funnelling nested another backpack");
            ItemStack collected = player.getInventory().getItem(0);
            helper.assertTrue(collected.is(droppedBackpack.getItem()), "Dropped backpack did not reach normal inventory");
            checkCount(helper, contentsCount(collected, Items.DIAMOND), 32, "Nested backpack's original contents were changed");
        });
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void funnellingCollectsAllowedItemsAndOrdinaryBlockItems(GameTestHelper helper) {
        withSurvivalPlayer(helper, player -> {
            ItemStack backpack = funnellingBackpack();
            player.getInventory().setItem(9, backpack);
            ItemEntity diamonds = drop(helper, new ItemStack(Items.DIAMOND, 32));
            ItemEntity chests = drop(helper, new ItemStack(Items.CHEST, 4));

            diamonds.playerTouch(player);
            chests.playerTouch(player);

            checkCount(helper, contentsCount(backpack, Items.DIAMOND), 32, "Allowed diamond pickup");
            checkCount(helper, contentsCount(backpack, Items.CHEST), 4, "Allowed block-item pickup");
            checkCount(helper, playerCount(player, Items.DIAMOND), 0, "Pickup duplicated diamonds into vanilla inventory");
            checkCount(helper, playerCount(player, Items.CHEST), 0, "Pickup duplicated chests into vanilla inventory");
            helper.assertTrue(diamonds.isRemoved() && chests.isRemoved(), "Fully funnelled drops were left in the world");
        });
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void partialFunnellingConservesTheWorldRemainder(GameTestHelper helper) {
        withSurvivalPlayer(helper, player -> {
            ItemStack backpack = funnellingBackpack();
            List<ItemStack> contents = new ArrayList<>(Collections.nCopies(9, new ItemStack(Items.COBBLESTONE, 64)));
            contents.set(0, new ItemStack(Items.DIAMOND, 61));
            backpack.set(Inmis.BACKPACK_COMPONENT.get(), new BackpackComponent(contents));
            fillPlayerInventory(player);
            player.getInventory().setItem(9, backpack);
            ItemEntity diamonds = drop(helper, new ItemStack(Items.DIAMOND, 8));

            diamonds.playerTouch(player);

            checkCount(helper, contentsCount(backpack, Items.DIAMOND), 64, "Partial pickup stored count");
            helper.assertTrue(!diamonds.isRemoved(), "Uncollected remainder was discarded");
            checkCount(helper, diamonds.getItem().getCount(), 5, "Partial pickup world remainder");
            checkCount(helper, playerCount(player, Items.DIAMOND), 0, "Full vanilla inventory received diamonds");
            checkCount(helper, contentsCount(backpack, Items.DIAMOND) + diamonds.getItem().getCount(), 69,
                    "Partial pickup total item conservation");
        });
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void funnellingRejectsBlacklistedBlocksAndShulkers(GameTestHelper helper) {
        withSurvivalPlayer(helper, player -> {
            Inmis.CONFIG.blacklist = List.of("minecraft:chest");
            ItemStack backpack = funnellingBackpack();
            fillPlayerInventory(player);
            player.getInventory().setItem(9, backpack);
            ItemEntity chests = drop(helper, new ItemStack(Items.CHEST, 4));
            ItemEntity shulker = drop(helper, new ItemStack(Items.SHULKER_BOX));

            chests.playerTouch(player);
            shulker.playerTouch(player);

            checkCount(helper, contentsCount(backpack, Items.CHEST), 0, "Blacklisted ordinary BlockItem was stored");
            checkCount(helper, contentsCount(backpack, Items.SHULKER_BOX), 0, "Disabled shulker was stored");
            helper.assertTrue(!chests.isRemoved() && !shulker.isRemoved(), "Rejected items were deleted");
            checkCount(helper, chests.getItem().getCount(), 4, "Rejected chest count");
            checkCount(helper, shulker.getItem().getCount(), 1, "Rejected shulker count");
        });
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void openMenuAndPickupShareOneLiveInventory(GameTestHelper helper) {
        withSurvivalPlayer(helper, player -> {
            ItemStack backpack = funnellingBackpack();
            backpack.set(Inmis.BACKPACK_COMPONENT.get(),
                    new BackpackComponent(List.of(new ItemStack(Items.DIAMOND, 32))));
            player.getInventory().setItem(2, backpack);
            AbstractContainerMenu previous = player.containerMenu;
            BackpackScreenHandler menu = new BackpackScreenHandler(1, player.getInventory(), backpack);
            player.containerMenu = menu;
            try {
                helper.assertTrue(menu.stillValid(player), "Owned backpack menu unexpectedly invalid");
                ItemEntity diamonds = drop(helper, new ItemStack(Items.DIAMOND, 8));

                diamonds.playerTouch(player);

                checkCount(helper, menu.getSlot(0).getItem().getCount(), 40, "Open menu missed funnel pickup");
                checkCount(helper, contentsCount(backpack, Items.DIAMOND), 40, "Saved contents missed funnel pickup");
                ItemStack removed = menu.getBackpackInventory().removeItem(0, 10);
                checkCount(helper, contentsCount(backpack, Items.DIAMOND), 30, "Menu write replaced fresh pickup with stale contents");
                ItemStack moved = menu.quickMoveStack(player, 0);
                checkCount(helper, moved.getCount(), 30, "Shift-click did not extract current contents");
                checkCount(helper, playerCount(player, Items.DIAMOND) + removed.getCount(), 40, "Menu/pickup/extraction item conservation");
                checkCount(helper, contentsCount(backpack, Items.DIAMOND), 0, "Extracted contents remained saved");
            } finally {
                player.containerMenu = previous;
            }
        });
    }

    @GameTest(templateNamespace = "inmis_game_tests", template = "empty")
    public static void upgradeRecipePreservesContentsBeyondTheNewTierCapacity(GameTestHelper helper) {
        withSurvivalPlayer(helper, player -> {
            ItemStack original = funnellingBackpack();
            List<ItemStack> contents = new ArrayList<>(Collections.nCopies(27, ItemStack.EMPTY));
            contents.set(0, new ItemStack(Items.DIAMOND, 32));
            ItemStack armor = new ItemStack(Items.NETHERITE_CHESTPLATE);
            armor.set(DataComponents.CUSTOM_NAME, Component.literal("Recovered armor"));
            contents.set(26, armor);
            original.set(Inmis.BACKPACK_COMPONENT.get(), new BackpackComponent(contents));
            List<ItemStack> ingredients = new ArrayList<>(Collections.nCopies(9, new ItemStack(Items.IRON_INGOT)));
            ingredients.set(4, original);
            CraftingInput input = CraftingInput.of(3, 3, ingredients);
            var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                    .orElseThrow(() -> new IllegalStateException("Plated backpack recipe was not loaded or did not match"));
            helper.assertTrue(recipe.id().equals(Inmis.id("plated_backpack")), "Unexpected backpack upgrade recipe matched");

            ItemStack upgraded = recipe.value().assemble(input, helper.getLevel().registryAccess());

            helper.assertTrue(upgraded.is(backpack("plated").getItem()), "Recipe did not create a plated backpack");
            checkCount(helper, contentsCount(upgraded, Items.DIAMOND), 32, "Upgrade diamond count");
            checkCount(helper, contentsCount(upgraded, Items.NETHERITE_CHESTPLATE), 1, "Upgrade discarded overflow armor");
            helper.assertTrue(upgraded.get(Inmis.BACKPACK_COMPONENT.get()).stacks().get(26)
                    .getHoverName().getString().equals("Recovered armor"), "Upgrade discarded armor components");
            helper.assertTrue(upgraded.get(Inmis.BACKPACK_AUGMENTS.get()).equals(original.get(Inmis.BACKPACK_AUGMENTS.get())),
                    "Upgrade lost augment settings");
            player.getInventory().setItem(2, upgraded);
            BackpackScreenHandler recovery = new BackpackScreenHandler(1, player.getInventory(), upgraded);
            helper.assertTrue(!recovery.getSlot(26).mayPlace(new ItemStack(Items.DIAMOND)), "Overflow became new usable storage");
            ItemStack recovered = recovery.quickMoveStack(player, 26);
            helper.assertTrue(recovered.is(Items.NETHERITE_CHESTPLATE), "Upgraded overflow item cannot be recovered");
            checkCount(helper, contentsCount(upgraded, Items.NETHERITE_CHESTPLATE), 0, "Recovered overflow armor remained saved");
            checkCount(helper, contentsCount(original, Items.NETHERITE_CHESTPLATE), 1, "Craft output mutated its input preview");
        });
    }

    private static void assertHeldTotemPriority(GameTestHelper helper, Player player, InteractionHand hand) {
        ItemStack backpack = immortalBackpack(true);
        player.getInventory().setItem(9, backpack);
        player.setItemInHand(hand, new ItemStack(Items.TOTEM_OF_UNDYING));

        player.hurt(player.damageSources().generic(), 1_000);

        helper.assertTrue(player.isAlive() && player.getHealth() == 1, "Held vanilla totem did not resurrect player");
        helper.assertTrue(player.getItemInHand(hand).isEmpty(), "Held vanilla totem was not consumed");
        checkCount(helper, contentsCount(backpack, Items.TOTEM_OF_UNDYING), 1, "Backpack totem was consumed before held totem");
    }

    private static ItemStack immortalBackpack(boolean enabled) {
        ItemStack backpack = backpack("withered");
        backpack.set(Inmis.BACKPACK_COMPONENT.get(), new BackpackComponent(List.of(new ItemStack(Items.TOTEM_OF_UNDYING))));
        backpack.set(Inmis.BACKPACK_AUGMENTS.get(), BackpackAugmentsComponent.DEFAULT.withImmortalEnabled(enabled));
        return backpack;
    }

    private static ItemStack funnellingBackpack() {
        ItemStack backpack = backpack("frayed");
        backpack.set(Inmis.BACKPACK_AUGMENTS.get(), BackpackAugmentsComponent.DEFAULT.withFunnelling(
                new BackpackAugmentsComponent.FunnellingSettings(true,
                        BackpackAugmentsComponent.FunnellingSettings.Mode.ALLOW, List.of())));
        return backpack;
    }

    private static ItemStack backpack(String tier) {
        Item item = BuiltInRegistries.ITEM.get(Inmis.id(tier + "_backpack"));
        if (!(item instanceof BackpackItem)) {
            throw new IllegalStateException("Backpack tier is not registered: " + tier);
        }
        return new ItemStack(item);
    }

    private static ItemEntity drop(GameTestHelper helper, ItemStack stack) {
        BlockPos position = helper.absolutePos(new BlockPos(1, 1, 1));
        ItemEntity entity = new ItemEntity(helper.getLevel(), position.getX() + 0.5, position.getY(), position.getZ() + 0.5, stack);
        entity.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static void fillPlayerInventory(Player player) {
        for (int i = 0; i < 36; i++) {
            player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        }
    }

    private static int contentsCount(ItemStack backpack, Item item) {
        return Inmis.getBackpackContents(backpack).stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static int playerCount(Player player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void checkCount(GameTestHelper helper, int actual, int expected, String message) {
        helper.assertTrue(actual == expected, message + ": expected " + expected + ", got " + actual);
    }

    private static void resetDamageProtection(Player player) {
        player.removeAllEffects();
        player.setAbsorptionAmount(0);
        player.invulnerableTime = 0;
    }

    private static void withSurvivalPlayer(GameTestHelper helper, Consumer<Player> test) {
        InmisConfig previous = Inmis.CONFIG;
        Inmis.CONFIG = new InmisConfig();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos position = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        player.getInventory().clearContent();
        player.getAbilities().invulnerable = false;
        try {
            test.accept(player);
            helper.succeed();
        } finally {
            Inmis.CONFIG = previous;
            player.discard();
            helper.killAllEntities();
        }
    }
}
