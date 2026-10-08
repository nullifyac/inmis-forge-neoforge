package draylar.inmis;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
public class CommonProxy implements IGuiHandler {
    public void preInit() {}
    public void init() {}
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        net.minecraft.item.ItemStack stack = draylar.inmis.network.ServerNetworking.getOpeningStack(player);
        return id == 0 && stack.getItem() instanceof BackpackItem ? new BackpackScreenHandler(0, player.inventory, stack) : null;
    }
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) { return null; }
}
