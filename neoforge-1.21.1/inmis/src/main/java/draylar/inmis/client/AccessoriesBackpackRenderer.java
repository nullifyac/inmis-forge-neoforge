package draylar.inmis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import draylar.inmis.Inmis;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.client.DefaultAccessoryRenderer;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class AccessoriesBackpackRenderer implements AccessoryRenderer {

    @Override
    public <M extends LivingEntity> void render(
            ItemStack stack,
            SlotReference reference,
            PoseStack matrices,
            EntityModel<M> model,
            MultiBufferSource multiBufferSource,
            int light,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        DefaultAccessoryRenderer.INSTANCE.render(stack, reference, matrices, model, multiBufferSource, light,
                limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
    }

    @Override
    public boolean shouldRender(boolean isRendering) {
        return isRendering && Inmis.CONFIG.trinketRendering;
    }
}
