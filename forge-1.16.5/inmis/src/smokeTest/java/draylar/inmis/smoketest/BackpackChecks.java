package draylar.inmis.smoketest;

import com.mojang.authlib.GameProfile;
import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.DamageSource;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.CraftingInventory;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.ShapedRecipe;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.world.GameRules;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
public final class BackpackChecks {
    public static void occupiedOffhandImmortalConsumesAndPersists(SmokeContext helper) {
        ServerPlayerEntity player = player(helper);
        ItemStack backpack = backpack("withered", false);
        player.inventory.setItem(0, backpack);
        inventory(backpack).setItem(0, new ItemStack(Items.TOTEM_OF_UNDYING));
        player.setItemInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        // Main hand occupies slot 0, so keep the backpack in another inventory slot.
        player.inventory.setItem(1, backpack);
        ItemStack shield = new ItemStack(Items.SHIELD);
        player.setItemInHand(Hand.OFF_HAND, shield);

        lethal(player, DamageSource.GENERIC);
        check(helper, player.getHealth() == 1.0F, "Occupied offhand prevented Immortal resurrection");
        check(helper, player.getItemInHand(Hand.OFF_HAND) == shield, "Immortal replaced the shield");
        check(helper, backpackCount(backpack, Items.TOTEM_OF_UNDYING) == 0, "Backpack totem was not consumed");
        ItemStack reloaded = ItemStack.of(backpack.save(new CompoundNBT()));
        check(helper, backpackCount(reloaded, Items.TOTEM_OF_UNDYING) == 0, "Consumed totem returned after serialization");
        lethal(player, DamageSource.GENERIC);
        check(helper, player.isDeadOrDying(), "A consumed backpack totem protected a second death");
        helper.succeed();
    }
    public static void heldTotemsKeepPriority(SmokeContext helper) {
        for (Hand hand : Hand.values()) {
            ServerPlayerEntity player = player(helper);
            ItemStack backpack = backpack("withered", false);
            player.inventory.setItem(1, backpack);
            inventory(backpack).setItem(0, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.setItemInHand(hand, new ItemStack(Items.TOTEM_OF_UNDYING));
            lethal(player, DamageSource.GENERIC);
            check(helper, player.getHealth() == 1.0F, "Held totem failed to resurrect");
            check(helper, player.getItemInHand(hand).isEmpty(), "Held totem did not take priority");
            check(helper, backpackCount(backpack, Items.TOTEM_OF_UNDYING) == 1, "Held totem also consumed a backpack totem");
        }
        helper.succeed();
    }
    public static void disabledAndBypassDamageDoNotUseImmortal(SmokeContext helper) {
        ServerPlayerEntity disabled = player(helper);
        ItemStack disabledBackpack = backpack("withered", false);
        Inmis.setBackpackAugments(disabledBackpack,
                Inmis.getOrCreateAugments(disabledBackpack, ((BackpackItem) disabledBackpack.getItem()).getTier())
                        .withImmortalEnabled(false));
        disabled.inventory.setItem(1, disabledBackpack);
        inventory(disabledBackpack).setItem(0, new ItemStack(Items.TOTEM_OF_UNDYING));
        lethal(disabled, DamageSource.GENERIC);
        check(helper, disabled.isDeadOrDying(), "Disabled Immortal still resurrected");
        check(helper, backpackCount(disabledBackpack, Items.TOTEM_OF_UNDYING) == 1, "Disabled Immortal consumed a totem");

        ServerPlayerEntity bypass = player(helper);
        ItemStack bypassBackpack = backpack("withered", false);
        bypass.inventory.setItem(1, bypassBackpack);
        inventory(bypassBackpack).setItem(0, new ItemStack(Items.TOTEM_OF_UNDYING));
        lethal(bypass, DamageSource.OUT_OF_WORLD);
        check(helper, bypass.isDeadOrDying(), "Immortal bypassed vanilla void death rules");
        check(helper, backpackCount(bypassBackpack, Items.TOTEM_OF_UNDYING) == 1, "Bypass damage consumed a totem");
        helper.succeed();
    }
    public static void funnelPickupRejectsRestrictedItems(SmokeContext helper) {
        boolean originalShulkers = Inmis.CONFIG.disableShulkers;
        boolean originalUnstackables = Inmis.CONFIG.unstackablesOnly;
        try {
            Inmis.CONFIG.disableShulkers = true;
            Inmis.CONFIG.unstackablesOnly = false;
            ServerPlayerEntity player = player(helper);
            fillPlayerInventory(player);
            ItemStack backpack = backpack("frayed", true);
            player.inventory.setItem(0, backpack);
            assertRejected(helper, player, backpack, new ItemStack(backpack("frayed", false).getItem()));
            assertRejected(helper, player, backpack, new ItemStack(Items.SHULKER_BOX));
            Inmis.CONFIG.unstackablesOnly = true;
            assertRejected(helper, player, backpack, new ItemStack(Items.DIAMOND, 3));
            Inmis.CONFIG.unstackablesOnly = false;
            ItemEntity allowed = dropped(helper, new ItemStack(Items.DIAMOND, 10));
            allowed.playerTouch(player);
            check(helper, allowed.removed, "Allowed pickup was not fully funnelled");
            check(helper, backpackCount(backpack, Items.DIAMOND) == 10, "Allowed funnel pickup lost items");
            check(helper, backpackCount(backpack, backpack.getItem()) == 0, "Funnelling nested another backpack");
        } finally {
            Inmis.CONFIG.disableShulkers = originalShulkers;
            Inmis.CONFIG.unstackablesOnly = originalUnstackables;
        }
        helper.succeed();
    }
    public static void partialFunnelPickupConservesWorldRemainder(SmokeContext helper) {
        ServerPlayerEntity player = player(helper);
        fillPlayerInventory(player);
        ItemStack backpack = backpack("frayed", true);
        player.inventory.setItem(0, backpack);
        BackpackInventory inventory = inventory(backpack);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            inventory.setItem(slot, new ItemStack(Items.STONE, 64));
        }
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 63));
        ItemEntity dropped = dropped(helper, new ItemStack(Items.DIAMOND, 10));
        dropped.playerTouch(player);
        check(helper, backpackCount(backpack, Items.DIAMOND) == 64, "Partial funnel did not fill the existing stack");
        check(helper, !dropped.removed && dropped.getItem().getCount() == 9, "Partial funnel lost the world remainder");
        check(helper, playerCount(player, Items.DIAMOND) == 0, "Full player inventory unexpectedly received diamonds");
        check(helper, backpackCount(backpack, Items.DIAMOND) + dropped.getItem().getCount() == 73, "Partial pickup duplicated or lost diamonds");
        dropped.remove();
        helper.succeed();
    }
    public static void openMenuUsesLiveFunnelInventory(SmokeContext helper) {
        ServerPlayerEntity player = player(helper);
        ItemStack backpack = backpack("frayed", true);
        player.inventory.setItem(0, backpack);
        BackpackScreenHandler menu = new BackpackScreenHandler(1, player.inventory, backpack);
        player.containerMenu = menu;
        menu.getBackpackInventory().setItem(0, new ItemStack(Items.DIAMOND, 2));
        ItemEntity dropped = dropped(helper, new ItemStack(Items.DIAMOND, 5));
        dropped.playerTouch(player);
        check(helper, dropped.removed, "Funnel pickup with an open menu failed");
        check(helper, menu.slots.get(0).getItem().getCount() == 7, "Open menu retained a stale inventory after pickup");
        check(helper, backpackCount(backpack, Items.DIAMOND) == 7, "Live menu pickup did not save");
        menu.quickMoveStack(player, 0);
        menu.getBackpackInventory().setChanged();
        check(helper, backpackCount(backpack, Items.DIAMOND) == 0, "Open menu restored extracted items from stale data");
        check(helper, playerCount(player, Items.DIAMOND) == 7, "Menu extraction duplicated or lost diamonds");
        check(helper, backpackCount(ItemStack.of(backpack.save(new CompoundNBT())), Items.DIAMOND) == 0,
                "Extracted items returned after serialization");
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }
    public static void recoveryRowsAllowExtractionAndLockOpenBackpack(SmokeContext helper) {
        ServerPlayerEntity player = player(helper);
        ItemStack backpack = backpack("frayed", false);
        player.inventory.setItem(0, backpack);
        saveSlot(backpack, 12, new ItemStack(Items.NETHERITE_CHESTPLATE));
        ItemStack nested = backpack("frayed", false);
        saveSlot(backpack, 13, nested);
        BackpackScreenHandler menu = new BackpackScreenHandler(1, player.inventory, backpack);
        player.containerMenu = menu;
        check(helper, menu.getBackpackInventory().getContainerSize() >= 14, "Saved overflow was truncated");
        check(helper, !menu.slots.get(12).mayPlace(new ItemStack(Items.DIAMOND)), "Recovery row accepted new items");
        menu.quickMoveStack(player, 12);
        check(helper, playerCount(player, Items.NETHERITE_CHESTPLATE) == 1, "Recovery armor could not be extracted");
        check(helper, backpackCount(backpack, Items.NETHERITE_CHESTPLATE) == 0, "Recovery extraction did not persist");
        menu.quickMoveStack(player, 13);
        check(helper, playerCount(player, backpack.getItem()) == 2, "Previously nested backpack remained stuck");
        check(helper, backpackCount(backpack, backpack.getItem()) == 0, "Nested backpack extraction did not persist");
        int openSlot = menu.getBackpackInventory().getContainerSize() + 27;
        check(helper, menu.quickMoveStack(player, openSlot).isEmpty(), "Shift click moved the open backpack");
        menu.clicked(0, 0, ClickType.SWAP, player);
        menu.clicked(openSlot, 0, ClickType.THROW, player);
        check(helper, player.inventory.getItem(0) == backpack, "Swap or drop moved the open backpack");
        check(helper, menu.stillValid(player), "Active backpack menu lost its owner");
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }
    public static void realUpgradeRecipePreservesOverflow(SmokeContext helper) {
        ServerPlayerEntity player = player(helper);
        ItemStack backpack = backpack("frayed", true);
        saveSlot(backpack, 20, new ItemStack(Items.DIAMOND, 7));
        BackpackScreenHandler owner = new BackpackScreenHandler(1, player.inventory, backpack);
        CraftingInventory crafting = new CraftingInventory(owner, 3, 3);
        for (int i = 0; i < 9; i++) {
            crafting.setItem(i, i == 4 ? backpack : new ItemStack(Items.IRON_INGOT));
        }
        ShapedRecipe recipe = (ShapedRecipe) helper.getLevel().getRecipeManager()
                .byKey(Inmis.id("plated_backpack")).orElseThrow(() -> new AssertionError("Required fixture item/recipe is missing"));
        check(helper, recipe.matches(crafting, helper.getLevel()), "Loaded plated upgrade recipe did not match");
        ItemStack upgraded = recipe.assemble(crafting);
        check(helper, ((BackpackItem) upgraded.getItem()).getTier().getName().equals("plated"), "Upgrade returned wrong backpack");
        check(helper, backpackCount(upgraded, Items.DIAMOND) == 7, "Upgrade truncated saved overflow");
        check(helper, inventory(upgraded).getItem(20).getCount() == 7, "Upgrade changed the saved recovery slot");
        check(helper, Inmis.getOrCreateAugments(upgraded, ((BackpackItem) upgraded.getItem()).getTier()).funnelling().enabled(),
                "Upgrade dropped augment settings");
        helper.succeed();
    }
    public static void deathSpillingPreservesMainOffhandAndArmor(SmokeContext helper) {
        boolean originalMainSpill = Inmis.CONFIG.spillMainBackpacksOnDeath;
        boolean originalArmorSpill = Inmis.CONFIG.spillArmorBackpacksOnDeath;
        boolean originalKeepInventory = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        try {
            helper.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY)
                    .set(false, helper.getLevel().getServer());
            // Vanilla inventory indices: main inventory, offhand, chest armor.
            for (int sourceSlot : new int[]{1, 40, 38}) {
                Inmis.CONFIG.spillMainBackpacksOnDeath = sourceSlot != 38;
                Inmis.CONFIG.spillArmorBackpacksOnDeath = sourceSlot == 38;
                ServerPlayerEntity player = player(helper, true);
                ItemStack backpack = backpack("frayed", false);
                inventory(backpack).setItem(0, new ItemStack(Items.DIAMOND, 7));
                player.inventory.setItem(sourceSlot, backpack);
                List<ItemEntity> drops = java.util.Collections.emptyList();
                try {
                    lethal(player, DamageSource.GENERIC);
                    drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(2),
                            entity -> !entity.removed);
                    int looseDiamonds = drops.stream().filter(entity -> (entity.getItem().getItem() == Items.DIAMOND))
                            .mapToInt(entity -> entity.getItem().getCount()).sum();
                    List<ItemEntity> backpacks = drops.stream()
                            .filter(entity -> entity.getItem().getItem() instanceof BackpackItem).collect(java.util.stream.Collectors.toList());
                    int storedDiamonds = backpacks.stream()
                            .mapToInt(entity -> backpackCount(entity.getItem(), Items.DIAMOND)).sum();
                    check(helper, player.isDeadOrDying(), "Death fixture did not reach vanilla death drops");
                    check(helper, looseDiamonds + storedDiamonds + playerCount(player, Items.DIAMOND) == 7,
                            "Death spilling lost or duplicated diamonds in source slot " + sourceSlot);
                    check(helper, looseDiamonds == 7, "Death did not spill backpack contents from source slot " + sourceSlot);
                    check(helper, backpacks.size() == 1 && Inmis.isBackpackEmpty(backpacks.get(0).getItem()),
                            "Death did not drop exactly one empty backpack from source slot " + sourceSlot);
                } finally {
                    drops.forEach(ItemEntity::remove);
                }
            }
        } finally {
            Inmis.CONFIG.spillMainBackpacksOnDeath = originalMainSpill;
            Inmis.CONFIG.spillArmorBackpacksOnDeath = originalArmorSpill;
            helper.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY)
                    .set(originalKeepInventory, helper.getLevel().getServer());
        }
        helper.succeed();
    }
    public static void deathSpillingRespectsRetentionFlagsAndVanishing(SmokeContext helper) {
        boolean originalMainSpill = Inmis.CONFIG.spillMainBackpacksOnDeath;
        boolean originalArmorSpill = Inmis.CONFIG.spillArmorBackpacksOnDeath;
        boolean originalKeepInventory = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        try {
            for (int scenario = 0; scenario < 5; scenario++) {
                boolean keepInventory = scenario == 0;
                Inmis.CONFIG.spillMainBackpacksOnDeath = scenario == 0 || scenario == 3 || scenario == 4;
                Inmis.CONFIG.spillArmorBackpacksOnDeath = scenario == 0 || scenario == 2;
                helper.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY)
                        .set(keepInventory, helper.getLevel().getServer());
                ServerPlayerEntity player = player(helper, true);
                ItemStack backpack = backpack("frayed", false);
                inventory(backpack).setItem(0, new ItemStack(Items.DIAMOND, 7));
                if (scenario == 4) {
                    backpack.enchant(Enchantments.VANISHING_CURSE, 1);
                }
                int sourceSlot = scenario == 3 ? 38 : 1;
                player.inventory.setItem(sourceSlot, backpack);
                List<ItemEntity> drops = java.util.Collections.emptyList();
                try {
                    lethal(player, DamageSource.GENERIC);
                    drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(2),
                            entity -> !entity.removed);
                    check(helper, drops.stream().noneMatch(entity -> (entity.getItem().getItem() == Items.DIAMOND)),
                            "Retention, disabled/category flags or vanishing curse leaked contents in scenario " + scenario);
                    List<ItemEntity> backpacks = drops.stream()
                            .filter(entity -> entity.getItem().getItem() instanceof BackpackItem).collect(java.util.stream.Collectors.toList());
                    if (keepInventory) {
                        check(helper, backpacks.isEmpty() && player.inventory.getItem(sourceSlot) == backpack
                                        && backpackCount(backpack, Items.DIAMOND) == 7,
                                "keepInventory did not retain the full backpack");
                    } else if (scenario == 4) {
                        check(helper, backpacks.isEmpty(), "Vanishing-cursed backpack was dropped or spilled");
                    } else {
                        check(helper, backpacks.size() == 1 && backpackCount(backpacks.get(0).getItem(), Items.DIAMOND) == 7,
                                "Disabled or unrelated spill flag changed backpack contents in scenario " + scenario);
                    }
                } finally {
                    drops.forEach(ItemEntity::remove);
                }
            }
        } finally {
            Inmis.CONFIG.spillMainBackpacksOnDeath = originalMainSpill;
            Inmis.CONFIG.spillArmorBackpacksOnDeath = originalArmorSpill;
            helper.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY)
                    .set(originalKeepInventory, helper.getLevel().getServer());
        }
        helper.succeed();
    }
    public static void deathSpillingRemainsInsideCancelledDropEvent(SmokeContext helper) {
        boolean originalMainSpill = Inmis.CONFIG.spillMainBackpacksOnDeath;
        boolean originalKeepInventory = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        ServerPlayerEntity player = player(helper, true);
        int[] captured = new int[3];
        Consumer<LivingDropsEvent> cancelDrops = event -> {
            if (event.getEntity() == player) {
                captured[0] = event.getDrops().stream().filter(entity -> (entity.getItem().getItem() == Items.DIAMOND))
                        .mapToInt(entity -> entity.getItem().getCount()).sum();
                captured[1] = event.getDrops().stream()
                        .filter(entity -> entity.getItem().getItem() instanceof BackpackItem)
                        .mapToInt(entity -> entity.getItem().getCount()).sum();
                captured[2] = event.getDrops().stream()
                        .filter(entity -> entity.getItem().getItem() instanceof BackpackItem)
                        .mapToInt(entity -> backpackCount(entity.getItem(), Items.DIAMOND)).sum();
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingDropsEvent.class, cancelDrops);
        List<ItemEntity> drops = java.util.Collections.emptyList();
        try {
            Inmis.CONFIG.spillMainBackpacksOnDeath = true;
            helper.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY)
                    .set(false, helper.getLevel().getServer());
            ItemStack backpack = backpack("frayed", false);
            inventory(backpack).setItem(0, new ItemStack(Items.DIAMOND, 7));
            player.inventory.setItem(1, backpack);
            lethal(player, DamageSource.GENERIC);
            drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(2),
                    entity -> !entity.removed);
            check(helper, captured[0] == 7 && captured[1] == 1 && captured[2] == 0,
                    "Spilled contents and empty backpack were not captured together");
            check(helper, drops.isEmpty(), "Cancelled death-drop event leaked spilled items into the world");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancelDrops);
            drops.forEach(ItemEntity::remove);
            Inmis.CONFIG.spillMainBackpacksOnDeath = originalMainSpill;
            helper.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY)
                    .set(originalKeepInventory, helper.getLevel().getServer());
        }
        helper.succeed();
    }

    private static ServerPlayerEntity player(SmokeContext helper) {
        return player(helper, false);
    }

    private static ServerPlayerEntity player(SmokeContext helper, boolean dropsOnDeath) {
        // Forge's normal FakePlayer ignores all damage; enable only the vanilla damage path needed by these tests.
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "InmisTest")) {
            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return false;
            }

            @Override
            public void die(DamageSource source) {
                if (dropsOnDeath) {
                    // Exercise real PlayerEntity.dropEquipment, Forge drop collection and Inmis drop listeners.
                    dropAllDeathLoot(source);
                } else {
                    super.die(source);
                }
            }
        };
        try {
            Field spawnProtection = ServerPlayerEntity.class.getDeclaredField("spawnInvulnerableTime");
            spawnProtection.setAccessible(true);
            spawnProtection.setInt(player, 0);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot remove test player's initial spawn protection", exception);
        }
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        player.abilities.instabuild = false;
        player.abilities.invulnerable = false;
        player.setHealth(20.0F);
        return player;
    }

    private static void lethal(ServerPlayerEntity player, DamageSource source) {
        player.removeAllEffects();
        player.setAbsorptionAmount(0);
        player.invulnerableTime = 0;
        player.setHealth(1.0F);
        player.hurt(source, 100.0F);
    }

    private static ItemStack backpack(String tier, boolean funnel) {
        BackpackItem item = Inmis.BACKPACKS.stream().map(entry -> entry.get())
                .filter(entry -> entry.getTier().getName().equals(tier)).findFirst().orElseThrow(() -> new AssertionError("Required fixture item/recipe is missing"));
        ItemStack stack = new ItemStack(item);
        Inmis.setBackpackAugments(stack, BackpackAugmentsComponent.DEFAULT.withFunnelling(
                new BackpackAugmentsComponent.FunnellingSettings(funnel,
                        BackpackAugmentsComponent.FunnellingSettings.Mode.ALLOW, java.util.Collections.emptyList())));
        return stack;
    }

    private static BackpackInventory inventory(ItemStack stack) {
        return new BackpackInventory(stack, ((BackpackItem) stack.getItem()).getTier());
    }

    private static int backpackCount(ItemStack stack, Item item) {
        return Inmis.getBackpackContents(stack).stream().filter(entry -> (entry.getItem() == item)).mapToInt(ItemStack::getCount).sum();
    }

    private static int playerCount(PlayerEntity player, Item item) {
        return player.inventory.items.stream().filter(entry -> (entry.getItem() == item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void fillPlayerInventory(PlayerEntity player) {
        for (int i = 0; i < 36; i++) {
            player.inventory.setItem(i, new ItemStack(Items.STONE, 64));
        }
    }

    private static ItemEntity dropped(SmokeContext helper, ItemStack stack) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ItemEntity entity = new ItemEntity(helper.getLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, stack);
        entity.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static void assertRejected(SmokeContext helper, PlayerEntity player, ItemStack backpack, ItemStack rejected) {
        int before = rejected.getCount();
        ItemEntity entity = dropped(helper, rejected);
        entity.playerTouch(player);
        check(helper, !entity.removed && entity.getItem().getCount() == before, "Restricted item was picked up or lost");
        check(helper, backpackCount(backpack, rejected.getItem()) == 0, "Restricted item entered backpack");
        entity.remove();
    }

    private static void saveSlot(ItemStack backpack, int slot, ItemStack contents) {
        ListNBT inventory = Inmis.getOrCreateInventory(backpack).copy();
        CompoundNBT entry = new CompoundNBT();
        entry.putInt("Slot", slot);
        entry.put("Stack", contents.save(new CompoundNBT()));
        inventory.add(entry);
        backpack.getOrCreateTag().put("Inventory", inventory);
    }

    private static void check(SmokeContext helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
