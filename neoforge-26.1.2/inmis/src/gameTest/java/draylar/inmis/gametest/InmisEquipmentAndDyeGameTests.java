package draylar.inmis.gametest;

import draylar.inmis.Inmis;
import draylar.inmis.config.InmisConfig;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.item.component.BackpackComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Native admission and recipe paths, rather than direct equipment/component assignment. */
public final class InmisEquipmentAndDyeGameTests {
    @GameTest
    public static void nativeChestSlotAcceptsDragAndShiftEquip(GameTestHelper helper) {
        withPlayer(helper, player -> {
            ItemStack original = filledBackpack();
            var menu = player.inventoryMenu;
            var chest = menu.getSlot(6);
            helper.assertTrue(chest.mayPlace(original), "Native chest slot rejected a backpack");
            menu.setCarried(original.copy());
            menu.clicked(6, 0, ContainerInput.PICKUP, player);
            helper.assertTrue(menu.getCarried().isEmpty(), "Chest placement did not consume the carried backpack");
            helper.assertTrue(ItemStack.matches(player.getItemBySlot(EquipmentSlot.CHEST), original),
                    "Chest placement lost backpack contents or components");

            player.getInventory().clearContent();
            player.getInventory().setItem(9, original.copy());
            menu.clicked(9, 0, ContainerInput.QUICK_MOVE, player);
            helper.assertTrue(player.getInventory().getItem(9).isEmpty(), "Shift-equip retained the original backpack");
            helper.assertTrue(ItemStack.matches(player.getItemBySlot(EquipmentSlot.CHEST), original),
                    "Native shift-equip rejected the backpack or lost components");
            helper.assertTrue(backpackCount(player) == 1, "Shift-equip duplicated or deleted the backpack");
        });
    }

    @GameTest
    public static void nativeChestSlotHonorsDisableAndEmptyUnequip(GameTestHelper helper) {
        withPlayer(helper, player -> {
            ItemStack original = filledBackpack();
            var menu = player.inventoryMenu;
            var chest = menu.getSlot(6);
            Inmis.CONFIG.allowBackpacksInChestplate = false;
            helper.assertTrue(!chest.mayPlace(original), "Disabled chest compatibility still accepts backpacks");
            menu.setCarried(original.copy());
            menu.clicked(6, 0, ContainerInput.PICKUP, player);
            helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                    && ItemStack.matches(menu.getCarried(), original), "Disabled chest placement changed the input");
            menu.setCarried(ItemStack.EMPTY);
            player.getInventory().setItem(9, original.copy());
            menu.clicked(9, 0, ContainerInput.QUICK_MOVE, player);
            helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && backpackCount(player) == 1,
                    "Disabled shift-equip placed a backpack in the chest slot or changed its count");

            player.getInventory().clearContent();
            Inmis.CONFIG.allowBackpacksInChestplate = true;
            Inmis.CONFIG.requireEmptyForUnequip = true;
            menu.setCarried(original.copy());
            menu.clicked(6, 0, ContainerInput.PICKUP, player);
            helper.assertTrue(!chest.mayPickup(player), "Filled backpack bypassed the unequip restriction");
            menu.clicked(6, 0, ContainerInput.PICKUP, player);
            helper.assertTrue(menu.getCarried().isEmpty() && ItemStack.matches(chest.getItem(), original),
                    "Click removed a filled locked backpack");
            Inmis.wipeBackpack(chest.getItem());
            helper.assertTrue(chest.mayPickup(player), "Emptied backpack remained locked");
            menu.clicked(6, 0, ContainerInput.PICKUP, player);
            helper.assertTrue(chest.getItem().isEmpty() && !menu.getCarried().isEmpty(),
                    "Native click failed to remove an emptied backpack");
        });
    }

    @GameTest
    public static void nativeDyeRecipePreservesBackpackContentsAndSettings(GameTestHelper helper) {
        withPlayer(helper, player -> {
            ItemStack original = filledBackpack();
            CraftingInput input = CraftingInput.of(2, 1, List.of(original, new ItemStack(Items.RED_DYE)));
            var recipes = helper.getLevel().getServer().getRecipeManager();
            var recipe = recipes.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                    .orElseThrow(() -> new IllegalStateException("No loaded dye recipe matches the default dyeable backpack"));
            ItemStack dyed = recipe.value().assemble(input);
            helper.assertTrue(dyed.is(original.getItem()) && dyed.getCount() == 1, "Dye recipe changed backpack tier or count");
            helper.assertTrue(dyed.has(DataComponents.DYED_COLOR), "Dye recipe did not apply a color");
            helper.assertTrue(dyed.get(DataComponents.DYED_COLOR).rgb()
                    == DyedItemColor.applyDyes((DyedItemColor) null, List.of(net.minecraft.world.item.DyeColor.RED)).rgb(),
                    "Dye recipe used the wrong dye color");
            helper.assertTrue(dyed.get(Inmis.BACKPACK_COMPONENT.get()).equals(original.get(Inmis.BACKPACK_COMPONENT.get())),
                    "Dye recipe deleted or resized saved recovery contents");
            helper.assertTrue(dyed.get(Inmis.BACKPACK_AUGMENTS.get()).equals(original.get(Inmis.BACKPACK_AUGMENTS.get())),
                    "Dye recipe reset augment settings");
            helper.assertTrue(dyed.getHoverName().getString().equals(original.getHoverName().getString()),
                    "Dye recipe lost custom name");
            helper.assertTrue(!original.has(DataComponents.DYED_COLOR), "Dye output mutated the crafting input");
            CraftingInput twoBags = CraftingInput.of(3, 1,
                    List.of(original, original.copy(), new ItemStack(Items.RED_DYE)));
            helper.assertTrue(recipes.getRecipeFor(RecipeType.CRAFTING, twoBags, helper.getLevel()).isEmpty(),
                    "Dye recipe accepted two backpacks");
        });
    }

    private static ItemStack filledBackpack() {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Inmis.id("frayed_backpack")));
        List<ItemStack> contents = new ArrayList<>(Collections.nCopies(22, ItemStack.EMPTY));
        contents.set(21, new ItemStack(Items.DIAMOND, 37));
        stack.set(Inmis.BACKPACK_COMPONENT.get(), new BackpackComponent(contents));
        stack.set(Inmis.BACKPACK_AUGMENTS.get(), BackpackAugmentsComponent.DEFAULT.withFunnelling(
                BackpackAugmentsComponent.DEFAULT.funnelling().withEnabled(false)));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Dye and equipment recovery bag"));
        return stack;
    }

    private static int backpackCount(Player player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof draylar.inmis.item.BackpackItem) count += stack.getCount();
        }
        return count;
    }

    private static void withPlayer(GameTestHelper helper, Consumer<Player> test) {
        InmisConfig previous = Inmis.CONFIG;
        Inmis.CONFIG = new InmisConfig();
        Inmis.CONFIG.enableTrinketCompatibility = false;
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().clearContent();
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
