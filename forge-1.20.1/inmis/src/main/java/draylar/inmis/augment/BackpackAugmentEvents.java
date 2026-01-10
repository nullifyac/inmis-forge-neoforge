package draylar.inmis.augment;

import draylar.inmis.Inmis;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
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
        if (BackpackAugmentHandler.beforeItemPickup(event.getEntity(), event.getItem(), null)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityDropLoot(LivingDropsEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            BackpackAugmentHandler.onLootDroppedByEntity(event.getDrops(), player);
        }
    }

    @SubscribeEvent
    public static void onPickupXp(PlayerXpEvent.PickupXp event) {
        BackpackAugmentHandler.onPlayerPickupExperienceOrb(event.getEntity(), event.getOrb());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player instanceof net.minecraft.server.level.ServerPlayer player) {
            BackpackAugmentHandler.onPlayerTick(player);
        }
    }
}
