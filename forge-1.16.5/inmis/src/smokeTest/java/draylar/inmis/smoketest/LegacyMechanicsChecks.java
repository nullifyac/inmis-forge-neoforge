package draylar.inmis.smoketest;

import com.mojang.authlib.GameProfile;
import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.config.InmisConfig;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;

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
            player.tickCount = 5;
            draylar.inmis.augment.BackpackAugmentHandler.onPlayerTick(player);
            checkNativeChestAdmission(player);
            fillPlayerInventory(player);
            ItemStack quiver = quiver(true);
            player.inventory.setItem(0, quiver);
            arrow = arrow(context, AbstractArrowEntity.PickupStatus.ALLOWED);
            arrow.playerTouch(player);
            check(arrow.removed, "Allowed arrow was not picked up into the backpack");
            check(packed(quiver, Items.ARROW) == 1 && carried(player, Items.ARROW) == 0,
                    "Allowed arrow bypassed the backpack or duplicated");
            check(packed(ItemStack.of(quiver.save(new CompoundNBT())), Items.ARROW) == 1,
                    "Picked-up arrow did not persist in backpack NBT");

            player.inventory.clearContent();
            quiver = quiver(false);
            player.inventory.setItem(0, quiver);
            arrow = arrow(context, AbstractArrowEntity.PickupStatus.ALLOWED);
            arrow.playerTouch(player);
            check(arrow.removed && carried(player, Items.ARROW) == 1 && packed(quiver, Items.ARROW) == 0,
                    "Disabled Funnelling broke vanilla arrow pickup");

            fillPlayerInventory(player);
            quiver = quiver(true);
            player.inventory.setItem(0, quiver);
            BackpackInventory contents = inventory(quiver);
            for (int slot = 0; slot < contents.getContainerSize(); slot++) {
                contents.setItem(slot, new ItemStack(Items.STONE, 64));
            }
            arrow = arrow(context, AbstractArrowEntity.PickupStatus.ALLOWED);
            arrow.playerTouch(player);
            check(!arrow.removed && packed(quiver, Items.ARROW) == 0 && carried(player, Items.ARROW) == 0,
                    "Full backpack and player inventory deleted or duplicated the arrow");
        } finally {
            if (arrow != null) arrow.remove();
            player.remove();
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
            player.inventory.setItem(0, quiver);

            arrow = arrow(context, AbstractArrowEntity.PickupStatus.DISALLOWED);
            arrow.playerTouch(player);
            check(!arrow.removed && packed(quiver, Items.ARROW) == 0,
                    "Funnelling admitted an arrow with forbidden pickup");
            arrow.remove();

            arrow = arrow(context, AbstractArrowEntity.PickupStatus.CREATIVE_ONLY);
            arrow.playerTouch(player);
            check(!arrow.removed && packed(quiver, Items.ARROW) == 0,
                    "Survival player picked up a creative-only arrow");
            player.abilities.instabuild = true;
            arrow.playerTouch(player);
            check(arrow.removed && packed(quiver, Items.ARROW) == 0 && carried(player, Items.ARROW) == 0,
                    "Creative-only pickup created an item instead of following vanilla removal");
            player.abilities.instabuild = false;

            arrow = arrow(context, AbstractArrowEntity.PickupStatus.ALLOWED);
            arrow.shakeTime = 5;
            arrow.playerTouch(player);
            check(!arrow.removed && packed(quiver, Items.ARROW) == 0,
                    "Funnelling ignored the native arrow shake delay");
            arrow.remove();

            arrow = arrow(context, AbstractArrowEntity.PickupStatus.ALLOWED);
            arrow.setGrounded(false);
            arrow.playerTouch(player);
            check(!arrow.removed && packed(quiver, Items.ARROW) == 0,
                    "Funnelling picked up an airborne arrow");
        } finally {
            if (arrow != null) arrow.remove();
            player.remove();
            Inmis.CONFIG = previous;
        }
        context.succeed();
    }

    public static void returningTridentKeepsNativeOwnerAndInventoryRules(SmokeContext context) {
        InmisConfig previous = Inmis.CONFIG;
        Inmis.CONFIG = new InmisConfig();
        FakePlayer owner = player(context);
        FakePlayer stranger = player(context);
        ReturningTrident trident = null;
        boolean ownerAdded = false;
        try {
            fillPlayerInventory(owner);
            ItemStack quiver = quiver(true);
            owner.inventory.setItem(0, quiver);
            ItemStack original = new ItemStack(Items.TRIDENT);
            original.setDamageValue(23);
            original.setHoverName(new StringTextComponent("Inmis legacy return"));
            original.enchant(net.minecraft.enchantment.Enchantments.LOYALTY, 3);
            // Legacy ProjectileEntity resolves its owner through the world's UUID registry.
            context.getLevel().addNewPlayer(owner);
            ownerAdded = true;
            trident = new ReturningTrident(context.getLevel(), owner, original);
            BlockPos position = context.absolutePos(new BlockPos(1, 1, 1));
            trident.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0, 0);
            context.getLevel().addFreshEntity(trident);
            check(trident.getOwner() == owner, "Native world did not resolve the trident owner");

            trident.playerTouch(stranger);
            check(!trident.removed && carried(stranger, Items.TRIDENT) == 0,
                    "A different player picked up the owner's trident");
            trident.playerTouch(owner);
            check(!trident.removed && packed(quiver, Items.TRIDENT) == 0 && carried(owner, Items.TRIDENT) == 0,
                    "Full player inventory routed or deleted a returning trident");

            owner.inventory.setItem(1, ItemStack.EMPTY);
            trident.playerTouch(owner);
            ItemStack returned = owner.inventory.getItem(1);
            check(trident.removed && returned.getItem() == Items.TRIDENT && returned.getCount() == 1,
                    "Owner could not recover the trident through vanilla inventory pickup");
            check(returned.getDamageValue() == 23 && returned.getHoverName().getString().equals("Inmis legacy return")
                            && net.minecraft.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                                    net.minecraft.enchantment.Enchantments.LOYALTY, returned) == 3,
                    "Native trident pickup lost damage, name or Loyalty");
            check(packed(quiver, Items.TRIDENT) == 0 && carried(owner, Items.TRIDENT) == 1,
                    "Trident pickup also stored a duplicate in the backpack");
        } finally {
            if (trident != null) trident.remove();
            if (ownerAdded) context.getLevel().removePlayerImmediately(owner);
            else owner.remove();
            stranger.remove();
            Inmis.CONFIG = previous;
        }
        context.succeed();
    }

    public static void curiosNativeAdmissionAndDisabledRecoveryFollowConfig(SmokeContext context) {
        check(Inmis.CURIOS_LOADED == ModList.get().isLoaded("curios"), "Curios availability flag disagrees with loaded mods");
        if (Inmis.CURIOS_LOADED) CuriosChecks.admissionAndRecovery(context);
        context.succeed();
    }

    private static final class CuriosChecks {
        private static void admissionAndRecovery(SmokeContext context) {
            InmisConfig previous = Inmis.CONFIG;
            Inmis.CONFIG = new InmisConfig();
            FakePlayer player = player(context);
            try {
                Inmis.CONFIG.enableTrinketCompatibility = true;
                Inmis.CONFIG.requireEmptyForUnequip = true;
                top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler back =
                        top.theillusivec4.curios.api.CuriosApi.getCuriosHelper().getCuriosHandler(player).resolve()
                                .orElseThrow(() -> new AssertionError("Native Curios capability missing"))
                                .getStacksHandler("back").orElseThrow(() -> new AssertionError("Native Curios back slot missing"));
                top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler stacks = back.getStacks();
                check(stacks.getSlots() > 0, "Native back slot has no capacity");
                top.theillusivec4.curios.common.inventory.CurioSlot slot =
                        new top.theillusivec4.curios.common.inventory.CurioSlot(player, stacks, 0, "back", 0, 0, back.getRenders());
                ItemStack bag = backpack("frayed");
                inventory(bag).setItem(0, new ItemStack(Items.DIAMOND, 7));
                ItemStack pouch = new ItemStack(Inmis.ENDER_POUCH.get());
                check(slot.mayPlace(bag) && slot.mayPlace(pouch),
                        "Native Curios back slot rejects enabled backpack or ender pouch");

                Inmis.CONFIG.enableTrinketCompatibility = false;
                check(!slot.mayPlace(bag) && !slot.mayPlace(pouch),
                        "Native Curios slot permits backpack or pouch admission while disabled");
                check(!draylar.inmis.compat.CuriosCompat.tryEquipBackpack(player, bag) && bag.getCount() == 1
                                && packed(bag, Items.DIAMOND) == 7 && stacks.getStackInSlot(0).isEmpty(),
                        "Disabled use-to-equip mutated the held backpack or native slot");

                Inmis.CONFIG.enableTrinketCompatibility = true;
                check(slot.mayPlace(bag) && stacks.insertItem(0, bag, false).isEmpty(),
                        "Re-enabling compatibility did not restore native admission");
                check(!slot.mayPickup(player), "Filled native backpack bypassed requireEmptyForUnequip");
                Inmis.CONFIG.enableTrinketCompatibility = false;
                check(slot.mayPickup(player), "Disabling compatibility trapped an already equipped backpack");
                ItemStack recovered = slot.remove(1);
                check(recovered.getCount() == 1 && packed(recovered, Items.DIAMOND) == 7
                                && stacks.getStackInSlot(0).isEmpty(),
                        "Native recovery after disabling compatibility lost or duplicated backpack contents");

                Inmis.CONFIG.enableTrinketCompatibility = true;
                int[] nativeDenials = new int[1];
                java.util.function.Consumer<top.theillusivec4.curios.api.event.CurioEquipEvent> denyEquip = event -> {
                    if (event.getSlotContext().getWearer() == player
                            && event.getSlotContext().getIdentifier().equals("back")
                            && event.getStack().getItem() instanceof BackpackItem) {
                        nativeDenials[0]++;
                        event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
                    }
                };
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                        net.minecraftforge.eventbus.api.EventPriority.HIGHEST, false,
                        top.theillusivec4.curios.api.event.CurioEquipEvent.class, denyEquip);
                try {
                    check(!draylar.inmis.compat.CuriosCompat.tryEquipBackpack(player, recovered),
                            "Use-to-equip bypassed the native Curios equip denial");
                    check(nativeDenials[0] > 0, "Use-to-equip did not consult the native Curios equip event");
                    check(recovered.getCount() == 1 && packed(recovered, Items.DIAMOND) == 7
                                    && stacks.getStackInSlot(0).isEmpty(),
                            "Denied native equip changed the held backpack or filled the back slot");
                } finally {
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(denyEquip);
                }
            } finally {
                player.remove();
                Inmis.CONFIG = previous;
            }
        }
    }

    private static FakePlayer player(SmokeContext context) {
        FakePlayer player = new FakePlayer(context.getLevel(), new GameProfile(UUID.randomUUID(), "InmisLegacy"));
        BlockPos position = context.absolutePos(new BlockPos(1, 1, 1));
        player.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0, 0);
        player.inventory.clearContent();
        player.abilities.instabuild = false;
        return player;
    }

    private static void checkNativeChestAdmission(FakePlayer player) {
        boolean previousArmor = Inmis.CONFIG.allowBackpacksInChestplate;
        boolean previousCompatibility = Inmis.CONFIG.enableTrinketCompatibility;
        boolean previousRequireEmpty = Inmis.CONFIG.requireEmptyForUnequip;
        try {
            // Isolate vanilla armor admission from Curios' shift-click routing.
            Inmis.CONFIG.enableTrinketCompatibility = false;
            Inmis.CONFIG.requireEmptyForUnequip = false;
            Inmis.CONFIG.allowBackpacksInChestplate = true;
            ItemStack bag = quiver(true);
            bag.setHoverName(new StringTextComponent("Inmis filled armor backpack"));
            BackpackInventory contents = inventory(bag);
            for (int slot = 0; slot < contents.getContainerSize(); slot++) {
                contents.setItem(slot, new ItemStack(Items.STONE, 64));
            }
            contents.setItem(0, new ItemStack(Items.DIAMOND, 7));
            ItemStack armor = new ItemStack(Items.NETHERITE_CHESTPLATE);
            armor.setHoverName(new StringTextComponent("Inmis stored armor"));
            armor.setDamageValue(23);
            armor.enchant(net.minecraft.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, 3);
            contents.setItem(1, armor);
            ItemStack expected = bag.copy();
            net.minecraft.inventory.container.PlayerContainer menu = player.inventoryMenu;
            net.minecraft.inventory.container.Slot chest = menu.getSlot(6);

            check(chest.mayPlace(bag), "Enabled native chest slot rejected a filled backpack");
            player.inventory.setCarried(bag);
            menu.clicked(6, 0, net.minecraft.inventory.container.ClickType.PICKUP, player);
            check(ItemStack.matches(expected, chest.getItem()) && player.inventory.getCarried().isEmpty()
                            && ItemStack.matches(expected, player.inventory.getItem(38)),
                    "Native chest drag lost backpack contents, augments or metadata");

            menu.clicked(6, 0, net.minecraft.inventory.container.ClickType.PICKUP, player);
            menu.clicked(9, 0, net.minecraft.inventory.container.ClickType.PICKUP, player);
            check(chest.getItem().isEmpty() && ItemStack.matches(expected, menu.getSlot(9).getItem())
                            && player.inventory.getCarried().isEmpty(),
                    "Native chest removal did not preserve the filled backpack");
            check(!menu.quickMoveStack(player, 9).isEmpty() && menu.getSlot(9).getItem().isEmpty()
                            && ItemStack.matches(expected, chest.getItem()) && nativeBackpackCount(player) == 1,
                    "Native shift equip did not preserve exactly one filled backpack");
            check(!menu.quickMoveStack(player, 6).isEmpty() && chest.getItem().isEmpty()
                            && ItemStack.matches(expected, menu.getSlot(9).getItem()) && nativeBackpackCount(player) == 1,
                    "Native shift unequip lost or duplicated the filled backpack");

            Inmis.CONFIG.allowBackpacksInChestplate = false;
            check(!chest.mayPlace(expected), "Disabled native chest slot admits a backpack");
            menu.quickMoveStack(player, 9);
            check(chest.getItem().isEmpty() && ItemStack.matches(expected, menu.getSlot(36).getItem())
                            && nativeBackpackCount(player) == 1,
                    "Disabled shift equip inserted a backpack into armor or changed its contents");
            menu.clicked(36, 0, net.minecraft.inventory.container.ClickType.PICKUP, player);
            menu.clicked(6, 0, net.minecraft.inventory.container.ClickType.PICKUP, player);
            check(chest.getItem().isEmpty() && ItemStack.matches(expected, player.inventory.getCarried())
                            && nativeBackpackCount(player) == 0,
                    "Disabled chest drag changed or equipped the carried backpack");
        } finally {
            player.inventory.setCarried(ItemStack.EMPTY);
            player.inventory.clearContent();
            Inmis.CONFIG.allowBackpacksInChestplate = previousArmor;
            Inmis.CONFIG.enableTrinketCompatibility = previousCompatibility;
            Inmis.CONFIG.requireEmptyForUnequip = previousRequireEmpty;
        }
    }

    private static int nativeBackpackCount(FakePlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.inventory.getContainerSize(); slot++) {
            ItemStack stack = player.inventory.getItem(slot);
            if (stack.getItem() instanceof BackpackItem) count += stack.getCount();
        }
        return count;
    }

    private static GroundedArrow arrow(SmokeContext context, AbstractArrowEntity.PickupStatus pickup) {
        BlockPos position = context.absolutePos(new BlockPos(1, 1, 1));
        GroundedArrow arrow = new GroundedArrow(context.getLevel(), position.getX() + 0.5,
                position.getY(), position.getZ() + 0.5);
        arrow.pickup = pickup;
        context.getLevel().addFreshEntity(arrow);
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
        return player.inventory.items.stream().filter(entry -> entry.getItem() == item)
                .mapToInt(ItemStack::getCount).sum();
    }

    private static void fillPlayerInventory(FakePlayer player) {
        for (int slot = 0; slot < 36; slot++) player.inventory.setItem(slot, new ItemStack(Items.STONE, 64));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class GroundedArrow extends ArrowEntity {
        private GroundedArrow(ServerWorld world, double x, double y, double z) {
            super(world, x, y, z);
            inGround = true;
            shakeTime = 0;
        }

        private void setGrounded(boolean grounded) { inGround = grounded; }
    }

    private static final class ReturningTrident extends TridentEntity {
        private ReturningTrident(ServerWorld world, FakePlayer owner, ItemStack stack) {
            super(world, owner, stack);
            inGround = false;
            setNoPhysics(true);
            shakeTime = 0;
            pickup = AbstractArrowEntity.PickupStatus.ALLOWED;
        }
    }
}
