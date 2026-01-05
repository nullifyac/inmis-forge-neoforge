package draylar.inmis.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import draylar.inmis.Inmis;
import draylar.inmis.api.Dimension;
import draylar.inmis.api.Rectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;

public class BackpackHandledScreen extends AbstractContainerScreen<BackpackScreenHandler> {

    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation("inmis", "textures/gui/backpack_container.png");
    private static final ResourceLocation SLOT_TEXTURE = new ResourceLocation("inmis", "textures/gui/backpack_slot.png");

    private final int guiTitleColor = Integer.decode(Inmis.CONFIG.guiTitleColor);

    public BackpackHandledScreen(BackpackScreenHandler handler, Inventory player, Component title) {
        super(handler, player, handler.getBackpackStack().getHoverName());

        Dimension dimension = handler.getDimension();
        this.imageWidth = dimension.getWidth();
        this.imageHeight = dimension.getHeight();
        this.titleLabelY = 7;
        this.inventoryLabelX = handler.getPlayerInvSlotPosition(dimension, 0, 0).x;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(PoseStack poseStack, float delta, int mouseX, int mouseY) {
        RenderSystem.clearColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = this.leftPos;
        int y = this.topPos;
        renderBackgroundTexture(poseStack, new Rectangle(x, y, imageWidth, imageHeight), delta, 0xFFFFFFFF);
        RenderSystem.setShaderTexture(0, SLOT_TEXTURE);
        for (Slot slot : getMenu().slots) {
            blit(poseStack, x + slot.x - 1, y + slot.y - 1, 0, 0, 18, 18, 18, 18);
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float delta) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, delta);
        this.renderTooltip(poseStack, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        this.font.draw(poseStack, title, titleLabelX, titleLabelY, guiTitleColor);
        this.font.draw(poseStack, playerInventoryTitle, inventoryLabelX, inventoryLabelY, guiTitleColor);
    }

    public void renderBackgroundTexture(PoseStack poseStack, Rectangle bounds, float delta, int color) {
        float alpha = ((color >> 24) & 0xFF) / 255f;
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;
        RenderSystem.clearColor(red, green, blue, alpha);
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);
        int x = bounds.x, y = bounds.y, width = bounds.width, height = bounds.height;
        int xTextureOffset = 0;
        int yTextureOffset = 66;

        blit(poseStack, x, y, 106 + xTextureOffset, 124 + yTextureOffset, 8, 8, 256, 256);
        blit(poseStack, x + width - 8, y, 248 + xTextureOffset, 124 + yTextureOffset, 8, 8, 256, 256);
        blit(poseStack, x, y + height - 8, 106 + xTextureOffset, 182 + yTextureOffset, 8, 8, 256, 256);
        blit(poseStack, x + width - 8, y + height - 8, 248 + xTextureOffset, 182 + yTextureOffset, 8, 8, 256, 256);

        drawTexturedQuad(poseStack, GUI_TEXTURE, x + 8, x + width - 8, y, y + 8, getZOffset(),
                (114 + xTextureOffset) / 256f, (248 + xTextureOffset) / 256f,
                (124 + yTextureOffset) / 256f, (132 + yTextureOffset) / 256f);
        drawTexturedQuad(poseStack, GUI_TEXTURE, x + 8, x + width - 8, y + height - 8, y + height, getZOffset(),
                (114 + xTextureOffset) / 256f, (248 + xTextureOffset) / 256f,
                (182 + yTextureOffset) / 256f, (190 + yTextureOffset) / 256f);
        drawTexturedQuad(poseStack, GUI_TEXTURE, x, x + 8, y + 8, y + height - 8, getZOffset(),
                (106 + xTextureOffset) / 256f, (114 + xTextureOffset) / 256f,
                (132 + yTextureOffset) / 256f, (182 + yTextureOffset) / 256f);
        drawTexturedQuad(poseStack, GUI_TEXTURE, x + width - 8, x + width, y + 8, y + height - 8, getZOffset(),
                (248 + xTextureOffset) / 256f, (256 + xTextureOffset) / 256f,
                (132 + yTextureOffset) / 256f, (182 + yTextureOffset) / 256f);

        drawTexturedQuad(poseStack, GUI_TEXTURE, x + 8, x + width - 8, y + 8, y + height - 8, getZOffset(),
                (114 + xTextureOffset) / 256f, (248 + xTextureOffset) / 256f,
                (132 + yTextureOffset) / 256f, (182 + yTextureOffset) / 256f);
    }

    private int getZOffset() {
        return 0;
    }

    private static void drawTexturedQuad(PoseStack poseStack, ResourceLocation texture, int x1, int x2, int y1, int y2, int z,
                                         float u1, float u2, float v1, float v2) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        var matrix4f = poseStack.last().pose();
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferBuilder.vertex(matrix4f, x1, y1, z).uv(u1, v1).endVertex();
        bufferBuilder.vertex(matrix4f, x1, y2, z).uv(u1, v2).endVertex();
        bufferBuilder.vertex(matrix4f, x2, y2, z).uv(u2, v2).endVertex();
        bufferBuilder.vertex(matrix4f, x2, y1, z).uv(u2, v1).endVertex();
        bufferBuilder.end();
        BufferUploader.end(bufferBuilder);
    }
}
