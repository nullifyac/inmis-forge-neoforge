package draylar.inmis.compat;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.List;
import java.util.Optional;

public final class CuriosCompat {

    private static final String BACK_SLOT = "back";

    private static final ICurioItem BACKPACK_CURIO = new ICurioItem() {
        @Override
        public boolean canEquip(SlotContext slotContext, ItemStack stack) {
            return BACK_SLOT.equals(slotContext.identifier());
        }

        @Override
        public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
            if (stack.getItem() instanceof BackpackItem && Inmis.CONFIG.requireEmptyForUnequip) {
                return Inmis.isBackpackEmpty(stack);
            }
            return true;
        }
    };

    private static final ICurioItem ENDER_POUCH_CURIO = new ICurioItem() {
        @Override
        public boolean canEquip(SlotContext slotContext, ItemStack stack) {
            return BACK_SLOT.equals(slotContext.identifier());
        }
    };

    private CuriosCompat() {
    }

    public static void registerCurios() {
        for (RegistryObject<BackpackItem> backpack : Inmis.BACKPACKS) {
            CuriosApi.registerCurio(backpack.get(), BACKPACK_CURIO);
        }
        CuriosApi.registerCurio(Inmis.ENDER_POUCH.get(), ENDER_POUCH_CURIO);
    }

    public static ItemStack findFirstEquippedBackpack(Player player) {
        LazyOptional<ICuriosItemHandler> handlerOptional = CuriosApi.getCuriosInventory(player);
        Optional<ICuriosItemHandler> handler = handlerOptional.resolve();
        if (handler.isEmpty()) {
            return ItemStack.EMPTY;
        }

        return handler.get()
                .findFirstCurio(stack -> stack.getItem() instanceof BackpackItem)
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }

    public static boolean tryEquipBackpack(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (!(stack.getItem() instanceof BackpackItem) && stack.getItem() != Inmis.ENDER_POUCH.get()) {
            return false;
        }

        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(handler -> handler.getStacksHandler(BACK_SLOT))
                .map(ICurioStacksHandler::getStacks)
                .map(stacks -> tryInsertIntoBackSlot(stacks, stack))
                .orElse(false);
    }

    private static boolean tryInsertIntoBackSlot(IDynamicStackHandler stacks, ItemStack stack) {
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (stacks.getStackInSlot(i).isEmpty()) {
                ItemStack toInsert = stack.copy();
                toInsert.setCount(1);
                ItemStack remainder = stacks.insertItem(i, toInsert, false);
                if (remainder.isEmpty()) {
                    stack.shrink(1);
                    return true;
                }
            }
        }

        return false;
    }

    public static void spillCurios(Player player, LivingDropsEvent event) {
        CuriosApi.getCuriosInventory(player).resolve().ifPresent(handler -> {
            List<SlotResult> curios = handler.findCurios(stack -> stack.getItem() instanceof BackpackItem);
            for (SlotResult result : curios) {
                ItemStack stack = result.stack();
                ItemStack original = stack.copy();
                event.getDrops().removeIf(drop -> ItemStack.isSameItemSameTags(drop.getItem(), original));

                for (ItemStack contents : Inmis.getBackpackContents(stack)) {
                    if (!contents.isEmpty()) {
                        event.getDrops().add(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), contents));
                    }
                }

                Inmis.wipeBackpack(stack);
                event.getDrops().add(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack.copy()));
                handler.setEquippedCurio(result.slotContext().identifier(), result.slotContext().index(), ItemStack.EMPTY);
            }
        });
    }
}
