package draylar.inmis.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.ResourceLocation;

/** Bounded augment settings protocol for the legacy networking API. */
public final class LegacyBuffer {
    private final ByteBuf buffer;
    public LegacyBuffer(ByteBuf buffer) { this.buffer = buffer; }
    public void writeBoolean(boolean value) { buffer.writeBoolean(value); }
    public boolean readBoolean() { return buffer.readBoolean(); }
    public void writeVarInt(int value) { buffer.writeInt(value); }
    public int readVarInt() { return buffer.readInt(); }
    public void writeEnum(Enum<?> value) { buffer.writeByte(value.ordinal()); }
    public <T extends Enum<T>> T readEnum(Class<T> type) {
        int ordinal = buffer.readUnsignedByte();
        T[] values = type.getEnumConstants();
        if (ordinal >= values.length) throw new IllegalArgumentException("Invalid augment enum");
        return values[ordinal];
    }
    public void writeResourceLocation(ResourceLocation id) {
        byte[] value = id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (value.length > 256) throw new IllegalArgumentException("Filter id too long");
        buffer.writeShort(value.length); buffer.writeBytes(value);
    }
    public ResourceLocation readResourceLocation() {
        int length = buffer.readUnsignedShort();
        if (length > 256 || length > buffer.readableBytes()) throw new IllegalArgumentException("Invalid filter id length");
        byte[] value = new byte[length]; buffer.readBytes(value);
        String id = new String(value, java.nio.charset.StandardCharsets.UTF_8);
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("Invalid filter id");
        return new ResourceLocation(id);
    }
}
