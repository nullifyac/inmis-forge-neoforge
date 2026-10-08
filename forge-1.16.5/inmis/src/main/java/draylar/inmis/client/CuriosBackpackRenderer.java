package draylar.inmis.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.util.math.vector.Vector3f;
import draylar.inmis.Inmis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

public final class CuriosBackpackRenderer {

    public static void render(ItemStack stack, LivingEntity entity, MatrixStack poseStack,
                              IRenderTypeBuffer buffer, int light) {
        if (!Inmis.CONFIG.trinketRendering) {
            return;
        }

        poseStack.pushPose();
        poseStack.mulPose(Vector3f.XP.rotationDegrees(180));
        poseStack.translate(0, -0.2, -0.25);

        if (entity.isCrouching()) {
            poseStack.mulPose(Vector3f.XP.rotationDegrees(25));
            poseStack.translate(0, -0.2, 0);
        }

        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                ItemCameraTransforms.TransformType.FIXED,
                light,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer);
        poseStack.popPose();
    }
}
