package draylar.inmis.item;

import draylar.inmis.Inmis;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class EnderBackpackItem extends Item {

    public static final Component CONTAINER_NAME = Component.translatable("container.enderchest");

    public EnderBackpackItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        PlayerEnderChestContainer enderChestInventory = player.getEnderChestInventory();

        if (Inmis.CONFIG.playSound) {
            if (level.isClientSide) {
                level.playSound(player, player.blockPosition(), SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS, 1f, 1f);
            }
        }

        if (enderChestInventory != null) {
            if (!level.isClientSide) {
                player.openMenu(new SimpleMenuProvider(
                        (id, playerInventory, playerEntity) -> ChestMenu.threeRows(id, playerInventory, enderChestInventory),
                        CONTAINER_NAME));
                player.awardStat(Stats.OPEN_ENDERCHEST);
            }
        }

        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}
