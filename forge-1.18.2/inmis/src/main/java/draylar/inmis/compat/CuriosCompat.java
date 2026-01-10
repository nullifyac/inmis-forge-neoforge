package draylar.inmis.compat;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.common.capability.CurioItemCapability;
import top.theillusivec4.curios.common.capability.ItemizedCurioCapability;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

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
        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, CuriosCompat::attachCapabilities);
    }

    private static void attachCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();
        if (stack.getItem() instanceof BackpackItem) {
            attachCurio(event, BACKPACK_CURIO);
        } else if (stack.getItem() == Inmis.ENDER_POUCH.get()) {
            attachCurio(event, ENDER_POUCH_CURIO);
        }
    }

    private static void attachCurio(AttachCapabilitiesEvent<ItemStack> event, ICurioItem curioItem) {
        ItemStack stack = event.getObject();
        if (!curioItem.hasCurioCapability(stack)) {
            return;
        }

        event.addCapability(CuriosCapability.ID_ITEM,
                CurioItemCapability.createProvider(new ItemizedCurioCapability(curioItem, stack)));
    }

    public static ItemStack findFirstEquippedBackpack(Player player) {
        return CuriosApi.getCuriosHelper()
                .findFirstCurio(player, stack -> stack.getItem() instanceof BackpackItem)
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }

    public static List<ItemStack> getEquippedBackpacks(Player player) {
        List<SlotResult> curios = CuriosApi.getCuriosHelper()
                .findCurios(player, stack -> stack.getItem() instanceof BackpackItem);
        List<ItemStack> stacks = new java.util.ArrayList<>(curios.size());
        for (SlotResult result : curios) {
            stacks.add(result.stack());
        }
        return stacks;
    }

    public static boolean tryEquipBackpack(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (!(stack.getItem() instanceof BackpackItem) && stack.getItem() != Inmis.ENDER_POUCH.get()) {
            return false;
        }

        return CuriosApi.getCuriosHelper().getCuriosHandler(player).resolve()
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
        CuriosApi.getCuriosHelper().getCuriosHandler(player).ifPresent(handler -> {
            List<SlotResult> curios = CuriosApi.getCuriosHelper()
                    .findCurios(player, stack -> stack.getItem() instanceof BackpackItem);
            for (SlotResult result : curios) {
                ItemStack stack = result.stack();
                ItemStack original = stack.copy();
                event.getDrops().removeIf(drop -> ItemStack.isSameItemSameTags(drop.getItem(), original));

                for (ItemStack contents : Inmis.getBackpackContents(stack)) {
                    if (!contents.isEmpty()) {
                        event.getDrops().add(new ItemEntity(player.level, player.getX(), player.getY(), player.getZ(), contents));
                    }
                }

                Inmis.wipeBackpack(stack);
                event.getDrops().add(new ItemEntity(player.level, player.getX(), player.getY(), player.getZ(), stack.copy()));
                CuriosApi.getCuriosHelper().setEquippedCurio(player, result.slotContext().identifier(), result.slotContext().index(), ItemStack.EMPTY);
            }
        });
    }

    public static int replaceMatchingStacks(Player player, Predicate<ItemStack> matcher, UnaryOperator<ItemStack> converter) {
        return CuriosApi.getCuriosHelper().getCuriosHandler(player)
                .map(handler -> {
                    List<SlotResult> curios = CuriosApi.getCuriosHelper().findCurios(player, matcher::test);
                    int converted = 0;
                    for (SlotResult result : curios) {
                        ItemStack replacement = converter.apply(result.stack());
                        if (replacement != null && !replacement.isEmpty()) {
                            CuriosApi.getCuriosHelper().setEquippedCurio(player,
                                    result.slotContext().identifier(), result.slotContext().index(), replacement);
                            converted++;
                        }
                    }
                    return converted;
                })
                .orElse(0);
    }

}
