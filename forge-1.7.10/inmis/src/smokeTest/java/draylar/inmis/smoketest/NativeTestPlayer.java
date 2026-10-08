package draylar.inmis.smoketest;

import com.mojang.authlib.GameProfile;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.WorldServer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real native server player and death/inventory methods, with an isolated network manager. */
final class NativeTestPlayer extends EntityPlayerMP {
    final List<EntityItem> emitted=new ArrayList<>();
    NativeTestPlayer(WorldServer world){
        super(FMLCommonHandler.instance().getMinecraftServerInstance(),world,new GameProfile(UUID.randomUUID(),"InmisCheck"),new ItemInWorldManager(world));
        playerNetServerHandler=new NetHandlerPlayServer(mcServer,new NetworkManager(false),this);
        setPosition(.5,64,.5);
    }
    io.netty.channel.embedded.EmbeddedChannel enablePacketLoop(){
        io.netty.channel.embedded.EmbeddedChannel channel=new io.netty.channel.embedded.EmbeddedChannel(playerNetServerHandler.netManager);
        playerNetServerHandler.netManager.setConnectionState(net.minecraft.network.EnumConnectionState.PLAY);
        return channel;
    }
    @Override public void joinEntityItemWithWorld(EntityItem item){if(captureDrops)capturedDrops.add(item);else emitted.add(item);}
}
