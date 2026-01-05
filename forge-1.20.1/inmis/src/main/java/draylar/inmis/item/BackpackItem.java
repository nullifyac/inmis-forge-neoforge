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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

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
                    SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(backpack.getOpenSound()));
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

    public static void openScreen(Player player, ItemStack backpackItemStack) {
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
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
    public EquipmentSlot getEquipmentSlot() {
        return Inmis.CONFIG.allowBackpacksInChestplate ? EquipmentSlot.CHEST : EquipmentSlot.MAINHAND;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return false;
    }
}
