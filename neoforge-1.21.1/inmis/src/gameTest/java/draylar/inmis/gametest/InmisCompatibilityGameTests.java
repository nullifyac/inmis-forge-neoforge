package draylar.inmis.gametest;

import draylar.inmis.Inmis;
import draylar.inmis.config.InmisConfig;
import draylar.inmis.item.component.BackpackComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder("inmis_game_tests")
@PrefixGameTestTemplate(false)
public final class InmisCompatibilityGameTests {

    private InmisCompatibilityGameTests() {
    }

    @GameTest(template = "empty")
    public static void runtimeProfileMatchesLoadedMods(GameTestHelper helper) {
        String profile = System.getProperty("inmis.test.compatProfile", "none");
        helper.assertTrue(List.of("none", "curios", "accessories", "both").contains(profile),
                "Unknown compatibility test profile: " + profile);
        helper.assertTrue(Inmis.CURIOS_LOADED == (profile.equals("curios") || profile.equals("both")),
                "Curios runtime presence does not match requested profile " + profile);
        helper.assertTrue(Inmis.ACCESSORIES_LOADED == (profile.equals("accessories") || profile.equals("both")),
                "Accessories runtime presence does not match requested profile " + profile);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void installedIntegrationsRespectEquipToggleAndAllowRecovery(GameTestHelper helper) {
        InmisConfig previousConfig = Inmis.CONFIG;
        Inmis.CONFIG = new InmisConfig();
        try {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack backpack = new ItemStack(Inmis.BACKPACKS.getFirst().get());
            ItemStack pouch = new ItemStack(Inmis.ENDER_POUCH.get());
            // Separate classes keep absent optional APIs outside the baseline test's class linking.
            if (Inmis.CURIOS_LOADED) {
                CuriosChecks.run(helper, player, backpack.copy(), pouch.copy());
            }
            if (Inmis.ACCESSORIES_LOADED) {
                AccessoriesChecks.run(helper, player, backpack.copy(), pouch.copy());
            }
            helper.succeed();
        } finally {
            Inmis.CONFIG = previousConfig;
        }
    }

    private static final class CuriosChecks {

        private static void run(GameTestHelper helper, Player player, ItemStack backpack, ItemStack pouch) {
            var back = new top.theillusivec4.curios.api.SlotContext("back", player, 0, false, true);
            var wrongSlot = new top.theillusivec4.curios.api.SlotContext("ring", player, 0, false, true);
            var backpackCurio = top.theillusivec4.curios.api.CuriosApi.getCurio(backpack).orElseThrow();
            var pouchCurio = top.theillusivec4.curios.api.CuriosApi.getCurio(pouch).orElseThrow();
            var stacks = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .flatMap(handler -> handler.getStacksHandler("back"))
                    .orElseThrow().getStacks();

            Inmis.CONFIG.enableTrinketCompatibility = true;
            helper.assertTrue(backpack.is(TagKey.create(Registries.ITEM,
                            ResourceLocation.fromNamespaceAndPath("curios", "back"))),
                    "The backpack's Curios item tag was not loaded");
            helper.assertTrue(backpackCurio.canEquip(back) && pouchCurio.canEquip(back),
                    "Enabled Curios callbacks must accept backpacks and pouches in the back slot");
            helper.assertTrue(!backpackCurio.canEquip(wrongSlot) && !pouchCurio.canEquip(wrongSlot),
                    "Curios callbacks must reject other accessory slots");
            helper.assertTrue(stacks.isItemValid(0, backpack) && stacks.isItemValid(0, pouch),
                    "Native Curios slot validation rejected enabled Inmis items");

            Inmis.CONFIG.requireEmptyForUnequip = true;
            helper.assertTrue(backpackCurio.canUnequip(back), "An empty Curios backpack must be removable");
            backpack.set(Inmis.BACKPACK_COMPONENT.get(), new BackpackComponent(List.of(new ItemStack(Items.DIAMOND))));
            helper.assertTrue(!backpackCurio.canUnequip(back),
                    "The empty-only unequip option must protect a filled Curios backpack");

            Inmis.CONFIG.enableTrinketCompatibility = false;
            helper.assertTrue(!backpackCurio.canEquip(back) && !pouchCurio.canEquip(back),
                    "Disabled Curios callbacks must reject backpacks and pouches");
            helper.assertTrue(!stacks.isItemValid(0, backpack) && !stacks.isItemValid(0, pouch),
                    "Curios tags must not bypass the disabled compatibility option");
            helper.assertTrue(backpackCurio.canUnequip(back),
                    "A legacy filled Curios backpack must remain removable when compatibility is disabled");
        }
    }

    private static final class AccessoriesChecks {

        private static void run(GameTestHelper helper, Player player, ItemStack backpack, ItemStack pouch) {
            helper.assertTrue(io.wispforest.accessories.data.EntitySlotLoader.getEntitySlots(player).containsKey("back"),
                    "The native Accessories entity loader did not bind a back slot to the player");
            var capability = io.wispforest.accessories.api.AccessoriesCapability.getOptionally(player)
                    .orElseThrow(() -> new AssertionError("The native Accessories player capability is missing"));
            var backContainer = capability.getContainers().get("back");
            helper.assertTrue(backContainer != null && backContainer.getSize() > 0,
                    "The native Accessories player capability has no usable back container");
            var back = backContainer.createReference(0);
            helper.assertTrue(back.isValid() && back.slotContainer() == backContainer,
                    "The native Accessories player back container does not produce a valid slot reference");
            var wrongSlot = io.wispforest.accessories.api.slot.SlotReference.of(player, "ring", 0);
            var backpackAccessory = io.wispforest.accessories.api.AccessoriesAPI.getOrDefaultAccessory(backpack);
            var pouchAccessory = io.wispforest.accessories.api.AccessoriesAPI.getOrDefaultAccessory(pouch);

            Inmis.CONFIG.enableTrinketCompatibility = true;
            helper.assertTrue(backpackAccessory.canEquip(backpack, back) && pouchAccessory.canEquip(pouch, back),
                    "Enabled Accessories callbacks must accept backpacks and pouches in the back slot");
            helper.assertTrue(!backpackAccessory.canEquip(backpack, wrongSlot)
                            && !pouchAccessory.canEquip(pouch, wrongSlot),
                    "Accessories callbacks must reject other accessory slots");
            helper.assertTrue(io.wispforest.accessories.api.AccessoriesAPI.canInsertIntoSlot(backpack, back)
                            && io.wispforest.accessories.api.AccessoriesAPI.canInsertIntoSlot(pouch, back),
                    "Native Accessories slot validation rejected enabled Inmis items");
            var equippedBackpack = capability.attemptToEquipAccessory(backpack.copy());
            helper.assertTrue(equippedBackpack != null && equippedBackpack.slotContainer() == backContainer
                            && backContainer.getAccessories().getItem(0).is(backpack.getItem()),
                    "Native Accessories equipment admission did not place the backpack in the player back container");
            backContainer.getAccessories().setItem(0, ItemStack.EMPTY);
            var equippedPouch = capability.attemptToEquipAccessory(pouch.copy());
            helper.assertTrue(equippedPouch != null && equippedPouch.slotContainer() == backContainer
                            && backContainer.getAccessories().getItem(0).is(pouch.getItem()),
                    "Native Accessories equipment admission did not place the pouch in the player back container");
            backContainer.getAccessories().setItem(0, ItemStack.EMPTY);

            Inmis.CONFIG.requireEmptyForUnequip = true;
            helper.assertTrue(backpackAccessory.canUnequip(backpack, back),
                    "An empty Accessories backpack must be removable");
            backpack.set(Inmis.BACKPACK_COMPONENT.get(), new BackpackComponent(List.of(new ItemStack(Items.DIAMOND))));
            backContainer.getAccessories().setItem(0, backpack);
            helper.assertTrue(!backpackAccessory.canUnequip(backpack, back),
                    "The empty-only unequip option must protect a filled Accessories backpack");

            Inmis.CONFIG.enableTrinketCompatibility = false;
            helper.assertTrue(!backpackAccessory.canEquip(backpack, back) && !pouchAccessory.canEquip(pouch, back),
                    "Disabled Accessories callbacks must reject backpacks and pouches");
            helper.assertTrue(!io.wispforest.accessories.api.AccessoriesAPI.canInsertIntoSlot(backpack, back)
                            && !io.wispforest.accessories.api.AccessoriesAPI.canInsertIntoSlot(pouch, back),
                    "Accessories slot predicates must not bypass disabled compatibility");
            helper.assertTrue(backpackAccessory.canUnequip(backpack, back),
                    "A legacy filled Accessories backpack must remain removable when compatibility is disabled");
            helper.assertTrue(ItemStack.matches(back.getStack(), backpack),
                    "The filled legacy backpack is missing from the native player back container");
            backContainer.getAccessories().setItem(0, ItemStack.EMPTY);
            helper.assertTrue(capability.attemptToEquipAccessory(backpack.copy()) == null
                            && capability.attemptToEquipAccessory(pouch.copy()) == null
                            && backContainer.getAccessories().getItem(0).isEmpty(),
                    "Native Accessories equipment admission bypassed disabled compatibility into an empty player slot");
        }
    }
}
