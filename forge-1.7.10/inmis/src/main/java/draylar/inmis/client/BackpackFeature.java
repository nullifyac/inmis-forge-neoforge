package draylar.inmis.client;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderPlayerEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** The native specials event runs while the player's body transform is active. */
public final class BackpackFeature {
    @SubscribeEvent
    public void render(RenderPlayerEvent.Specials.Post event) {
        EntityPlayer player = event.entityPlayer;
        if (player.isInvisible()) return;
        ItemStack backpack = player.inventory.armorItemInSlot(2);
        if ((backpack == null || !(backpack.getItem() instanceof BackpackItem)) && draylar.inmis.compat.BaublesCompat.isLoaded()
                && Inmis.CONFIG.enableTrinketCompatibility && Inmis.CONFIG.trinketRendering) {
            backpack = draylar.inmis.compat.BaublesCompat.findFirstEquippedBackpack(player);
        }
        if (backpack != null && backpack.getItem() instanceof BackpackItem) BackpackRenderer.render(backpack, player);
    }
}
