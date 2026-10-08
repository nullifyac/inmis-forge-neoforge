package draylar.inmis.augment;

import draylar.inmis.Inmis;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Inmis.MOD_ID)
public final class BackpackAugmentEvents {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPreItemPickup(EntityItemPickupEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.getEntity() instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) event.getEntity();
            if (BackpackAugmentHandler.beforeItemPickup(player, event.getItem(), null)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityDropLoot(LivingDropsEvent event) {
        if (event.getSource().getEntity() instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) event.getSource().getEntity();
            BackpackAugmentHandler.onLootDroppedByEntity(event.getDrops(), player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.getPlayer() instanceof net.minecraft.entity.player.ServerPlayerEntity) {
            net.minecraft.entity.player.ServerPlayerEntity player = (net.minecraft.entity.player.ServerPlayerEntity) event.getPlayer();
            BackpackAugmentHandler.onBlockBroken(player, event.getState(), event.getPos());
        }
    }

    @SubscribeEvent
    public static void onPickupXp(PlayerXpEvent.PickupXp event) {
        if (event.getEntity() instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) event.getEntity();
            BackpackAugmentHandler.onPlayerPickupExperienceOrb(player, event.getOrb());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player instanceof net.minecraft.entity.player.ServerPlayerEntity) {
            net.minecraft.entity.player.ServerPlayerEntity player = (net.minecraft.entity.player.ServerPlayerEntity) event.player;
            BackpackAugmentHandler.onPlayerTick(player);
        }
    }
}
