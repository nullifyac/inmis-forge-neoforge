package draylar.inmis.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.util.math.vector.Vector3f;
import draylar.inmis.item.BackpackItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.IEntityRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;

public class BackpackFeature extends LayerRenderer<AbstractClientPlayerEntity, PlayerModel<AbstractClientPlayerEntity>> {

    public BackpackFeature(IEntityRenderer<AbstractClientPlayerEntity, PlayerModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLight, AbstractClientPlayerEntity player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack chestSlot = player.getItemBySlot(EquipmentSlotType.CHEST);
        if (chestSlot.getItem() instanceof BackpackItem) {
            poseStack.pushPose();

            poseStack.mulPose(Vector3f.XP.rotationDegrees(180));
            poseStack.translate(0, -0.2, -0.25);

            if (player.isCrouching()) {
                poseStack.mulPose(Vector3f.XP.rotationDegrees(25));
                poseStack.translate(0, -0.2, 0);
            }

            Minecraft.getInstance().getItemRenderer().renderStatic(
                    chestSlot,
                    ItemCameraTransforms.TransformType.FIXED,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    bufferSource);
            poseStack.popPose();
        }
    }
}
