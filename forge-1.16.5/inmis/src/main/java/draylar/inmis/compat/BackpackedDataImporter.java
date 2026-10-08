package draylar.inmis.compat;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

public final class BackpackedDataImporter {

    private static final String LEGACY_KEY = "Items";

    private BackpackedDataImporter() {
    }

    public static ListNBT tryImport(ItemStack stack, BackpackInfo info) {
        if (!BackpackedImportController.isImportEnabled()) {
            return null;
        }

        CompoundNBT tag = stack.getTag();
        if (tag == null || !tag.contains(LEGACY_KEY, 9)) {
            return null;
        }

        int size = Math.max(0, info.getRowWidth() * info.getNumberOfRows());
        if (size == 0) {
            return null;
        }

        ListNBT legacy = (ListNBT) tag.get(LEGACY_KEY);
        if (!legacy.isEmpty() && legacy.getElementType() != 10) {
            return null;
        }
        ListNBT converted = new ListNBT();
        boolean hadEntries = !legacy.isEmpty();
        boolean populated = false;
        Set<Integer> occupiedSlots = new HashSet<>();

        for (int i = 0; i < legacy.size(); i++) {
            CompoundNBT entry = legacy.getCompound(i);
            int slot = resolveSlot(entry, i);
            if (slot < 0 || slot >= Short.MAX_VALUE - 36) {
                Inmis.LOGGER.warn("Preserving Backpacked data on {} because slot {} cannot be represented by a backpack menu", stack.getHoverName().getString(), slot);
                return null;
            }

            ItemStack imported = ItemStack.of(entry.copy());
            if (imported.isEmpty()) {
                if (entry.isEmpty() || "minecraft:air".equals(entry.getString("id"))
                        || (entry.contains("Count", 99) && entry.getInt("Count") <= 0)) {
                    continue;
                }
                Inmis.LOGGER.warn("Preserving Backpacked data on {} because an item could not be decoded", stack.getHoverName().getString());
                return null;
            }
            if (!occupiedSlots.add(slot)) {
                Inmis.LOGGER.warn("Preserving Backpacked data on {} because slot {} contains multiple items", stack.getHoverName().getString(), slot);
                return null;
            }

            CompoundNBT stackTag = new CompoundNBT();
            stackTag.putInt("Slot", slot);
            stackTag.put("Stack", imported.save(new CompoundNBT()));
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

    private static int resolveSlot(CompoundNBT entry, int fallback) {
        if (entry.contains("Slot", 1)) {
            return entry.getByte("Slot") & 255;
        }

        if (entry.contains("Slot", 3)) {
            return entry.getInt("Slot");
        }

        return fallback;
    }
}
