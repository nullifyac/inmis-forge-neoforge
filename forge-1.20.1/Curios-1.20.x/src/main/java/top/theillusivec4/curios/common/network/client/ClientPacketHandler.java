package top.theillusivec4.curios.common.network.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import top.theillusivec4.curios.common.inventory.container.CuriosContainerV2;
import top.theillusivec4.curios.common.network.server.SPacketQuickMove;

public class ClientPacketHandler {

  // For some reason this is the only packet that causes class-loading issues on a server
  // todo: Refactor other client packets into this class for best practice
  public static void handlePacket(SPacketQuickMove msg) {
    Minecraft mc = Minecraft.getInstance();
    LocalPlayer player = mc.player;

    if (player != null && player.containerMenu instanceof CuriosContainerV2 container) {
      container.quickMoveStack(player, msg.moveIndex);
    }
  }
}
