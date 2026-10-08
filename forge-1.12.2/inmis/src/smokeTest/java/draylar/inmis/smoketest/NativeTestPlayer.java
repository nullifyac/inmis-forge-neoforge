package draylar.inmis.smoketest;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.world.WorldServer;
import net.minecraft.network.*;
import net.minecraft.util.DamageSource;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
/** A real vanilla server-player damage/death path with an unconnected packet sink. */
public class NativeTestPlayer extends EntityPlayerMP {
    public NativeTestPlayer(WorldServer world, String name) {
        super(world.getMinecraftServer(),world,new GameProfile(UUID.randomUUID(),name),new PlayerInteractionManager(world));
        this.connection = new NetHandlerPlayServer(world.getMinecraftServer(),new NetworkManager(EnumPacketDirection.SERVERBOUND),this);
        net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(EntityPlayerMP.class,this,0,"respawnInvulnerabilityTicks","field_147101_bU");
        capabilities.disableDamage=false; capabilities.isCreativeMode=false;
        interactionManager.setGameType(net.minecraft.world.GameType.SURVIVAL);
    }
    @Override public boolean isEntityInvulnerable(DamageSource source) { return false; }
}
