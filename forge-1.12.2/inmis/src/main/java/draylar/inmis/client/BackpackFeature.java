package draylar.inmis.client;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;

public final class BackpackFeature implements LayerRenderer<AbstractClientPlayer> {
    public BackpackFeature(RenderPlayer renderer) {
    }

    @Override
    public void doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTicks,
                              float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        if (player.isInvisible()) return;
        ItemStack backpack = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (!(backpack.getItem() instanceof BackpackItem) && Inmis.BAUBLES_LOADED
                && Inmis.CONFIG.enableTrinketCompatibility && Inmis.CONFIG.trinketRendering) {
            backpack = draylar.inmis.compat.BaublesCompat.findFirstEquippedBackpack(player);
        }
        if (backpack.getItem() instanceof BackpackItem) BackpackRenderer.render(backpack, player);
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
