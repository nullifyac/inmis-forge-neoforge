package draylar.inmis.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/** Shares the same item model and dye tint for chest and optional BODY equipment. */
public final class BackpackRenderer {
    private BackpackRenderer() {
    }

    public static void render(ItemStack stack, EntityPlayer player) {
        GlStateManager.pushMatrix();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.rotate(180, 1, 0, 0);
        GlStateManager.translate(0, -0.2, -0.25);
        if (player.isSneaking()) {
            GlStateManager.rotate(25, 1, 0, 0);
            GlStateManager.translate(0, -0.2, 0);
        }
        Minecraft.getMinecraft().getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.FIXED);
        GlStateManager.popMatrix();
    }
}
