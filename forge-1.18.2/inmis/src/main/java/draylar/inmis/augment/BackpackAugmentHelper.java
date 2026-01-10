package draylar.inmis.augment;

import draylar.inmis.Inmis;
import draylar.inmis.compat.CuriosCompat;
import draylar.inmis.item.BackpackItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class BackpackAugmentHelper {

    private BackpackAugmentHelper() {
    }

    public static List<ItemStack> getBackpackStacks(Player player) {
        List<ItemStack> stacks = new ArrayList<>();
        Inventory inventory = player.getInventory();
        collectBackpacks(inventory.items, stacks);
        collectBackpacks(inventory.armor, stacks);
        collectBackpacks(inventory.offhand, stacks);

        if (Inmis.CURIOS_LOADED && Inmis.CONFIG.enableTrinketCompatibility) {
            stacks.addAll(CuriosCompat.getEquippedBackpacks(player));
        }

        return stacks;
    }

    private static void collectBackpacks(List<ItemStack> items, List<ItemStack> target) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && stack.getItem() instanceof BackpackItem) {
                target.add(stack);
            }
        }
    }
}
