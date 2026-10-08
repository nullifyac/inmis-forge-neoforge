package draylar.inmis.mixin;

import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackAugmentHandler;
import draylar.inmis.augment.BackpackAugmentType;
import draylar.inmis.augment.BackpackAugments;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import net.minecraft.util.Direction;
import net.minecraft.inventory.IInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.tileentity.IHopper;
import net.minecraft.tileentity.HopperTileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(HopperTileEntity.class)
public class HopperBlockEntityMixin {

    @Inject(method = "getContainerAt(Lnet/minecraft/world/World;DDD)Lnet/minecraft/inventory/IInventory;", at = @At("RETURN"), cancellable = true)
    private static void inmis$getBackpackContainer(World level, double x, double y, double z, CallbackInfoReturnable<IInventory> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }
        List<PlayerEntity> players = level.getEntitiesOfClass(PlayerEntity.class,
                new AxisAlignedBB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5),
                player -> !BackpackAugmentHandler.getBackpackInventoriesWithAugment(player, BackpackAugmentType.HOPPER_BRIDGE).isEmpty());
        if (players.isEmpty()) {
            return;
        }
        PlayerEntity player = players.get(level.random.nextInt(players.size()));
        List<BackpackInventory> inventories = BackpackAugmentHandler.getBackpackInventoriesWithAugment(player, BackpackAugmentType.HOPPER_BRIDGE);
        if (inventories.isEmpty()) {
            return;
        }
        BackpackInventory inventory = inventories.get(level.random.nextInt(inventories.size()));
        cir.setReturnValue(inventory);
    }

    @Inject(method = "addItem(Lnet/minecraft/inventory/IInventory;Lnet/minecraft/inventory/IInventory;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Direction;)Lnet/minecraft/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private static void inmis$addItemToBackpack(IInventory source, IInventory target, ItemStack stack, Direction face,
                                               CallbackInfoReturnable<ItemStack> cir) {
        if (target instanceof BackpackInventory) {
            BackpackInventory inventory = (BackpackInventory) target;
            if (!(source instanceof IHopper)) {
                cir.setReturnValue(stack);
                return;
            }
            ItemStack backpackStack = inventory.getBackpackStack();
            if (!(backpackStack.getItem() instanceof BackpackItem)) {
                return;
            }
            BackpackItem backpackItem = (BackpackItem) backpackStack.getItem();
            draylar.inmis.config.BackpackInfo tier = backpackItem.getTier();
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
                net.minecraft.util.ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id == null || !settings.filters().contains(id)) {
                    cir.setReturnValue(stack);
                }
            }
        }
    }
}
