package draylar.inmis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import draylar.inmis.Inmis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public class CuriosBackpackRenderer implements ICurioRenderer {
    @Override
    public <S extends LivingEntityRenderState, M extends EntityModel<? super S>> void render(
            ItemStack stack, SlotContext slotContext, PoseStack poseStack, SubmitNodeCollector collector,
            int packedLight, S state, RenderLayerParent<S, M> parent, EntityRendererProvider.Context context,
            float yRotation, float xRotation) {
        if (!Inmis.CONFIG.trinketRendering || Minecraft.getInstance().level == null) {
            return;
        }
        ItemStackRenderState backpack = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver()
                .updateForLiving(backpack, stack, ItemDisplayContext.FIXED, slotContext.entity());
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(180));
        poseStack.translate(0, -0.2, -0.25);
        if (slotContext.entity().isCrouching()) {
            poseStack.mulPose(Axis.XP.rotationDegrees(25));
            poseStack.translate(0, -0.2, 0);
        }
        backpack.submit(poseStack, collector, packedLight, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}