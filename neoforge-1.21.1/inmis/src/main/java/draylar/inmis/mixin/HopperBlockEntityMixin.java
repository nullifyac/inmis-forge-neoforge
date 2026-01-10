package draylar.inmis.mixin;

import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackAugmentHandler;
import draylar.inmis.augment.BackpackAugmentType;
import draylar.inmis.augment.BackpackAugments;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(HopperBlockEntity.class)
public class HopperBlockEntityMixin {

    @Inject(method = "getEntityContainer", at = @At("HEAD"), cancellable = true)
    private static void inmis$getBackpackContainer(Level level, double x, double y, double z, CallbackInfoReturnable<Container> cir) {
        List<Player> players = level.getEntitiesOfClass(Player.class,
                new AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5),
                player -> !BackpackAugmentHandler.getBackpackInventoriesWithAugment(player, BackpackAugmentType.HOPPER_BRIDGE).isEmpty());
        if (players.isEmpty()) {
            return;
        }
        Player player = players.get(level.random.nextInt(players.size()));
        List<BackpackInventory> inventories = BackpackAugmentHandler.getBackpackInventoriesWithAugment(player, BackpackAugmentType.HOPPER_BRIDGE);
        if (inventories.isEmpty()) {
            return;
        }
        BackpackInventory inventory = inventories.get(level.random.nextInt(inventories.size()));
        cir.setReturnValue(inventory);
    }

    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private static void inmis$addItemToBackpack(Container source, Container target, ItemStack stack, Direction face,
                                               CallbackInfoReturnable<ItemStack> cir) {
        if (target instanceof BackpackInventory inventory) {
            if (!(source instanceof Hopper)) {
                cir.setReturnValue(stack);
                return;
            }
            ItemStack backpackStack = inventory.getBackpackStack();
            if (!(backpackStack.getItem() instanceof BackpackItem backpackItem)) {
                return;
            }
            var tier = backpackItem.getTier();
            if (!BackpackAugments.isUnlocked(tier, BackpackAugmentType.HOPPER_BRIDGE)) {
                return;
            }
            BackpackAugmentsComponent augments = Inmis.getOrCreateAugments(backpackStack, tier);
            BackpackAugmentsComponent.HopperBridgeSettings settings = augments.hopperBridge();
            if (!settings.enabled() || !settings.insert()) {
                cir.setReturnValue(stack);
                return;
            }
            if (settings.filterMode().checkInsert()) {
                var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id == null || !settings.filters().contains(id)) {
                    cir.setReturnValue(stack);
                }
            }
        }
    }
}
