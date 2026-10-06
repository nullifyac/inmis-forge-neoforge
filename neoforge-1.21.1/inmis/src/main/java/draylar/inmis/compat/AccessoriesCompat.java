package draylar.inmis.compat;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.events.OnDeathCallback;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import io.wispforest.accessories.api.slot.SlotReference;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class AccessoriesCompat {

    private static final String BACK_SLOT = "back";
    private static final ResourceLocation BACKPACK_PREDICATE = Inmis.id("backpack");
    private static boolean registered = false;

    private static final Accessory BACKPACK_ACCESSORY = new Accessory() {
        @Override
        public boolean canEquip(ItemStack stack, SlotReference reference) {
            return Inmis.CONFIG.enableTrinketCompatibility && BACK_SLOT.equals(reference.slotName());
        }

        @Override
        public boolean canUnequip(ItemStack stack, SlotReference reference) {
            if (Inmis.CONFIG.enableTrinketCompatibility
                    && stack.getItem() instanceof BackpackItem && Inmis.CONFIG.requireEmptyForUnequip) {
                return Inmis.isBackpackEmpty(stack);
            }
            return Accessory.super.canUnequip(stack, reference);
        }

        @Override
        public boolean canEquipFromUse(ItemStack stack) {
            return Inmis.CONFIG.enableTrinketCompatibility && Inmis.CONFIG.requireArmorTrinketToOpen;
        }
    };

    private static final Accessory ENDER_POUCH_ACCESSORY = new Accessory() {
        @Override
        public boolean canEquip(ItemStack stack, SlotReference reference) {
            return Inmis.CONFIG.enableTrinketCompatibility && BACK_SLOT.equals(reference.slotName());
        }

        @Override
        public boolean canEquipFromUse(ItemStack stack) {
            return false;
        }
    };

    private AccessoriesCompat() {
    }

    public static void registerAccessories() {
        if (registered) {
            return;
        }
        registered = true;
        var spillPhase = Inmis.id("spill_backpacks");
        OnDeathCallback.EVENT.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, spillPhase);
        OnDeathCallback.EVENT.register(spillPhase, (state, entity, capability, source, drops) -> {
            if (state != TriState.FALSE && entity instanceof Player player && !player.level().isClientSide
                    && Inmis.CONFIG.enableTrinketCompatibility && Inmis.CONFIG.spillArmorBackpacksOnDeath) {
                spillDroppedAccessories(drops);
            }
            return TriState.DEFAULT;
        });

        AccessoriesAPI.registerPredicate(BACKPACK_PREDICATE, (level, slotType, slot, stack) -> {
            if (stack.getItem() instanceof BackpackItem || stack.getItem() == Inmis.ENDER_POUCH.get()) {
                return Inmis.CONFIG.enableTrinketCompatibility && BACK_SLOT.equals(slotType.name())
                        ? TriState.TRUE : TriState.FALSE;
            }
            return TriState.DEFAULT;
        });

        for (var backpack : Inmis.BACKPACKS) {
            AccessoriesAPI.registerAccessory(backpack.get(), BACKPACK_ACCESSORY);
        }
        AccessoriesAPI.registerAccessory(Inmis.ENDER_POUCH.get(), ENDER_POUCH_ACCESSORY);
    }

    public static ItemStack findFirstEquippedBackpack(Player player) {
        var capability = AccessoriesCapability.get(player);
        if (capability == null) {
            return ItemStack.EMPTY;
        }

        var entry = capability.getFirstEquipped(stack -> stack.getItem() instanceof BackpackItem);
        return entry != null ? entry.stack() : ItemStack.EMPTY;
    }

    public static List<ItemStack> getEquippedBackpacks(Player player) {
        var capability = AccessoriesCapability.get(player);
        if (capability == null) {
            return List.of();
        }

        List<SlotEntryReference> equipped = capability.getEquipped(stack -> stack.getItem() instanceof BackpackItem);
        if (equipped.isEmpty()) {
            return List.of();
        }

        List<ItemStack> stacks = new ArrayList<>(equipped.size());
        for (SlotEntryReference entry : equipped) {
            stacks.add(entry.stack());
        }
        return stacks;
    }

    public static boolean tryEquipBackpack(Player player, ItemStack stack) {
        if (!Inmis.CONFIG.enableTrinketCompatibility) {
            return false;
        }
        if (player.level().isClientSide) {
            return false;
        }
        if (stack.isEmpty()) {
            return false;
        }
        if (!(stack.getItem() instanceof BackpackItem) && stack.getItem() != Inmis.ENDER_POUCH.get()) {
            return false;
        }

        var capability = AccessoriesCapability.get(player);
        if (capability == null) {
            return false;
        }

        ItemStack toEquip = stack.copy();
        toEquip.setCount(1);

        if (capability.attemptToEquipAccessory(toEquip) == null) {
            return false;
        }

        stack.shrink(1);
        return true;
    }

    private static void spillDroppedAccessories(List<ItemStack> drops) {
        // The native death callback supplies only stacks selected for dropping, after its own rules.
        for (ItemStack stack : List.copyOf(drops)) {
            if (stack.getItem() instanceof BackpackItem) {
                for (ItemStack contents : Inmis.getBackpackContents(stack)) {
                    if (!contents.isEmpty()) {
                        drops.add(contents);
                    }
                }
                Inmis.wipeBackpack(stack);
            }
        }
    }

    public static int replaceMatchingStacks(Player player, Predicate<ItemStack> matcher, UnaryOperator<ItemStack> converter) {
        var capability = AccessoriesCapability.get(player);
        if (capability == null) {
            return 0;
        }

        int converted = 0;
        List<SlotEntryReference> equipped = capability.getEquipped(matcher);
        for (SlotEntryReference entry : equipped) {
            ItemStack replacement = converter.apply(entry.stack());
            if (replacement != null && !replacement.isEmpty()) {
                entry.reference().setStack(replacement);
                converted++;
            }
        }
        return converted;
    }
}
