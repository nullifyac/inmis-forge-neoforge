package draylar.inmis.compat;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class BackpackedDataImporter {

    private static final String LEGACY_KEY = "Items";

    private BackpackedDataImporter() {
    }

    public static ListTag tryImport(ItemStack stack, BackpackInfo info) {
        if (!BackpackedImportController.isImportEnabled()) {
            return null;
        }

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(LEGACY_KEY, Tag.TAG_LIST)) {
            return null;
        }

        int size = Math.max(0, info.getRowWidth() * info.getNumberOfRows());
        if (size == 0) {
            tag.remove(LEGACY_KEY);
            return null;
        }

        ListTag legacy = tag.getList(LEGACY_KEY, Tag.TAG_COMPOUND);
        ListTag converted = new ListTag();
        boolean hadEntries = !legacy.isEmpty();
        boolean populated = false;

        for (int i = 0; i < legacy.size(); i++) {
            CompoundTag entry = legacy.getCompound(i);
            int slot = resolveSlot(entry, i);
            if (slot < 0 || slot >= size) {
                Inmis.LOGGER.debug("Skipping Backpacked slot {} outside bounds {} for {}", slot, size, stack.getHoverName().getString());
                continue;
            }

            ItemStack imported = ItemStack.of(entry.copy());
            if (imported.isEmpty()) {
                continue;
            }

            CompoundTag stackTag = new CompoundTag();
            stackTag.putInt("Slot", slot);
            stackTag.put("Stack", imported.save(new CompoundTag()));
            converted.add(stackTag);
            populated = true;
        }

        tag.remove(LEGACY_KEY);

        if (populated) {
            Inmis.LOGGER.info("Imported Backpacked contents into {}", stack.getHoverName().getString());
        } else if (hadEntries) {
            Inmis.LOGGER.debug("Backpacked data on {} was empty after conversion", stack.getHoverName().getString());
        }

        return converted;
    }

    private static int resolveSlot(CompoundTag entry, int fallback) {
        if (entry.contains("Slot", Tag.TAG_BYTE)) {
            return entry.getByte("Slot") & 255;
        }

        if (entry.contains("Slot", Tag.TAG_INT)) {
            return entry.getInt("Slot");
        }

        return fallback;
    }
}
