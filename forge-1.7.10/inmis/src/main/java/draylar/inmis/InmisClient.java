package draylar.inmis;

import draylar.inmis.client.BackpackFeature;
import draylar.inmis.client.InmisKeybinds;
import draylar.inmis.network.ServerNetworking.OpeningData;
import draylar.inmis.network.ServerNetworking;
import draylar.inmis.ui.BackpackHandledScreen;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

/** Kept behind the sided proxy so dedicated servers never load client classes. */
public class InmisClient extends CommonProxy {
    @Override
    public void preInit() {
        InmisKeybinds.register();
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new InmisKeybinds());
        MinecraftForge.EVENT_BUS.register(new BackpackFeature());
    }

    @Override
    public void init() {
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != 0) return null;
        OpeningData opening = ServerNetworking.consumeClientOpening();
        if (opening == null) return null;
        BackpackScreenHandler menu = new BackpackScreenHandler(player.inventory, opening);
        return new BackpackHandledScreen(menu, player.inventory, new ChatComponentText(menu.getBackpackStack().getDisplayName()));
    }
}
