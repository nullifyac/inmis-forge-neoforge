package draylar.inmis.compat;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.component.BackpackComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BackpackedDataImporter {

    private static final String ITEMS_KEY = "Items";

    private BackpackedDataImporter() {
    }

    public static BackpackComponent tryImport(ItemStack stack, BackpackInfo info) {
        if (!BackpackedImportController.isImportEnabled()) {
            return null;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }

        CompoundTag tag = customData.copyTag();
        if (!tag.contains(ITEMS_KEY, Tag.TAG_LIST)) {
            return null;
        }

        int size = info.getRowWidth() * info.getNumberOfRows();
        if (size <= 0) {
            return null;
        }

        List<ItemStack> slots = createEmptyList(size);
        ListTag list = tag.getList(ITEMS_KEY, Tag.TAG_COMPOUND);
        boolean populatedSlot = false;
        boolean hadEntries = list.size() > 0;
        HolderLookup.Provider registries = resolveRegistries();
        if (registries == null) {
            Inmis.LOGGER.warn("Skipping Backpacked import for {} because no registry access is available yet", stack.getHoverName().getString());
            return null;
        }

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.contains("Slot", Tag.TAG_BYTE) ? entry.getByte("Slot") & 255 : i;
            if (slot < 0 || slot >= size) {
                Inmis.LOGGER.debug("Skipping Backpacked slot {} for {} because it exceeds size {}", slot, stack.getHoverName().getString(), size);
                continue;
            }

            Optional<ItemStack> parsed = ItemStack.parse(registries, entry);
            ItemStack importedStack = parsed.orElse(ItemStack.EMPTY);
            if (!importedStack.isEmpty()) {
                slots.set(slot, importedStack);
                populatedSlot = true;
            }
        }

        tag.remove(ITEMS_KEY);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }

        if (populatedSlot) {
            Inmis.LOGGER.info("Imported Backpacked data into {}", stack.getHoverName().getString());
        } else if (hadEntries) {
            Inmis.LOGGER.debug("Backpacked data in {} was empty after conversion", stack.getHoverName().getString());
        }

        return new BackpackComponent(slots);
    }

    private static HolderLookup.Provider resolveRegistries() {
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            return ServerLifecycleHooks.getCurrentServer().registryAccess();
        }

        if (FMLEnvironment.dist == Dist.CLIENT) {
            return getClientRegistryAccess();
        }

        return null;
    }

    @OnlyIn(Dist.CLIENT)
    private static HolderLookup.Provider getClientRegistryAccess() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            return minecraft.level.registryAccess();
        }

        ClientPacketListener connection = minecraft.getConnection();
        return connection != null ? connection.registryAccess() : null;
    }

    private static List<ItemStack> createEmptyList(int size) {
        List<ItemStack> slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            slots.add(ItemStack.EMPTY);
        }
        return slots;
    }
}
