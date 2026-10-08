package draylar.inmis.smoketest;

import com.mojang.authlib.GameProfile;
import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.config.InmisConfig;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import java.util.Collections;
import java.util.UUID;

/** Native checks for pickup and equipment contracts that changed before Minecraft 1.18. */
public final class LegacyMechanicsChecks {
    private LegacyMechanicsChecks() { }

    public static void allowedArrowPickupRoutesAndFallsBackWithoutLoss(SmokeContext context) {
        InmisConfig previous = Inmis.CONFIG;
        Inmis.CONFIG = new InmisConfig();
        FakePlayer player = player(context);
        GroundedArrow arrow = null;
        try {
            // Exercise the real periodic server path, including Seedflow's direction lookup.
            player.ticksExisted = 5;
            draylar.inmis.augment.BackpackAugmentHandler.onPlayerTick(player);
            fillPlayerInventory(player);
            ItemStack quiver = quiver(true);
            player.inventory.setInventorySlotContents(0, quiver);
            arrow = arrow(context, EntityArrow.PickupStatus.ALLOWED);
            arrow.onCollideWithPlayer(player);
            check(arrow.isDead, "Allowed arrow was not picked up into the backpack");
            check(packed(quiver, Items.ARROW) == 1 && carried(player, Items.ARROW) == 0,
                    "Allowed arrow bypassed the backpack or duplicated");
            check(packed(new ItemStack(quiver.writeToNBT(new NBTTagCompound())), Items.ARROW) == 1,
                    "Picked-up arrow did not persist in backpack NBT");

            player.inventory.clear();
            quiver = quiver(false);
            player.inventory.setInventorySlotContents(0, quiver);
            arrow = arrow(context, EntityArrow.PickupStatus.ALLOWED);
            arrow.onCollideWithPlayer(player);
            check(arrow.isDead && carried(player, Items.ARROW) == 1 && packed(quiver, Items.ARROW) == 0,
                    "Disabled Funnelling broke vanilla arrow pickup");

            fillPlayerInventory(player);
            quiver = quiver(true);
            player.inventory.setInventorySlotContents(0, quiver);
            BackpackInventory contents = inventory(quiver);
            for (int slot = 0; slot < contents.getSizeInventory(); slot++) {
                contents.setInventorySlotContents(slot, new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE), 64));
            }
            arrow = arrow(context, EntityArrow.PickupStatus.ALLOWED);
            arrow.onCollideWithPlayer(player);
            check(!arrow.isDead && packed(quiver, Items.ARROW) == 0 && carried(player, Items.ARROW) == 0,
                    "Full backpack and player inventory deleted or duplicated the arrow");
        } finally {
            if (arrow != null) arrow.setDead();
            player.setDead();
            Inmis.CONFIG = previous;
        }
        context.succeed();
    }

    public static void arrowPickupRetainsCreativeForbiddenAndGroundingRules(SmokeContext context) {
        InmisConfig previous = Inmis.CONFIG;
        Inmis.CONFIG = new InmisConfig();
        FakePlayer player = player(context);
        GroundedArrow arrow = null;
        try {
            fillPlayerInventory(player);
            ItemStack quiver = quiver(true);
            player.inventory.setInventorySlotContents(0, quiver);

            arrow = arrow(context, EntityArrow.PickupStatus.DISALLOWED);
            arrow.onCollideWithPlayer(player);
            check(!arrow.isDead && packed(quiver, Items.ARROW) == 0,
                    "Funnelling admitted an arrow with forbidden pickup");
            arrow.setDead();

            arrow = arrow(context, EntityArrow.PickupStatus.CREATIVE_ONLY);
            arrow.onCollideWithPlayer(player);
            check(!arrow.isDead && packed(quiver, Items.ARROW) == 0,
                    "Survival player picked up a creative-only arrow");
            player.capabilities.isCreativeMode = true;
            arrow.onCollideWithPlayer(player);
            check(arrow.isDead && packed(quiver, Items.ARROW) == 0 && carried(player, Items.ARROW) == 0,
                    "Creative-only pickup created an item instead of following vanilla removal");
            player.capabilities.isCreativeMode = false;

            arrow = arrow(context, EntityArrow.PickupStatus.ALLOWED);
            arrow.arrowShake = 5;
            arrow.onCollideWithPlayer(player);
            check(!arrow.isDead && packed(quiver, Items.ARROW) == 0,
                    "Funnelling ignored the native arrow shake delay");
            arrow.setDead();

            arrow = arrow(context, EntityArrow.PickupStatus.ALLOWED);
            arrow.setGrounded(false);
            arrow.onCollideWithPlayer(player);
            check(!arrow.isDead && packed(quiver, Items.ARROW) == 0,
                    "Funnelling picked up an airborne arrow");
        } finally {
            if (arrow != null) arrow.setDead();
            player.setDead();
            Inmis.CONFIG = previous;
        }
        context.succeed();
    }

    public static void baublesNativeAdmissionAndRecoveryFollowConfig(SmokeContext context) {
        check(Inmis.BAUBLES_LOADED == net.minecraftforge.fml.common.Loader.isModLoaded("baubles"),"Baubles availability disagrees with native loader");
        if (!Inmis.BAUBLES_LOADED) { context.succeed(); return; }
        FakePlayer player=player(context); ItemStack bag=quiver(true);
        baubles.api.cap.IBaublesItemHandler handler=baubles.api.BaublesApi.getBaublesHandler(player);
        baubles.common.container.SlotBauble slot=new baubles.common.container.SlotBauble(player,handler,5,0,0);
        boolean compat=Inmis.CONFIG.enableTrinketCompatibility; boolean empty=Inmis.CONFIG.requireEmptyForUnequip;
        try {
            Inmis.CONFIG.enableTrinketCompatibility=true; check(slot.isItemValid(bag),"Native BODY slot denied valid backpack");
            check(draylar.inmis.compat.BaublesCompat.tryEquipBackpack(player,bag),"Use-to-equip failed native BODY admission");
            ItemStack equipped=handler.getStackInSlot(5); inventory(equipped).setInventorySlotContents(0,new ItemStack(Items.DIAMOND,7));
            Inmis.CONFIG.requireEmptyForUnequip=true; check(!slot.canTakeStack(player),"Native BODY ignored nonempty unequip restriction");
            Inmis.CONFIG.enableTrinketCompatibility=false; check(slot.canTakeStack(player),"Disabled integration trapped equipped backpack");
            check(!slot.isItemValid(equipped),"Disabled integration accepted new backpack");
            check(packed(equipped,Items.DIAMOND)==7,"Native BODY operation lost saved contents");
        } finally { Inmis.CONFIG.enableTrinketCompatibility=compat;Inmis.CONFIG.requireEmptyForUnequip=empty;player.setDead(); }
        context.succeed();
    }

    public static void nativeChestAdmissionShiftAndRemovalPreserveStorage(SmokeContext context) {
        FakePlayer player = player(context);
        try { checkNativeChestAdmission(player); }
        finally { player.setDead(); }
        context.succeed();
    }

    private static FakePlayer player(SmokeContext context) {
        FakePlayer player = new FakePlayer(context.getLevel(), new GameProfile(UUID.randomUUID(), "InmisLegacy"));
        BlockPos position = context.absolutePos(new BlockPos(1, 1, 1));
        player.setPositionAndRotation(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0, 0);
        player.inventory.clear();
        player.capabilities.isCreativeMode = false;
        return player;
    }

    private static void checkNativeChestAdmission(FakePlayer player) {
        boolean previousArmor = Inmis.CONFIG.allowBackpacksInChestplate;
        boolean previousCompatibility = Inmis.CONFIG.enableTrinketCompatibility;
        boolean previousRequireEmpty = Inmis.CONFIG.requireEmptyForUnequip;
        try {
            // Isolate native armor admission from optional BODY-slot routing.
            Inmis.CONFIG.enableTrinketCompatibility = false;
            Inmis.CONFIG.requireEmptyForUnequip = false;
            Inmis.CONFIG.allowBackpacksInChestplate = true;
            ItemStack bag = quiver(true);
            bag.setStackDisplayName("Inmis filled armor backpack");
            BackpackInventory contents = inventory(bag);
            for (int slot = 0; slot < contents.getSizeInventory(); slot++) {
                contents.setInventorySlotContents(slot, new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE), 64));
            }
            contents.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 7));
            ItemStack armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
            armor.setStackDisplayName("Inmis stored armor");
            armor.setItemDamage(23);
            armor.addEnchantment(net.minecraft.init.Enchantments.PROTECTION, 3);
            contents.setInventorySlotContents(1, armor);
            ItemStack expected = bag.copy();
            net.minecraft.inventory.Container menu = player.inventoryContainer;
            net.minecraft.inventory.Slot chest = menu.getSlot(6);

            check(chest.isItemValid(bag), "Enabled native chest slot rejected a filled backpack");
            player.inventory.setItemStack(bag);
            menu.slotClick(6, 0, net.minecraft.inventory.ClickType.PICKUP, player);
            check(ItemStack.areItemStacksEqual(expected, chest.getStack()) && player.inventory.getItemStack().isEmpty()
                            && ItemStack.areItemStacksEqual(expected, player.inventory.getStackInSlot(38)),
                    "Native chest drag lost backpack contents, augments or metadata");

            menu.slotClick(6, 0, net.minecraft.inventory.ClickType.PICKUP, player);
            menu.slotClick(9, 0, net.minecraft.inventory.ClickType.PICKUP, player);
            check(chest.getStack().isEmpty() && ItemStack.areItemStacksEqual(expected, menu.getSlot(9).getStack())
                            && player.inventory.getItemStack().isEmpty(),
                    "Native chest removal did not preserve the filled backpack");
            check(!menu.transferStackInSlot(player, 9).isEmpty() && menu.getSlot(9).getStack().isEmpty()
                            && ItemStack.areItemStacksEqual(expected, chest.getStack()) && nativeBackpackCount(player) == 1,
                    "Native shift equip did not preserve exactly one filled backpack");
            check(!menu.transferStackInSlot(player, 6).isEmpty() && chest.getStack().isEmpty()
                            && ItemStack.areItemStacksEqual(expected, menu.getSlot(9).getStack()) && nativeBackpackCount(player) == 1,
                    "Native shift unequip lost or duplicated the filled backpack");

            Inmis.CONFIG.allowBackpacksInChestplate = false;
            check(!chest.isItemValid(expected), "Disabled native chest slot admits a backpack");
            menu.transferStackInSlot(player, 9);
            check(chest.getStack().isEmpty() && ItemStack.areItemStacksEqual(expected, menu.getSlot(36).getStack())
                            && nativeBackpackCount(player) == 1,
                    "Disabled shift equip inserted a backpack into armor or changed its contents");
            menu.slotClick(36, 0, net.minecraft.inventory.ClickType.PICKUP, player);
            menu.slotClick(6, 0, net.minecraft.inventory.ClickType.PICKUP, player);
            check(chest.getStack().isEmpty() && ItemStack.areItemStacksEqual(expected, player.inventory.getItemStack())
                            && nativeBackpackCount(player) == 0,
                    "Disabled chest drag changed or equipped the carried backpack");
        } finally {
            player.inventory.setItemStack(ItemStack.EMPTY);
            player.inventory.clear();
            Inmis.CONFIG.allowBackpacksInChestplate = previousArmor;
            Inmis.CONFIG.enableTrinketCompatibility = previousCompatibility;
            Inmis.CONFIG.requireEmptyForUnequip = previousRequireEmpty;
        }
    }

    private static int nativeBackpackCount(FakePlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack.getItem() instanceof BackpackItem) count += stack.getCount();
        }
        return count;
    }

    private static GroundedArrow arrow(SmokeContext context, EntityArrow.PickupStatus pickup) {
        BlockPos position = context.absolutePos(new BlockPos(1, 1, 1));
        GroundedArrow arrow = new GroundedArrow(context.getLevel(), position.getX() + 0.5,
                position.getY(), position.getZ() + 0.5);
        arrow.pickupStatus = pickup;
        context.getLevel().spawnEntity(arrow);
        return arrow;
    }

    private static ItemStack quiver(boolean funnel) {
        ItemStack stack = backpack("gilded");
        Inmis.setBackpackAugments(stack, BackpackAugmentsComponent.DEFAULT.withFunnelling(
                new BackpackAugmentsComponent.FunnellingSettings(funnel,
                        BackpackAugmentsComponent.FunnellingSettings.Mode.ALLOW, Collections.emptyList()))
                .withQuiverlink(new BackpackAugmentsComponent.QuiverlinkSettings(true,
                        BackpackAugmentsComponent.QuiverlinkSettings.Priority.BACKPACK)));
        return stack;
    }

    private static ItemStack backpack(String tier) {
        return new ItemStack(Inmis.BACKPACKS.stream().map(entry -> entry.get())
                .filter(item -> item.getTier().getName().equals(tier)).findFirst()
                .orElseThrow(() -> new AssertionError("Fixture backpack tier missing: " + tier)));
    }

    private static BackpackInventory inventory(ItemStack stack) {
        return new BackpackInventory(stack, ((BackpackItem) stack.getItem()).getTier());
    }

    private static int packed(ItemStack stack, Item item) {
        return Inmis.getBackpackContents(stack).stream().filter(entry -> entry.getItem() == item)
                .mapToInt(ItemStack::getCount).sum();
    }

    private static int carried(FakePlayer player, Item item) {
        return player.inventory.mainInventory.stream().filter(entry -> entry.getItem() == item)
                .mapToInt(ItemStack::getCount).sum();
    }

    private static void fillPlayerInventory(FakePlayer player) {
        for (int slot = 0; slot < 36; slot++) player.inventory.setInventorySlotContents(slot, new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE), 64));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class GroundedArrow extends net.minecraft.entity.projectile.EntityTippedArrow {
        private GroundedArrow(WorldServer world, double x, double y, double z) {
            super(world, x, y, z);
            inGround = true;
            arrowShake = 0;
        }

        private void setGrounded(boolean grounded) { inGround = grounded; }
    }

}
