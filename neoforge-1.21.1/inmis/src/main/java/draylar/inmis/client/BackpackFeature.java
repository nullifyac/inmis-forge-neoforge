package draylar.inmis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import draylar.inmis.item.BackpackItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class BackpackFeature extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public BackpackFeature(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> context) {
        super(context);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack chestSlot = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chestSlot.getItem() instanceof BackpackItem)) {
            return;
        }

        if (Minecraft.getInstance().level == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(180));
        poseStack.translate(0, -0.2, -0.25);

        if (player.isCrouching()) {
            poseStack.mulPose(Axis.XP.rotationDegrees(25));
            poseStack.translate(0, -0.2, 0);
        }

        Minecraft.getInstance().getItemRenderer().renderStatic(
                chestSlot,
                ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                player.level(),
                0);
        poseStack.popPose();
    }
}
