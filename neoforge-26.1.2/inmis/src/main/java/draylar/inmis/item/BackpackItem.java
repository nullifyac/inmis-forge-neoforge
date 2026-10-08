package draylar.inmis.item;

import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackAugmentType;
import draylar.inmis.augment.BackpackAugments;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.ui.BackpackScreenHandler;
import draylar.inmis.util.BackpackStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BackpackItem extends Item {

    private final BackpackInfo backpack;

    public BackpackItem(BackpackInfo backpack, Item.Properties properties) {
        super(properties);
        this.backpack = backpack;
    }

    @Override
    public InteractionResult use(Level level, Player user, InteractionHand hand) {
        if (!Inmis.CONFIG.requireArmorTrinketToOpen) {
            if (Inmis.CONFIG.playSound) {
                if (level.isClientSide()) {
                    Identifier soundId = Identifier.tryParse(backpack.getOpenSound());
                    SoundEvent sound = soundId != null
                            ? BuiltInRegistries.SOUND_EVENT.getOptional(soundId).orElse(null)
                            : null;
                    if (sound != null) {
                        level.playSound(user, user.blockPosition(), sound, SoundSource.PLAYERS, 1f, 1f);
                    }
                }
            }

            openScreen(user, user.getItemInHand(hand));
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        List<BackpackAugmentType> unlocks = BackpackAugments.getTierUnlocks(backpack);
        if (!unlocks.isEmpty()) {
            MutableComponent list = Component.empty();
            for (int i = 0; i < unlocks.size(); i++) {
                if (i > 0) {
                    list = list.append(", ");
                }
                list = list.append(unlocks.get(i).label());
            }
            tooltip.accept(Component.translatable("inmis.tooltip.unlocks", list).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.accept(Component.translatable("inmis.tooltip.unlocks.none").withStyle(ChatFormatting.GRAY));
        }
    }

    public static void openScreen(Player player, ItemStack backpackItemStack) {
        if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (!(backpackItemStack.getItem() instanceof BackpackItem backpackItem) || backpackItemStack.isEmpty()) {
                return;
            }
            try {
                int rows = BackpackStorage.getRequiredRows(backpackItemStack, backpackItem.getTier());
                if ((long) rows * backpackItem.getTier().getRowWidth() > Short.MAX_VALUE - 36L) {
                    throw new IllegalArgumentException("Saved contents exceed the supported menu size");
                }
            } catch (IllegalArgumentException exception) {
                serverPlayer.sendSystemMessage(Component.translatable("inmis.error.backpack_capacity"));
                Inmis.LOGGER.error("Cannot open backpack without losing saved contents: {}", exception.getMessage());
                return;
            }
            Inmis.getOrCreateAugments(backpackItemStack, backpackItem.getTier());
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable(backpackItemStack.getItem().getDescriptionId());
                }

                @Override
                public @Nullable AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
                    return new BackpackScreenHandler(syncId, inv, backpackItemStack);
                }
            }, buf -> BackpackScreenHandler.writeOpeningData(buf, serverPlayer.getInventory(), backpackItemStack));
        }
    }

    public BackpackInfo getTier() {
        return backpack;
    }

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return Inmis.CONFIG.allowBackpacksInChestplate ? EquipmentSlot.CHEST : EquipmentSlot.MAINHAND;
    }

    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot slot, LivingEntity entity) {
        if (slot == EquipmentSlot.CHEST) {
            // ArmorSlot uses canEquip independently of the preferred slot used by shift-click.
            return Inmis.CONFIG.allowBackpacksInChestplate && entity.canUseSlot(slot);
        }
        return super.canEquip(stack, slot, entity);
    }

}
