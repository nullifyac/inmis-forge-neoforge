package draylar.inmis.item;

import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackAugmentType;
import draylar.inmis.augment.BackpackAugments;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BackpackItem extends Item implements Equipable {

    private final BackpackInfo backpack;

    public BackpackItem(BackpackInfo backpack, Item.Properties properties) {
        super(properties);
        this.backpack = backpack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        if (!Inmis.CONFIG.requireArmorTrinketToOpen) {
            if (Inmis.CONFIG.playSound) {
                if (level.isClientSide) {
                    ResourceLocation soundId = ResourceLocation.tryParse(backpack.getOpenSound());
                    SoundEvent sound = soundId != null
                            ? BuiltInRegistries.SOUND_EVENT.getOptional(soundId).orElse(null)
                            : null;
                    if (sound != null) {
                        level.playSound(user, user.blockPosition(), sound, SoundSource.PLAYERS, 1f, 1f);
                    }
                }
            }

            openScreen(user, user.getItemInHand(hand));
            return InteractionResultHolder.success(user.getItemInHand(hand));
        }

        return InteractionResultHolder.pass(user.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        List<BackpackAugmentType> unlocks = BackpackAugments.getTierUnlocks(backpack);
        if (!unlocks.isEmpty()) {
            MutableComponent list = Component.empty();
            for (int i = 0; i < unlocks.size(); i++) {
                if (i > 0) {
                    list = list.append(", ");
                }
                list = list.append(unlocks.get(i).label());
            }
            tooltip.add(Component.translatable("inmis.tooltip.unlocks", list).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("inmis.tooltip.unlocks.none").withStyle(ChatFormatting.GRAY));
        }
    }

    public static void openScreen(Player player, ItemStack backpackItemStack) {
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (backpackItemStack.getItem() instanceof BackpackItem backpackItem) {
                Inmis.getOrCreateAugments(backpackItemStack, backpackItem.getTier());
            }
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable(backpackItemStack.getItem().getDescriptionId());
                }

                @Override
                public @Nullable AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
                    return new BackpackScreenHandler(syncId, inv, backpackItemStack);
                }
            }, buf -> ItemStack.STREAM_CODEC.encode(buf, backpackItemStack));
        }
    }

    public BackpackInfo getTier() {
        return backpack;
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return Inmis.CONFIG.allowBackpacksInChestplate ? EquipmentSlot.CHEST : EquipmentSlot.MAINHAND;
    }

}
