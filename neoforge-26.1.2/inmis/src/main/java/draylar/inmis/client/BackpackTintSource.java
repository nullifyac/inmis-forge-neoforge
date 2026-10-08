package draylar.inmis.client;

import com.mojang.serialization.MapCodec;
import draylar.inmis.item.DyeableBackpackItem;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.jspecify.annotations.Nullable;

/** Keeps model tinting consistent with the configured dyeable backpack tiers. */
public final class BackpackTintSource implements ItemTintSource {
    public static final MapCodec<BackpackTintSource> CODEC = MapCodec.unit(new BackpackTintSource());

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        return stack.getItem() instanceof DyeableBackpackItem
                ? DyedItemColor.getOrDefault(stack, DyedItemColor.LEATHER_COLOR) : -1;
    }

    @Override
    public MapCodec<BackpackTintSource> type() {
        return CODEC;
    }
}
