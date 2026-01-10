package draylar.inmis.network;

import draylar.inmis.item.component.BackpackAugmentsComponent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record UpdateBackpackAugmentsPayload(BackpackAugmentsComponent augments) implements CustomPacketPayload {

    public static final Type<UpdateBackpackAugmentsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("inmis", "update_backpack_augments"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateBackpackAugmentsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BackpackAugmentsComponent.STREAM_CODEC, UpdateBackpackAugmentsPayload::augments,
                    UpdateBackpackAugmentsPayload::new
            );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
