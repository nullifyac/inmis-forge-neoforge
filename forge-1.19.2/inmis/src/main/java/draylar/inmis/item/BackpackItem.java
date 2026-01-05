package draylar.inmis.item;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

public class BackpackItem extends Item {

    private final BackpackInfo backpack;

    public BackpackItem(BackpackInfo backpack, Item.Properties properties) {
        super(properties);
        this.backpack = backpack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!Inmis.CONFIG.requireArmorTrinketToOpen) {
            if (Inmis.CONFIG.playSound) {
                if (level.isClientSide) {
                    SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(backpack.getOpenSound()));
                    if (sound != null) {
                        level.playSound(user, user.blockPosition(), sound, SoundSource.PLAYERS, 1f, 1f);
                    }
                }
            }

            openScreen(user, stack);
            return InteractionResultHolder.success(stack);
        }

        if (Inmis.CONFIG.allowBackpacksInChestplate) {
            ItemStack equipped = user.getItemBySlot(EquipmentSlot.CHEST);
            if (equipped.isEmpty()) {
                if (!level.isClientSide) {
                    ItemStack toEquip = stack.copy();
                    toEquip.setCount(1);
                    user.setItemSlot(EquipmentSlot.CHEST, toEquip);
                    if (!user.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        }

        return InteractionResultHolder.pass(stack);
    }

    public static void openScreen(Player player, ItemStack backpackItemStack) {
        if (!player.level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable(backpackItemStack.getItem().getDescriptionId());
                }

                @Override
                public @Nullable AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
                    return new BackpackScreenHandler(syncId, inv, backpackItemStack);
                }
            }, buf -> buf.writeItem(backpackItemStack));
        }
    }

    public BackpackInfo getTier() {
        return backpack;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return false;
    }
}
