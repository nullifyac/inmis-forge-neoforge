package draylar.inmis.compat;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

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
            return null;
        }

        ListTag legacy = (ListTag) tag.get(LEGACY_KEY);
        if (!legacy.isEmpty() && legacy.getElementType() != Tag.TAG_COMPOUND) {
            return null;
        }
        ListTag converted = new ListTag();
        boolean hadEntries = !legacy.isEmpty();
        boolean populated = false;
        Set<Integer> occupiedSlots = new HashSet<>();

        for (int i = 0; i < legacy.size(); i++) {
            CompoundTag entry = legacy.getCompound(i);
            int slot = resolveSlot(entry, i);
            if (slot < 0 || slot >= Short.MAX_VALUE - 36) {
                Inmis.LOGGER.warn("Preserving Backpacked data on {} because slot {} cannot be represented by a backpack menu", stack.getHoverName().getString(), slot);
                return null;
            }

            ItemStack imported = ItemStack.of(entry.copy());
            if (imported.isEmpty()) {
                if (entry.isEmpty() || "minecraft:air".equals(entry.getString("id"))
                        || (entry.contains("Count", Tag.TAG_ANY_NUMERIC) && entry.getInt("Count") <= 0)) {
                    continue;
                }
                Inmis.LOGGER.warn("Preserving Backpacked data on {} because an item could not be decoded", stack.getHoverName().getString());
                return null;
            }
            if (!occupiedSlots.add(slot)) {
                Inmis.LOGGER.warn("Preserving Backpacked data on {} because slot {} contains multiple items", stack.getHoverName().getString(), slot);
                return null;
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
