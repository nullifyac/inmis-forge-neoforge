package draylar.inmis.compat;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.compat.BaublesCompat;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.InventoryEnderChest;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import draylar.inmis.LegacyRegistryEntry;

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

    public static int convertPlayerInventories(EntityPlayerMP player, BackpackItem replacement) {
        InventoryPlayer inventory = player.inventory;
        int converted = 0;
        converted += convertStacks(inventory.mainInventory, replacement);
        converted += convertStacks(inventory.armorInventory, replacement);
        converted += convertStacks(inventory.offHandInventory, replacement);
        converted += convertEnderChest(player.getInventoryEnderChest(), replacement);

        if (converted > 0) {
            inventory.markDirty();
        }

        if (Inmis.BAUBLES_LOADED && Inmis.CONFIG.enableTrinketCompatibility) {
            converted += BaublesCompat.replaceMatchingStacks(player, BackpackedConversion::isBackpackedStack,
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

    private static int convertEnderChest(InventoryEnderChest inventory, BackpackItem replacement) {
        int converted = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (isBackpackedStack(stack)) {
                ItemStack convertedStack = copyStackAsInmis(stack, replacement);
                if (!convertedStack.isEmpty()) {
                    inventory.setInventorySlotContents(slot, convertedStack);
                    converted++;
                }
            }
        }

        if (converted > 0) {
            inventory.markDirty();
        }

        return converted;
    }

    public static boolean isBackpackedStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation id = stack.getItem().getRegistryName();
        return BACKPACKED_ITEM_ID.equals(id);
    }

    private static ItemStack copyStackAsInmis(ItemStack stack, Item replacement) {
        ResourceLocation targetId = replacement.getRegistryName();
        if (targetId == null) {
            return ItemStack.EMPTY;
        }

        NBTTagCompound tag = stack.writeToNBT(new NBTTagCompound());
        tag.setString("id", targetId.toString());
        return new ItemStack(tag);
    }

    public static final class BackpackTarget {
        private final BackpackInfo info;
        private final LegacyRegistryEntry<BackpackItem> item;
        public BackpackTarget(BackpackInfo info, LegacyRegistryEntry<BackpackItem> item) {
            this.info = info;
            this.item = item;
        }
        public BackpackInfo info() { return info; }
        public LegacyRegistryEntry<BackpackItem> item() { return item; }
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
