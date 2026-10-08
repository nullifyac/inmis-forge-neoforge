package draylar.inmis.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** Uses the native icon extrusion and item tint for chest and optional Baubles equipment. */
public final class BackpackRenderer {
    private BackpackRenderer() {
    }

    public static void render(ItemStack stack, EntityPlayer player) {
        IIcon icon = stack.getItem().getIcon(stack, 0);
        if (icon == null) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glRotatef(180, 1, 0, 0);
            GL11.glTranslatef(0, -0.2F, -0.25F);
            if (player.isSneaking()) {
                GL11.glRotatef(25, 1, 0, 0);
                GL11.glTranslatef(0, -0.2F, 0);
            }
            GL11.glTranslatef(-0.5F, -0.5F, 0);
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationItemsTexture);
            int color = stack.getItem().getColorFromItemStack(stack, 0);
            GL11.glColor4f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, 1);
            ItemRenderer.renderItemIn2D(Tessellator.instance, icon.getMaxU(), icon.getMinV(), icon.getMinU(), icon.getMaxV(),
                    icon.getIconWidth(), icon.getIconHeight(), 1F / 16F);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
