package draylar.inmis;

import cpw.mods.fml.common.network.IGuiHandler;
import draylar.inmis.network.ServerNetworking;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public class CommonProxy implements IGuiHandler {
    public void preInit(){ }
    public void init(){ }
    public Object getServerGuiElement(int id,EntityPlayer player,World world,int x,int y,int z){
        return id==0?ServerNetworking.createServerMenu(player):id==1?player.getInventoryEnderChest():null;
    }
    public Object getClientGuiElement(int id,EntityPlayer player,World world,int x,int y,int z){return null;}
    public void receiveOpening(ServerNetworking.OpeningData data){ServerNetworking.setClientOpening(data);}
}
