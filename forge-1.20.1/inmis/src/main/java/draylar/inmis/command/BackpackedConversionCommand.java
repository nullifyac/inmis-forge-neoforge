package draylar.inmis.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import draylar.inmis.Inmis;
import draylar.inmis.compat.BackpackedConversion;
import draylar.inmis.compat.BackpackedImportController;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;

import java.util.Collection;

public final class BackpackedConversionCommand {

    private static final SimpleCommandExceptionType IMPORTS_DISABLED = new SimpleCommandExceptionType(
            Component.literal("Enable importBackpackedItems in inmis.json before running this command."));
    private static final DynamicCommandExceptionType UNKNOWN_TIER = new DynamicCommandExceptionType(name ->
            Component.literal("Unknown Inmis backpack tier: " + name));
    private static final Dynamic2CommandExceptionType CAPACITY_TOO_SMALL = new Dynamic2CommandExceptionType((sourceSize, targetSize) ->
            Component.literal("Backpacked size (" + sourceSize + " slots) exceeds selected Inmis tier (" + targetSize + " slots)."));
    private static final SimpleCommandExceptionType NOTHING_CONVERTED = new SimpleCommandExceptionType(
            Component.literal("No Backpacked backpacks were found for the selected players."));

    private static final SuggestionProvider<CommandSourceStack> TIER_SUGGESTIONS = (context, builder) -> {
        if (Inmis.CONFIG != null) {
            for (BackpackInfo info : Inmis.CONFIG.backpacks) {
                builder.suggest(info.getName());
            }
        }
        return builder.buildFuture();
    };

    private BackpackedConversionCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> convert = Commands.literal("convert_backpacked")
            .then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.argument("tier", StringArgumentType.string())
                    .suggests(TIER_SUGGESTIONS)
                    .then(Commands.argument("columns", IntegerArgumentType.integer(1, 15))
                        .then(Commands.argument("rows", IntegerArgumentType.integer(1, 12))
                            .executes(ctx -> execute(ctx, false))
                            .then(Commands.literal("allow_smaller")
                                .executes(ctx -> execute(ctx, true)))))));

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("inmis")
            .requires(source -> source.getEntity() == null || source.hasPermission(2))
            .then(convert)
            .then(Commands.literal("import_backpacked")
                .then(Commands.literal("enable").executes(ctx -> setImportOverride(ctx.getSource(), true)))
                .then(Commands.literal("disable").executes(ctx -> setImportOverride(ctx.getSource(), false)))
                .then(Commands.literal("use_config").executes(ctx -> setImportOverride(ctx.getSource(), null)))
                .then(Commands.literal("status").executes(ctx -> reportImportStatus(ctx.getSource()))));

        dispatcher.register(root);
    }

    private static int execute(CommandContext<CommandSourceStack> context, boolean allowSmaller) throws CommandSyntaxException {
        if (!BackpackedImportController.isImportEnabled()) {
            throw IMPORTS_DISABLED.create();
        }

        CommandSourceStack source = context.getSource();
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        String tierName = StringArgumentType.getString(context, "tier");
        int columns = IntegerArgumentType.getInteger(context, "columns");
        int rows = IntegerArgumentType.getInteger(context, "rows");
        int sourceSlots = Math.max(1, columns * rows);

        BackpackedConversion.BackpackTarget target = BackpackedConversion.resolveTarget(tierName)
            .orElseThrow(() -> UNKNOWN_TIER.create(tierName));
        BackpackInfo tierInfo = target.info();
        BackpackItem targetItem = target.item().get();

        int targetSlots = Math.max(1, tierInfo.getRowWidth() * tierInfo.getNumberOfRows());
        if (!allowSmaller && targetSlots < sourceSlots) {
            throw CAPACITY_TOO_SMALL.create(sourceSlots, targetSlots);
        }

        int convertedStacks = 0;

        for (ServerPlayer player : targets) {
            int convertedForPlayer = BackpackedConversion.convertPlayerInventories(player, targetItem);
            if (convertedForPlayer > 0) {
                player.inventoryMenu.broadcastChanges();
            }
            convertedStacks += convertedForPlayer;
        }

        if (convertedStacks == 0) {
            throw NOTHING_CONVERTED.create();
        }

        int totalConverted = convertedStacks;
        int targetCount = targets.size();
        String selectedTier = tierName;
        source.sendSuccess(() -> Component.literal("Converted " + totalConverted + " backpack(s) for "
            + targetCount + " player(s) using tier '" + selectedTier + "'."), true);
        return convertedStacks;
    }

    private static int setImportOverride(CommandSourceStack source, Boolean override) {
        if (override == null) {
            BackpackedImportController.setOverride(null);
            boolean enabled = BackpackedImportController.isImportEnabled();
            source.sendSuccess(() -> Component.literal("Backpacked import now follows inmis.json (" + stateLabel(enabled) + ")."), true);
        } else {
            BackpackedImportController.setOverride(override);
            source.sendSuccess(() -> Component.literal("Backpacked import override " + stateLabel(override) + "."), true);
        }

        return 1;
    }

    private static int reportImportStatus(CommandSourceStack source) {
        boolean enabled = BackpackedImportController.isImportEnabled();
        boolean overridden = BackpackedImportController.isOverridden();
        String mode = overridden ? "override" : "config";
        source.sendSuccess(() -> Component.literal("Backpacked import is " + stateLabel(enabled) + " (" + mode + ")."), false);
        return enabled ? 1 : 0;
    }

    private static String stateLabel(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }
}
