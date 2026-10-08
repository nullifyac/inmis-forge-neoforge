package draylar.inmis.compat;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import top.theillusivec4.curios.api.event.CurioDropsEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class CuriosCompat {

    private static final String BACK_SLOT = "back";
    private static boolean registered = false;

    private static final ICurioItem BACKPACK_CURIO = new ICurioItem() {
        @Override
        public boolean canEquip(SlotContext slotContext, ItemStack stack) {
            return Inmis.CONFIG.enableTrinketCompatibility && BACK_SLOT.equals(slotContext.identifier());
        }

        @Override
        public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
            if (Inmis.CONFIG.enableTrinketCompatibility
                    && stack.getItem() instanceof BackpackItem && Inmis.CONFIG.requireEmptyForUnequip) {
                return Inmis.isBackpackEmpty(stack);
            }
            return true;
        }
    };

    private static final ICurioItem ENDER_POUCH_CURIO = new ICurioItem() {
        @Override
        public boolean canEquip(SlotContext slotContext, ItemStack stack) {
            return Inmis.CONFIG.enableTrinketCompatibility && BACK_SLOT.equals(slotContext.identifier());
        }
    };

    private CuriosCompat() {
    }

    public static void registerCurios() {
        if (registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CurioDropsEvent.class, CuriosCompat::spillCurioDrops);

        for (var backpack : Inmis.BACKPACKS) {
            CuriosApi.registerCurio(backpack.get(), BACKPACK_CURIO);
        }
        CuriosApi.registerCurio(Inmis.ENDER_POUCH.get(), ENDER_POUCH_CURIO);
        CuriosApi.registerCurioPredicate(Inmis.id("backpack"), slotResult ->
                Inmis.CONFIG.enableTrinketCompatibility && BACK_SLOT.equals(slotResult.slotContext().identifier())
                        && (slotResult.stack().getItem() instanceof BackpackItem || slotResult.stack().getItem() == Inmis.ENDER_POUCH.get()));
    }

    public static ItemStack findFirstEquippedBackpack(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.findFirstCurio(stack -> stack.getItem() instanceof BackpackItem))
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }

    public static List<ItemStack> getEquippedBackpacks(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> {
                    List<SlotResult> curios = handler.findCurios(stack -> stack.getItem() instanceof BackpackItem);
                    List<ItemStack> stacks = new ArrayList<>(curios.size());
                    for (SlotResult result : curios) {
                        stacks.add(result.stack());
                    }
                    return stacks;
                })
                .orElse(List.of());
    }

    public static boolean tryEquipBackpack(Player player, ItemStack stack) {
        if (!Inmis.CONFIG.enableTrinketCompatibility) {
            return false;
        }
        if (player.level().isClientSide()) {
            return false;
        }
        if (stack.isEmpty()) {
            return false;
        }
        if (!(stack.getItem() instanceof BackpackItem) && stack.getItem() != Inmis.ENDER_POUCH.get()) {
            return false;
        }

        return CuriosApi.getCuriosInventory(player)
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

    private static void spillCurioDrops(CurioDropsEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || !Inmis.CONFIG.enableTrinketCompatibility || !Inmis.CONFIG.spillArmorBackpacksOnDeath) {
            return;
        }

        // Curios has already selected native drops and applied KEEP, DESTROY and Vanishing rules.
        // Never inspect retained equipped slots or remove item-equal drops from another inventory.
        for (ItemEntity backpackDrop : List.copyOf(event.getDrops())) {
            ItemStack stack = backpackDrop.getItem();
            if (!(stack.getItem() instanceof BackpackItem)) {
                continue;
            }
            for (ItemStack contents : Inmis.getBackpackContents(stack)) {
                if (!contents.isEmpty()) {
                    ItemEntity contentDrop = new ItemEntity(player.level(),
                            backpackDrop.getX(), backpackDrop.getY(), backpackDrop.getZ(), contents);
                    contentDrop.setPickUpDelay(40);
                    contentDrop.setDeltaMovement(backpackDrop.getDeltaMovement());
                    event.getDrops().add(contentDrop);
                }
            }
            Inmis.wipeBackpack(stack);
            backpackDrop.setItem(stack);
        }
    }

    public static int replaceMatchingStacks(Player player, Predicate<ItemStack> matcher, UnaryOperator<ItemStack> converter) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> {
                    List<SlotResult> curios = handler.findCurios(matcher::test);
                    int converted = 0;
                    for (SlotResult result : curios) {
                        ItemStack replacement = converter.apply(result.stack());
                        if (replacement != null && !replacement.isEmpty()) {
                            handler.setEquippedCurio(result.slotContext().identifier(), result.slotContext().index(), replacement);
                            converted++;
                        }
                    }
                    return converted;
                })
                .orElse(0);
    }
}
