package draylar.inmis.augment;

import draylar.inmis.Inmis;
import draylar.inmis.compat.BaublesCompat;
import draylar.inmis.item.BackpackItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class BackpackAugmentHelper {

    private BackpackAugmentHelper() {
    }

    public static List<ItemStack> getBackpackStacks(EntityPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        InventoryPlayer inventory = player.inventory;
        collectBackpacks(inventory.mainInventory, stacks);
        collectBackpacks(inventory.armorInventory, stacks);
        collectBackpacks(inventory.offHandInventory, stacks);

        if (Inmis.BAUBLES_LOADED && Inmis.CONFIG.enableTrinketCompatibility) {
            stacks.addAll(BaublesCompat.getEquippedBackpacks(player));
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
