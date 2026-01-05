package top.theillusivec4.curios.common.network.server.sync;

import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.theillusivec4.curios.common.data.CuriosEntityManager;
import top.theillusivec4.curios.common.data.CuriosSlotManager;

public class SPacketSyncData {

  private final ListTag slotData;
  private final ListTag entityData;

  public SPacketSyncData(ListTag slotData, ListTag entityData) {
    this.slotData = slotData;
    this.entityData = entityData;
  }

  public static void encode(SPacketSyncData msg, FriendlyByteBuf buf) {
    CompoundTag tag = new CompoundTag();
    tag.put("SlotData", msg.slotData);
    tag.put("EntityData", msg.entityData);
    buf.writeNbt(tag);
  }

  public static SPacketSyncData decode(FriendlyByteBuf buf) {
    CompoundTag tag = buf.readNbt();

    if (tag != null) {
      return new SPacketSyncData(tag.getList("SlotData", Tag.TAG_COMPOUND), tag.getList("EntityData", Tag.TAG_COMPOUND));
    }
    return new SPacketSyncData(new ListTag(), new ListTag());
  }

  public static void handle(SPacketSyncData msg, Supplier<NetworkEvent.Context> ctx) {
    ctx.get().enqueueWork(() -> {
      CuriosSlotManager.applySyncPacket(msg.slotData);
      CuriosEntityManager.applySyncPacket(msg.entityData);
    });
    ctx.get().setPacketHandled(true);
  }
}
