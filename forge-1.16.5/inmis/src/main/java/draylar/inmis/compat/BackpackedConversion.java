package draylar.inmis.compat;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.compat.CuriosCompat;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.RegistryObject;

import java.util.List;
import java.util.Optional;

public final class BackpackedConversion {

    public static final ResourceLocation BACKPACKED_ITEM_ID = new ResourceLocation("backpacked", "backpack");

    private BackpackedConversion() {
    }

    public static Optional<BackpackTarget> resolveTarget(String tierName) {
        if (Inmis.CONFIG == null) {
            return Optional.empty();
        }

        List<BackpackInfo> infos = Inmis.CONFIG.backpacks;
        for (int i = 0; i < infos.size(); i++) {
            BackpackInfo info = infos.get(i);
            if (info.getName().equals(tierName)) {
                if (i < Inmis.BACKPACKS.size()) {
                    return Optional.of(new BackpackTarget(info, Inmis.BACKPACKS.get(i)));
                }
                break;
            }
        }

        return Optional.empty();
    }

    public static int convertPlayerInventories(ServerPlayerEntity player, BackpackItem replacement) {
        PlayerInventory inventory = player.inventory;
        int converted = 0;
        converted += convertStacks(inventory.items, replacement);
        converted += convertStacks(inventory.armor, replacement);
        converted += convertStacks(inventory.offhand, replacement);
        converted += convertEnderChest(player.getEnderChestInventory(), replacement);

        if (converted > 0) {
            inventory.setChanged();
        }

        if (Inmis.CURIOS_LOADED && Inmis.CONFIG.enableTrinketCompatibility) {
            converted += CuriosCompat.replaceMatchingStacks(player, BackpackedConversion::isBackpackedStack,
                    stack -> copyStackAsInmis(stack, replacement));
        }

        return converted;
    }

    private static int convertStacks(List<ItemStack> stacks, BackpackItem replacement) {
        int converted = 0;
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (isBackpackedStack(stack)) {
                ItemStack convertedStack = copyStackAsInmis(stack, replacement);
                if (!convertedStack.isEmpty()) {
                    stacks.set(i, convertedStack);
                    converted++;
                }
            }
        }
        return converted;
    }

    private static int convertEnderChest(EnderChestInventory inventory, BackpackItem replacement) {
        int converted = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isBackpackedStack(stack)) {
                ItemStack convertedStack = copyStackAsInmis(stack, replacement);
                if (!convertedStack.isEmpty()) {
                    inventory.setItem(slot, convertedStack);
                    converted++;
                }
            }
        }

        if (converted > 0) {
            inventory.setChanged();
        }

        return converted;
    }

    public static boolean isBackpackedStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return BACKPACKED_ITEM_ID.equals(id);
    }

    private static ItemStack copyStackAsInmis(ItemStack stack, Item replacement) {
        ResourceLocation targetId = ForgeRegistries.ITEMS.getKey(replacement);
        if (targetId == null) {
            return ItemStack.EMPTY;
        }

        CompoundNBT tag = stack.save(new CompoundNBT());
        tag.putString("id", targetId.toString());
        return ItemStack.of(tag);
    }

    public static final class BackpackTarget {
        private final BackpackInfo info;
        private final RegistryObject<BackpackItem> item;
        public BackpackTarget(BackpackInfo info, RegistryObject<BackpackItem> item) {
            this.info = info;
            this.item = item;
        }
        public BackpackInfo info() { return info; }
        public RegistryObject<BackpackItem> item() { return item; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BackpackTarget)) return false;
            BackpackTarget target = (BackpackTarget) other;
            return java.util.Objects.equals(info, target.info) && java.util.Objects.equals(item, target.item);
        }
        @Override public int hashCode() {
            return java.util.Objects.hash(info, item);
        }
        @Override public String toString() {
            return "BackpackTarget[info=" + info + ", item=" + item + "]";
        }
    }
}
