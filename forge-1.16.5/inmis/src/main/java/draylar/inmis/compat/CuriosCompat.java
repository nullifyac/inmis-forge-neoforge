package draylar.inmis.compat;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import net.minecraftforge.eventbus.api.EventPriority;
import top.theillusivec4.curios.api.event.CurioDropsEvent;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
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
    private static boolean registered = false;

    private static final ICurioItem BACKPACK_CURIO = new ICurioItem() {
        @Override
        public boolean canEquip(String identifier, net.minecraft.entity.LivingEntity entity, ItemStack stack) {
            return Inmis.CONFIG.enableTrinketCompatibility && BACK_SLOT.equals(identifier);
        }

        @Override
        public boolean canUnequip(String identifier, net.minecraft.entity.LivingEntity entity, ItemStack stack) {
            if (Inmis.CONFIG.enableTrinketCompatibility
                    && stack.getItem() instanceof BackpackItem && Inmis.CONFIG.requireEmptyForUnequip) {
                return Inmis.isBackpackEmpty(stack);
            }
            return true;
        }

        @Override
        public boolean canRender(String identifier, int index, net.minecraft.entity.LivingEntity entity, ItemStack stack) {
            return Inmis.CONFIG.enableTrinketCompatibility && Inmis.CONFIG.trinketRendering && BACK_SLOT.equals(identifier);
        }

        @Override
        public void render(String identifier, int index, com.mojang.blaze3d.matrix.MatrixStack poseStack,
                           net.minecraft.client.renderer.IRenderTypeBuffer buffer, int light,
                           net.minecraft.entity.LivingEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, ItemStack stack) {
            draylar.inmis.client.CuriosBackpackRenderer.render(stack, entity, poseStack, buffer, light);
        }
    };

    private static final ICurioItem ENDER_POUCH_CURIO = new ICurioItem() {
        @Override
        public boolean canEquip(String identifier, net.minecraft.entity.LivingEntity entity, ItemStack stack) {
            return Inmis.CONFIG.enableTrinketCompatibility && BACK_SLOT.equals(identifier);
        }
    };

    private CuriosCompat() {
    }

    public static void registerBackSlot() {
        net.minecraftforge.fml.InterModComms.sendTo("curios",
                top.theillusivec4.curios.api.SlotTypeMessage.REGISTER_TYPE,
                () -> top.theillusivec4.curios.api.SlotTypePreset.BACK.getMessageBuilder().build());
    }

    public static void registerCurios() {
        if (registered) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, CurioDropsEvent.class, CuriosCompat::spillCurioDrops);
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

    public static ItemStack findFirstEquippedBackpack(PlayerEntity player) {
        return CuriosApi.getCuriosHelper()
                .findFirstCurio(player, stack -> stack.getItem() instanceof BackpackItem)
                .map(SlotResult::getStack)
                .orElse(ItemStack.EMPTY);
    }

    public static List<ItemStack> getEquippedBackpacks(PlayerEntity player) {
        List<SlotResult> curios = CuriosApi.getCuriosHelper()
                .findCurios(player, stack -> stack.getItem() instanceof BackpackItem);
        List<ItemStack> stacks = new java.util.ArrayList<>(curios.size());
        for (SlotResult result : curios) {
            stacks.add(result.getStack());
        }
        return stacks;
    }

    public static boolean tryEquipBackpack(PlayerEntity player, ItemStack stack) {
        if (!Inmis.CONFIG.enableTrinketCompatibility) {
            return false;
        }
        if (stack.isEmpty()) {
            return false;
        }
        if (!(stack.getItem() instanceof BackpackItem) && stack.getItem() != Inmis.ENDER_POUCH.get()) {
            return false;
        }

        return CuriosApi.getCuriosHelper().getCuriosHandler(player).resolve()
                .flatMap(handler -> handler.getStacksHandler(BACK_SLOT))
                .map(handler -> tryInsertIntoBackSlot(player, handler, stack))
                .orElse(false);
    }

    private static boolean tryInsertIntoBackSlot(PlayerEntity player, ICurioStacksHandler handler, ItemStack stack) {
        IDynamicStackHandler stacks = handler.getStacks();
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (stacks.getStackInSlot(i).isEmpty()) {
                ItemStack toInsert = stack.copy();
                toInsert.setCount(1);
                // Curios 4's raw handler omits the native slot's tag, callback and equip-event checks.
                top.theillusivec4.curios.common.inventory.CurioSlot nativeSlot =
                        new top.theillusivec4.curios.common.inventory.CurioSlot(
                                player, stacks, i, BACK_SLOT, 0, 0, handler.getRenders());
                if (!nativeSlot.mayPlace(toInsert)) {
                    continue;
                }
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
        if (!(event.getEntityLiving() instanceof PlayerEntity) || event.getEntityLiving().level.isClientSide
                || !Inmis.CONFIG.enableTrinketCompatibility || !Inmis.CONFIG.spillArmorBackpacksOnDeath) {
            return;
        }
        PlayerEntity player = (PlayerEntity) event.getEntityLiving();

        // Curios has already selected native drops and applied KEEP, DESTROY and Vanishing rules.
        // Never inspect retained equipped slots or remove item-equal drops from another inventory.
        for (ItemEntity backpackDrop : new java.util.ArrayList<>(event.getDrops())) {
            ItemStack stack = backpackDrop.getItem();
            if (!(stack.getItem() instanceof BackpackItem)) {
                continue;
            }
            for (ItemStack contents : Inmis.getBackpackContents(stack)) {
                if (!contents.isEmpty()) {
                    ItemEntity contentDrop = new ItemEntity(player.level,
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

    public static int replaceMatchingStacks(PlayerEntity player, Predicate<ItemStack> matcher, UnaryOperator<ItemStack> converter) {
        return CuriosApi.getCuriosHelper().getCuriosHandler(player)
                .map(handler -> {
                    List<SlotResult> curios = CuriosApi.getCuriosHelper().findCurios(player, matcher::test);
                    int converted = 0;
                    for (SlotResult result : curios) {
                        ItemStack replacement = converter.apply(result.getStack());
                        if (replacement != null && !replacement.isEmpty()) {
                            CuriosApi.getCuriosHelper().setEquippedCurio(player,
                                    result.getSlotContext().getIdentifier(), result.getSlotContext().getIndex(), replacement);
                            converted++;
                        }
                    }
                    return converted;
                })
                .orElse(0);
    }

}
