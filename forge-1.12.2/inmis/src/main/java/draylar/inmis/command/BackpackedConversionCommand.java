package draylar.inmis.command;
import draylar.inmis.Inmis;
import draylar.inmis.compat.*;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import java.util.*;
public final class BackpackedConversionCommand extends CommandBase {
    public String getName() { return "inmis"; }
    public String getUsage(ICommandSender sender) { return "/inmis import_backpacked <enable|disable|use_config|status> or /inmis convert_backpacked <targets> <tier> <columns> <rows> [allow_smaller]"; }
    public int getRequiredPermissionLevel() { return 2; }
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 2 && args[0].equals("import_backpacked")) {
            if (args[1].equals("enable")) BackpackedImportController.setOverride(true);
            else if (args[1].equals("disable")) BackpackedImportController.setOverride(false);
            else if (args[1].equals("use_config")) BackpackedImportController.setOverride(null);
            else if (!args[1].equals("status")) throw new WrongUsageException(getUsage(sender));
            sender.sendMessage(new TextComponentString("Backpacked import is " + (BackpackedImportController.isImportEnabled() ? "enabled" : "disabled") + (BackpackedImportController.isOverridden() ? " (override)." : " (config)."))); return;
        }
        if (args.length < 5 || args.length > 6 || !args[0].equals("convert_backpacked")) throw new WrongUsageException(getUsage(sender));
        if (!BackpackedImportController.isImportEnabled()) throw new CommandException("Enable importBackpackedItems first.");
        BackpackedConversion.BackpackTarget target = BackpackedConversion.resolveTarget(args[2]).orElseThrow(() -> new IllegalArgumentException("Unknown tier: " + args[2]));
        int source = parseInt(args[3],1,15) * parseInt(args[4],1,12);
        boolean allowSmaller = args.length == 6 && args[5].equals("allow_smaller");
        if (args.length == 6 && !allowSmaller) throw new WrongUsageException(getUsage(sender));
        if (!allowSmaller && target.info().getRowWidth() * target.info().getNumberOfRows() < source) throw new CommandException("Selected backpack capacity is smaller than the supplied source size.");
        List<EntityPlayerMP> players = getPlayers(server,sender,args[1]); int count = 0;
        for (EntityPlayerMP player : players) { count += BackpackedConversion.convertPlayerInventories(player,target.item().get()); player.inventoryContainer.detectAndSendChanges(); }
        if (count == 0) throw new CommandException("No Backpacked backpacks were found.");
        sender.sendMessage(new TextComponentString("Converted " + count + " backpack(s)."));
    }
}
