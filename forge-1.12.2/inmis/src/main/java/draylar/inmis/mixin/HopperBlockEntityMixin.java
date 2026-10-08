package draylar.inmis.mixin;

import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackAugmentHandler;
import draylar.inmis.augment.BackpackAugmentType;
import draylar.inmis.augment.BackpackAugments;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import net.minecraft.util.EnumFacing;
import net.minecraft.inventory.IInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.tileentity.IHopper;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(TileEntityHopper.class)
public class HopperBlockEntityMixin {

    @Inject(method = "getInventoryAtPosition(Lnet/minecraft/world/World;DDD)Lnet/minecraft/inventory/IInventory;", at = @At("RETURN"), cancellable = true)
    private static void inmis$getBackpackContainer(World level, double x, double y, double z, CallbackInfoReturnable<IInventory> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }
        List<EntityPlayer> players = level.getEntitiesWithinAABB(EntityPlayer.class,
                new AxisAlignedBB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5),
                player -> !BackpackAugmentHandler.getBackpackInventoriesWithAugment(player, BackpackAugmentType.HOPPER_BRIDGE).isEmpty());
        if (players.isEmpty()) {
            return;
        }
        EntityPlayer player = players.get(level.rand.nextInt(players.size()));
        List<BackpackInventory> inventories = BackpackAugmentHandler.getBackpackInventoriesWithAugment(player, BackpackAugmentType.HOPPER_BRIDGE);
        if (inventories.isEmpty()) {
            return;
        }
        BackpackInventory inventory = inventories.get(level.rand.nextInt(inventories.size()));
        cir.setReturnValue(inventory);
    }

    @Inject(method = "putStackInInventoryAllSlots(Lnet/minecraft/inventory/IInventory;Lnet/minecraft/inventory/IInventory;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/EnumFacing;)Lnet/minecraft/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private static void inmis$addItemToBackpack(IInventory source, IInventory target, ItemStack stack, EnumFacing face,
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
                net.minecraft.util.ResourceLocation id = stack.getItem().getRegistryName();
                if (id == null || !settings.filters().contains(id)) {
                    cir.setReturnValue(stack);
                }
            }
        }
    }
}
