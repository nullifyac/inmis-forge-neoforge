package draylar.inmis.compat;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.Optional;

public final class BackpackedMigrationManager {

    private static final ITextComponent SUCCESS_MESSAGE = new TextComponentString("[Inmis] Migrated your Backpacked data.")
            .setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.GREEN));

    private static boolean warnedMissingTier;
    private static boolean warnedCapacity;
    private static boolean warnedImporterDisabled;

    private BackpackedMigrationManager() {
    }

    public static void bootstrapFromConfig() {
        BackpackedImportController.setOverride(null);
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!shouldAutoMigrate()) {
            return;
        }

        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;

        if (!BackpackedImportController.isImportEnabled()) {
            if (!warnedImporterDisabled) {
                warnedImporterDisabled = true;
                Inmis.LOGGER.warn("Automatic Backpacked migration is enabled but importBackpackedItems is currently disabled. Run /inmis import_backpacked enable or update your config.");
            }
            return;
        }

        Optional<BackpackedConversion.BackpackTarget> targetOptional =
                BackpackedConversion.resolveTarget(Inmis.CONFIG.autoBackpackedTier);
        if (!targetOptional.isPresent()) {
            if (!warnedMissingTier) {
                warnedMissingTier = true;
                Inmis.LOGGER.warn("Automatic Backpacked migration skipped because tier '{}' is not defined in inmis.json.",
                        Inmis.CONFIG.autoBackpackedTier);
            }
            return;
        }

        BackpackInfo tierInfo = targetOptional.get().info();
        BackpackItem targetItem = targetOptional.get().item().get();

        int sourceSlots = Math.max(1, Inmis.CONFIG.autoBackpackedColumns * Inmis.CONFIG.autoBackpackedRows);
        int targetSlots = Math.max(1, tierInfo.getRowWidth() * tierInfo.getNumberOfRows());

        if (!Inmis.CONFIG.autoBackpackedAllowSmaller && targetSlots < sourceSlots) {
            if (!warnedCapacity) {
                warnedCapacity = true;
                Inmis.LOGGER.warn("Automatic Backpacked migration requires at least {} slots but tier '{}' only offers {}. Increase your target tier or enable autoBackpackedAllowSmaller.",
                        sourceSlots, tierInfo.getName(), targetSlots);
            }
            return;
        }

        int converted = BackpackedConversion.convertPlayerInventories(player, targetItem);
        if (converted > 0) {
            player.sendMessage(SUCCESS_MESSAGE);
        }
    }

    private static boolean shouldAutoMigrate() {
        return Inmis.CONFIG != null && Inmis.CONFIG.importBackpackedItems;
    }
}
