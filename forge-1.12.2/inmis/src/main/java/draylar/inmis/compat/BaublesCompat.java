package draylar.inmis.compat;
import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import baubles.api.IBauble;
import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.cap.BaublesCapabilities;
import baubles.api.cap.IBaublesItemHandler;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.util.*;
import java.util.function.*;
/** Loaded only when the optional Baubles mod is installed. */
public final class BaublesCompat {
    public static final int BODY_SLOT = 5;
    public static void register() { MinecraftForge.EVENT_BUS.register(new BaublesCompat()); }
    @SubscribeEvent public void attach(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();
        if (!(stack.getItem() instanceof BackpackItem) && (!Inmis.ENDER_POUCH.isPresent() || stack.getItem() != Inmis.ENDER_POUCH.get())) return;
        final IBauble bauble = new IBauble() {
            public BaubleType getBaubleType(ItemStack value) { return BaubleType.BODY; }
            public boolean canEquip(ItemStack value, EntityLivingBase player) { return Inmis.CONFIG.enableTrinketCompatibility; }
            public boolean canUnequip(ItemStack value, EntityLivingBase player) {
                return !Inmis.CONFIG.enableTrinketCompatibility || !Inmis.CONFIG.requireEmptyForUnequip || !(value.getItem() instanceof BackpackItem) || Inmis.isBackpackEmpty(value);
            }
            public boolean willAutoSync(ItemStack value, EntityLivingBase player) { return true; }
        };
        event.addCapability(Inmis.id("bauble"), new ICapabilityProvider() {
            public boolean hasCapability(Capability<?> cap, EnumFacing face) { return cap == BaublesCapabilities.CAPABILITY_ITEM_BAUBLE; }
            public <T> T getCapability(Capability<T> cap, EnumFacing face) { return cap == BaublesCapabilities.CAPABILITY_ITEM_BAUBLE ? BaublesCapabilities.CAPABILITY_ITEM_BAUBLE.cast(bauble) : null; }
        });
    }
    /** Called only for the exact newly-created item selected by Baubles' native death path. */
    public static void spillSelectedDrop(net.minecraft.entity.item.EntityItem drop, List<net.minecraft.entity.item.EntityItem> drops) {
        if (!Inmis.CONFIG.enableTrinketCompatibility || !Inmis.CONFIG.spillArmorBackpacksOnDeath) return;
        ItemStack bag = drop.getItem(); if (!(bag.getItem() instanceof BackpackItem)) return;
        for (ItemStack contents : Inmis.getBackpackContents(bag)) {
            if (contents.isEmpty()) continue;
            net.minecraft.entity.item.EntityItem item = new net.minecraft.entity.item.EntityItem(drop.world,drop.posX,drop.posY,drop.posZ,contents);
            item.setPickupDelay(40); item.motionX=drop.motionX; item.motionY=drop.motionY; item.motionZ=drop.motionZ;
            drops.add(item);
        }
        Inmis.wipeBackpack(bag);
    }
    public static ItemStack findFirstEquippedBackpack(EntityPlayer player) {
        IBaublesItemHandler handler = BaublesApi.getBaublesHandler(player);
        if (handler == null || handler.getSlots() <= BODY_SLOT) return ItemStack.EMPTY;
        ItemStack stack = handler.getStackInSlot(BODY_SLOT);
        return stack.getItem() instanceof BackpackItem ? stack : ItemStack.EMPTY;
    }
    public static List<ItemStack> getEquippedBackpacks(EntityPlayer player) {
        ItemStack stack = findFirstEquippedBackpack(player);
        return stack.isEmpty() ? Collections.emptyList() : Collections.singletonList(stack);
    }
    public static boolean tryEquipBackpack(EntityPlayer player, ItemStack stack) {
        if (!Inmis.CONFIG.enableTrinketCompatibility || stack.isEmpty()) return false;
        IBaublesItemHandler handler = BaublesApi.getBaublesHandler(player);
        if (handler == null || handler.getSlots() <= BODY_SLOT || !handler.getStackInSlot(BODY_SLOT).isEmpty() || !handler.isItemValidForSlot(BODY_SLOT, stack, player)) return false;
        IBauble bauble = stack.getCapability(BaublesCapabilities.CAPABILITY_ITEM_BAUBLE, null);
        if (bauble == null || !bauble.canEquip(stack, player)) return false;
        ItemStack single = stack.copy(); single.setCount(1);
        if (!handler.insertItem(BODY_SLOT, single, false).isEmpty()) return false;
        bauble.onEquipped(single, player); handler.setChanged(BODY_SLOT, true); stack.shrink(1); return true;
    }
    public static int replaceMatchingStacks(EntityPlayer player, Predicate<ItemStack> predicate, UnaryOperator<ItemStack> converter) {
        IBaublesItemHandler handler = BaublesApi.getBaublesHandler(player);
        if (handler == null) return 0;
        int changed = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (predicate.test(stack)) {
                ItemStack replacement = converter.apply(stack);
                if (!replacement.isEmpty()) { handler.setStackInSlot(i, replacement); handler.setChanged(i, true); changed++; }
            }
        }
        return changed;
    }
}
