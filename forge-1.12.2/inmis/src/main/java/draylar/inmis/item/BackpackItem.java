package draylar.inmis.item;
import draylar.inmis.Inmis;
import draylar.inmis.augment.*;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.util.BackpackStorage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.*;
import net.minecraft.util.text.*;
import net.minecraft.world.World;
import net.minecraft.client.util.ITooltipFlag;
import javax.annotation.Nullable;
import java.util.List;
public class BackpackItem extends Item {
    private final BackpackInfo backpack;
    public BackpackItem(BackpackInfo backpack) { this.backpack = backpack; setMaxStackSize(1); setCreativeTab(Inmis.GROUP); }
    public BackpackInfo getTier() { return backpack; }
    @Override public EntityEquipmentSlot getEquipmentSlot(ItemStack stack) { return Inmis.CONFIG.allowBackpacksInChestplate ? EntityEquipmentSlot.CHEST : null; }
    @Override public boolean isValidArmor(ItemStack stack, EntityEquipmentSlot slot, net.minecraft.entity.Entity player) {
        return slot == EntityEquipmentSlot.CHEST && Inmis.CONFIG.allowBackpacksInChestplate;
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!Inmis.CONFIG.requireArmorTrinketToOpen) {
            if (Inmis.CONFIG.playSound && world.isRemote) {
                SoundEvent sound = net.minecraftforge.fml.common.registry.ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(backpack.getOpenSound()));
                if (sound != null) world.playSound(player, player.getPosition(), sound, SoundCategory.PLAYERS, 1, 1);
            }
            openScreen(player, stack); return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        return new ActionResult<>(EnumActionResult.PASS, stack);
    }
    @Override public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        List<BackpackAugmentType> unlocks = BackpackAugments.getTierUnlocks(backpack);
        StringBuilder labels = new StringBuilder();
        for (BackpackAugmentType type : unlocks) {
            if (labels.length() > 0) labels.append(", "); labels.append(type.label().getUnformattedText());
        }
        tooltip.add(TextFormatting.GRAY + new TextComponentTranslation(unlocks.isEmpty() ? "inmis.tooltip.unlocks.none" : "inmis.tooltip.unlocks", labels.toString()).getUnformattedText());
    }
    public static void openScreen(EntityPlayer player, ItemStack stack) {
        if (!player.world.isRemote && player instanceof EntityPlayerMP && stack.getItem() instanceof BackpackItem) {
            // Validate recovery dimensions before sending anything or mutating a server menu.
            BackpackStorage.getRequiredSize(stack, ((BackpackItem)stack.getItem()).getTier());
            draylar.inmis.network.ServerNetworking.open((EntityPlayerMP)player, stack);
        }
    }
}
