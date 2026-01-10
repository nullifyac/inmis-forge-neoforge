package draylar.inmis.augment;

import draylar.inmis.Inmis;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingGetProjectileEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = Inmis.MOD_ID)
public final class BackpackAugmentEvents {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPreItemPickup(ItemEntityPickupEvent.Pre event) {
        if (event.canPickup().isFalse()) {
            return;
        }
        if (BackpackAugmentHandler.beforeItemPickup(event.getPlayer(), event.getItemEntity(), event.getItemEntity().getTarget())) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityDropLoot(LivingDropsEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            BackpackAugmentHandler.onLootDroppedByEntity(event.getDrops(), player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockDropLoot(BlockDropsEvent event) {
        Entity breaker = event.getBreaker();
        if (breaker instanceof net.minecraft.server.level.ServerPlayer player) {
            BackpackAugmentHandler.onLootDroppedByBlock(event.getDrops(), player);
            BackpackAugmentHandler.onBlockBroken(player, event.getState(), event.getPos());
        }
    }

    @SubscribeEvent
    public static void onGetProjectile(LivingGetProjectileEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack ammo = BackpackAugmentHandler.locateAmmunition(player, event.getProjectileWeaponItemStack(), event.getProjectileItemStack());
            if (!ammo.isEmpty()) {
                event.setProjectileItemStack(ammo);
            }
        }
    }

    @SubscribeEvent
    public static void onPickupXp(PlayerXpEvent.PickupXp event) {
        if (event.getEntity() instanceof Player player) {
            BackpackAugmentHandler.onPlayerPickupExperienceOrb(player, event.getOrb());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            BackpackAugmentHandler.onPlayerTick(player);
        }
    }
}
