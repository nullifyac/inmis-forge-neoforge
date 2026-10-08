package draylar.inmis.item;

import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackAugmentType;
import draylar.inmis.augment.BackpackAugments;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.ui.BackpackScreenHandler;
import draylar.inmis.util.BackpackStorage;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import javax.annotation.Nullable;

import java.util.List;

public class BackpackItem extends Item {

    private final BackpackInfo backpack;

    public BackpackItem(BackpackInfo backpack, Item.Properties properties) {
        super(properties);
        this.backpack = backpack;
    }

    @Override
    public ActionResult<ItemStack> use(World level, PlayerEntity user, Hand hand) {
        if (!Inmis.CONFIG.requireArmorTrinketToOpen) {
            if (Inmis.CONFIG.playSound) {
                if (level.isClientSide) {
                    SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(backpack.getOpenSound()));
                    if (sound != null) {
                        level.playSound(user, user.blockPosition(), sound, SoundCategory.PLAYERS, 1f, 1f);
                    }
                }
            }

            openScreen(user, user.getItemInHand(hand));
            return ActionResult.success(user.getItemInHand(hand));
        }

        return ActionResult.pass(user.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World level, List<ITextComponent> tooltip, ITooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        List<BackpackAugmentType> unlocks = BackpackAugments.getTierUnlocks(backpack);
        if (!unlocks.isEmpty()) {
            IFormattableTextComponent list = new StringTextComponent("");
            for (int i = 0; i < unlocks.size(); i++) {
                if (i > 0) {
                    list = list.append(", ");
                }
                list = list.append(unlocks.get(i).label());
            }
            tooltip.add(new TranslationTextComponent("inmis.tooltip.unlocks", list).withStyle(TextFormatting.GRAY));
        } else {
            tooltip.add(new TranslationTextComponent("inmis.tooltip.unlocks.none").withStyle(TextFormatting.GRAY));
        }
    }

    public static void openScreen(PlayerEntity player, ItemStack backpackItemStack) {
        if (!player.level.isClientSide && player instanceof ServerPlayerEntity) {
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            if (!(backpackItemStack.getItem() instanceof BackpackItem) || backpackItemStack.isEmpty()) {
                return;
            }
            BackpackItem backpackItem = (BackpackItem) backpackItemStack.getItem();
            try {
                int rows = BackpackStorage.getRequiredRows(backpackItemStack, backpackItem.getTier());
                if ((long) rows * backpackItem.getTier().getRowWidth() > Short.MAX_VALUE - 36L) {
                    throw new IllegalArgumentException("Saved contents exceed the supported menu size");
                }
            } catch (IllegalArgumentException exception) {
                player.displayClientMessage(new TranslationTextComponent("inmis.error.backpack_capacity"), false);
                Inmis.LOGGER.error("Cannot open backpack without losing saved contents: {}", exception.getMessage());
                return;
            }
            Inmis.getOrCreateAugments(backpackItemStack, backpackItem.getTier());
            NetworkHooks.openGui(serverPlayer, new INamedContainerProvider() {
                @Override
                public ITextComponent getDisplayName() {
                    return new TranslationTextComponent(backpackItemStack.getItem().getDescriptionId());
                }

                @Override
                public @Nullable Container createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
                    return new BackpackScreenHandler(syncId, inv, backpackItemStack);
                }
            }, buf -> BackpackScreenHandler.writeOpeningData(buf, serverPlayer.inventory, backpackItemStack));
        }
    }

    public BackpackInfo getTier() {
        return backpack;
    }

    @Override
    public EquipmentSlotType getEquipmentSlot(ItemStack stack) {
        return Inmis.CONFIG.allowBackpacksInChestplate ? EquipmentSlotType.CHEST : EquipmentSlotType.MAINHAND;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return false;
    }
}
