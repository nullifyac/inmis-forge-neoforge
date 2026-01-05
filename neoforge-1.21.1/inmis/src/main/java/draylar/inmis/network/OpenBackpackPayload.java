package draylar.inmis.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record OpenBackpackPayload() implements CustomPacketPayload {

    public static final Type<OpenBackpackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("inmis", "open_backpack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBackpackPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public @NotNull OpenBackpackPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new OpenBackpackPayload();
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, OpenBackpackPayload value) {
                }
            };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
