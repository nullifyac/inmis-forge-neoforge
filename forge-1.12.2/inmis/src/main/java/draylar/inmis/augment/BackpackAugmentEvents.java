package draylar.inmis.augment;

import draylar.inmis.Inmis;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerPickupXpEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class BackpackAugmentEvents {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void harvest(net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent event) {
        EntityPlayer player = event.getHarvester();
        if (player == null || player.world.isRemote || !BackpackAugmentHandler.hasLootboundBackpacks(player)) return;
        java.util.List<net.minecraft.entity.item.EntityItem> drops = new java.util.ArrayList<>();
        for (net.minecraft.item.ItemStack stack : event.getDrops()) if (player.world.rand.nextFloat() <= event.getDropChance()) drops.add(new net.minecraft.entity.item.EntityItem(player.world,event.getPos().getX()+0.5,event.getPos().getY()+0.5,event.getPos().getZ()+0.5,stack));
        BackpackAugmentHandler.onLootDroppedByBlock(drops,player);
        event.getDrops().clear(); for (net.minecraft.entity.item.EntityItem drop : drops) event.getDrops().add(drop.getItem()); event.setDropChance(1);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPreItemPickup(EntityItemPickupEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.getEntity() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntity();
            if (BackpackAugmentHandler.beforeItemPickup(player, event.getItem(), null)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityDropLoot(LivingDropsEvent event) {
        if (event.getSource().getTrueSource() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getSource().getTrueSource();
            BackpackAugmentHandler.onLootDroppedByEntity(event.getDrops(), player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.getPlayer() instanceof net.minecraft.entity.player.EntityPlayerMP) {
            net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) event.getPlayer();
            BackpackAugmentHandler.onBlockBroken(player, event.getState(), event.getPos());
        }
    }

    @SubscribeEvent
    public void onPickupXp(PlayerPickupXpEvent event) {
        if (event.getEntity() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntity();
            BackpackAugmentHandler.onPlayerPickupExperienceOrb(player, event.getOrb());
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) BackpackAugmentHandler.finishServerTick();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player instanceof net.minecraft.entity.player.EntityPlayerMP) {
            net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) event.player;
            BackpackAugmentHandler.onPlayerTick(player);
        }
    }
}
