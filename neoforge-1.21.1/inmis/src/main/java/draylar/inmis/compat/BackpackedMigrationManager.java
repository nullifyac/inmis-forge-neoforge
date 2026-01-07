package draylar.inmis.compat;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.compat.BackpackedImportController;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Optional;

public final class BackpackedMigrationManager {

    private static final Component SUCCESS_MESSAGE = Component.literal("[Inmis] Migrated your Backpacked data.")
            .withStyle(ChatFormatting.GREEN);

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

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!BackpackedImportController.isImportEnabled()) {
            if (!warnedImporterDisabled) {
                warnedImporterDisabled = true;
                Inmis.LOGGER.warn("Automatic Backpacked migration is enabled but importBackpackedItems is currently disabled. Run /inmis import_backpacked enable or update your config.");
            }
            return;
        }

        Optional<BackpackedConversion.BackpackTarget> targetOptional =
                BackpackedConversion.resolveTarget(Inmis.CONFIG.autoBackpackedTier);
        if (targetOptional.isEmpty()) {
            if (!warnedMissingTier) {
                warnedMissingTier = true;
                Inmis.LOGGER.warn("Automatic Backpacked migration skipped because tier '{}' is not defined in inmis.json.",
                        Inmis.CONFIG.autoBackpackedTier);
            }
            return;
        }

        BackpackInfo tierInfo = targetOptional.get().info();
        BackpackItem targetItem = targetOptional.get().itemSupplier().get();

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
            player.sendSystemMessage(SUCCESS_MESSAGE);
        }
    }

    private static boolean shouldAutoMigrate() {
        return Inmis.CONFIG != null && Inmis.CONFIG.importBackpackedItems;
    }
}
