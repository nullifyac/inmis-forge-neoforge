package draylar.inmis.item;

import draylar.inmis.Inmis;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.stats.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class EnderBackpackItem extends Item {

    public static final ITextComponent CONTAINER_NAME = new TranslationTextComponent("container.enderchest");

    public EnderBackpackItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public ActionResult<ItemStack> use(World level, PlayerEntity player, Hand hand) {
        EnderChestInventory enderChestInventory = player.getEnderChestInventory();

        if (Inmis.CONFIG.playSound) {
            if (level.isClientSide) {
                level.playSound(player, player.blockPosition(), SoundEvents.ENDER_CHEST_OPEN, SoundCategory.PLAYERS, 1f, 1f);
            }
        }

        if (enderChestInventory != null) {
            if (!level.isClientSide) {
                player.openMenu(new SimpleNamedContainerProvider(
                        (id, playerInventory, playerEntity) -> ChestContainer.threeRows(id, playerInventory, enderChestInventory),
                        CONTAINER_NAME));
                player.awardStat(Stats.OPEN_ENDERCHEST);
            }
        }

        return ActionResult.success(player.getItemInHand(hand));
    }
}
