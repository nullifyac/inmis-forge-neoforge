package draylar.inmis.gametest;

import draylar.inmis.Inmis;
import draylar.inmis.config.InmisConfig;
import draylar.inmis.item.component.BackpackComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.List;

public final class InmisCompatibilityGameTests {

    private InmisCompatibilityGameTests() {
    }

    @GameTest(template = "empty")
    public static void runtimeProfileMatchesLoadedMods(GameTestHelper helper) {
        String profile = System.getProperty("inmis.test.compatProfile", "none");
        helper.assertTrue(List.of("none", "curios").contains(profile),
                "Unknown compatibility test profile: " + profile);
        helper.assertTrue(Inmis.CURIOS_LOADED == profile.equals("curios"),
                "Curios runtime presence does not match requested profile " + profile);
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
            helper.succeed();
        } finally {
            Inmis.CONFIG = previousConfig;
        }
    }

    private static final class CuriosChecks {

        private static void run(GameTestHelper helper, Player player, ItemStack backpack, ItemStack pouch) {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).orElseThrow().loadDatapacks();
            var back = new top.theillusivec4.curios.api.SlotContext("back", player, 0, false, true);
            var wrongSlot = new top.theillusivec4.curios.api.SlotContext("ring", player, 0, false, true);
            var backpackCurio = top.theillusivec4.curios.api.CuriosApi.getCurio(backpack).orElseThrow();
            var pouchCurio = top.theillusivec4.curios.api.CuriosApi.getCurio(pouch).orElseThrow();
            var stacks = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .flatMap(handler -> handler.getStacksHandler("back"))
                    .orElseThrow().getStacks();

            Inmis.CONFIG.enableTrinketCompatibility = true;
            helper.assertTrue(backpack.is(TagKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("curios", "back"))),
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

}
