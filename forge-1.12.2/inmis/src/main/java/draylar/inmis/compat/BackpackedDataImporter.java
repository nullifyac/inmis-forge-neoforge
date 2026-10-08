package draylar.inmis.compat;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

public final class BackpackedDataImporter {

    private static final String LEGACY_KEY = "Items";

    private BackpackedDataImporter() {
    }

    public static NBTTagList tryImport(ItemStack stack, BackpackInfo info) {
        if (!BackpackedImportController.isImportEnabled()) {
            return null;
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(LEGACY_KEY, 9)) {
            return null;
        }

        int size = Math.max(0, info.getRowWidth() * info.getNumberOfRows());
        if (size == 0) {
            return null;
        }

        NBTTagList legacy = (NBTTagList) tag.getTag(LEGACY_KEY);
        if (!(legacy.tagCount() == 0) && legacy.getTagType() != 10) {
            return null;
        }
        NBTTagList converted = new NBTTagList();
        boolean hadEntries = !(legacy.tagCount() == 0);
        boolean populated = false;
        Set<Integer> occupiedSlots = new HashSet<>();

        for (int i = 0; i < legacy.tagCount(); i++) {
            NBTTagCompound entry = legacy.getCompoundTagAt(i);
            int slot = resolveSlot(entry, i);
            if (slot < 0 || slot >= Short.MAX_VALUE - 36) {
                Inmis.LOGGER.warn("Preserving Backpacked data on {} because slot {} cannot be represented by a backpack menu", stack.getDisplayName(), slot);
                return null;
            }

            ItemStack imported = new ItemStack(entry.copy());
            if (imported.isEmpty()) {
                if (entry.hasNoTags() || "minecraft:air".equals(entry.getString("id"))
                        || (entry.hasKey("Count", 99) && entry.getInteger("Count") <= 0)) {
                    continue;
                }
                Inmis.LOGGER.warn("Preserving Backpacked data on {} because an item could not be decoded", stack.getDisplayName());
                return null;
            }
            if (!occupiedSlots.add(slot)) {
                Inmis.LOGGER.warn("Preserving Backpacked data on {} because slot {} contains multiple items", stack.getDisplayName(), slot);
                return null;
            }

            NBTTagCompound stackTag = new NBTTagCompound();
            stackTag.setInteger("Slot", slot);
            stackTag.setTag("Stack", imported.writeToNBT(new NBTTagCompound()));
            converted.appendTag(stackTag);
            populated = true;
        }

        tag.removeTag(LEGACY_KEY);

        if (populated) {
            Inmis.LOGGER.info("Imported Backpacked contents into {}", stack.getDisplayName());
        } else if (hadEntries) {
            Inmis.LOGGER.debug("Backpacked data on {} was empty after conversion", stack.getDisplayName());
        }

        return converted;
    }

    private static int resolveSlot(NBTTagCompound entry, int fallback) {
        if (entry.hasKey("Slot", 1)) {
            return entry.getByte("Slot") & 255;
        }

        if (entry.hasKey("Slot", 3)) {
            return entry.getInteger("Slot");
        }

        return fallback;
    }
}
