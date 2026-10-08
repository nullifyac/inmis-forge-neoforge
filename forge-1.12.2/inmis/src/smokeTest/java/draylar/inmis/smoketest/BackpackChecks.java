package draylar.inmis.smoketest;

import com.mojang.authlib.GameProfile;
import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.util.DamageSource;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.InventoryCrafting;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.init.Enchantments;
import net.minecraft.world.GameRules;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
public final class BackpackChecks {
    public static void occupiedOffhandImmortalConsumesAndPersists(SmokeContext helper) {
        EntityPlayerMP player = player(helper);
        ItemStack backpack = backpack("withered", false);
        player.inventory.setInventorySlotContents(0, backpack);
        inventory(backpack).setInventorySlotContents(0, new ItemStack(Items.TOTEM_OF_UNDYING));
        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        // Main hand occupies slot 0, so keep the backpack in another inventory slot.
        player.inventory.setInventorySlotContents(1, backpack);
        ItemStack shield = new ItemStack(Items.SHIELD);
        player.setHeldItem(EnumHand.OFF_HAND, shield);

        lethal(player, DamageSource.GENERIC);
        check(helper, player.getHealth() == 1.0F, "Occupied offhand prevented Immortal resurrection");
        check(helper, player.getHeldItem(EnumHand.OFF_HAND) == shield, "Immortal replaced the shield");
        check(helper, backpackCount(backpack, Items.TOTEM_OF_UNDYING) == 0, "Backpack totem was not consumed");
        ItemStack reloaded = new ItemStack(backpack.writeToNBT(new NBTTagCompound()));
        check(helper, backpackCount(reloaded, Items.TOTEM_OF_UNDYING) == 0, "Consumed totem returned after serialization");
        lethal(player, DamageSource.GENERIC);
        check(helper, (player.getHealth() <= 0), "A consumed backpack totem protected a second death");
        helper.succeed();
    }
    public static void heldTotemsKeepPriority(SmokeContext helper) {
        for (EnumHand hand : EnumHand.values()) {
            EntityPlayerMP player = player(helper);
            ItemStack backpack = backpack("withered", false);
            player.inventory.setInventorySlotContents(1, backpack);
            inventory(backpack).setInventorySlotContents(0, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.setHeldItem(hand, new ItemStack(Items.TOTEM_OF_UNDYING));
            lethal(player, DamageSource.GENERIC);
            check(helper, player.getHealth() == 1.0F, "Held totem failed to resurrect");
            check(helper, player.getHeldItem(hand).isEmpty(), "Held totem did not take priority");
            check(helper, backpackCount(backpack, Items.TOTEM_OF_UNDYING) == 1, "Held totem also consumed a backpack totem");
        }
        helper.succeed();
    }
    public static void disabledAndBypassDamageDoNotUseImmortal(SmokeContext helper) {
        EntityPlayerMP disabled = player(helper);
        ItemStack disabledBackpack = backpack("withered", false);
        Inmis.setBackpackAugments(disabledBackpack,
                Inmis.getOrCreateAugments(disabledBackpack, ((BackpackItem) disabledBackpack.getItem()).getTier())
                        .withImmortalEnabled(false));
        disabled.inventory.setInventorySlotContents(1, disabledBackpack);
        inventory(disabledBackpack).setInventorySlotContents(0, new ItemStack(Items.TOTEM_OF_UNDYING));
        lethal(disabled, DamageSource.GENERIC);
        check(helper, (disabled.getHealth() <= 0), "Disabled Immortal still resurrected");
        check(helper, backpackCount(disabledBackpack, Items.TOTEM_OF_UNDYING) == 1, "Disabled Immortal consumed a totem");

        EntityPlayerMP bypass = player(helper);
        ItemStack bypassBackpack = backpack("withered", false);
        bypass.inventory.setInventorySlotContents(1, bypassBackpack);
        inventory(bypassBackpack).setInventorySlotContents(0, new ItemStack(Items.TOTEM_OF_UNDYING));
        lethal(bypass, DamageSource.OUT_OF_WORLD);
        check(helper, (bypass.getHealth() <= 0), "Immortal bypassed vanilla void death rules");
        check(helper, backpackCount(bypassBackpack, Items.TOTEM_OF_UNDYING) == 1, "Bypass damage consumed a totem");
        helper.succeed();
    }
    public static void funnelPickupRejectsRestrictedItems(SmokeContext helper) {
        boolean originalShulkers = Inmis.CONFIG.disableShulkers;
        boolean originalUnstackables = Inmis.CONFIG.unstackablesOnly;
        try {
            Inmis.CONFIG.disableShulkers = true;
            Inmis.CONFIG.unstackablesOnly = false;
            EntityPlayerMP player = player(helper);
            fillPlayerInventory(player);
            ItemStack backpack = backpack("frayed", true);
            player.inventory.setInventorySlotContents(0, backpack);
            assertRejected(helper, player, backpack, new ItemStack(backpack("frayed", false).getItem()));
            assertRejected(helper, player, backpack, new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.PURPLE_SHULKER_BOX)));
            Inmis.CONFIG.unstackablesOnly = true;
            assertRejected(helper, player, backpack, new ItemStack(Items.DIAMOND, 3));
            Inmis.CONFIG.unstackablesOnly = false;
            EntityItem allowed = dropped(helper, new ItemStack(Items.DIAMOND, 10));
            allowed.onCollideWithPlayer(player);
            check(helper, allowed.isDead, "Allowed pickup was not fully funnelled");
            check(helper, backpackCount(backpack, Items.DIAMOND) == 10, "Allowed funnel pickup lost items");
            check(helper, backpackCount(backpack, backpack.getItem()) == 0, "Funnelling nested another backpack");
        } finally {
            Inmis.CONFIG.disableShulkers = originalShulkers;
            Inmis.CONFIG.unstackablesOnly = originalUnstackables;
        }
        helper.succeed();
    }
    public static void partialFunnelPickupConservesWorldRemainder(SmokeContext helper) {
        EntityPlayerMP player = player(helper);
        fillPlayerInventory(player);
        ItemStack backpack = backpack("frayed", true);
        player.inventory.setInventorySlotContents(0, backpack);
        BackpackInventory inventory = inventory(backpack);
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            inventory.setInventorySlotContents(slot, new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE), 64));
        }
        inventory.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 63));
        EntityItem dropped = dropped(helper, new ItemStack(Items.DIAMOND, 10));
        dropped.onCollideWithPlayer(player);
        check(helper, backpackCount(backpack, Items.DIAMOND) == 64, "Partial funnel did not fill the existing stack");
        check(helper, !dropped.isDead && dropped.getItem().getCount() == 9, "Partial funnel lost the world remainder");
        check(helper, playerCount(player, Items.DIAMOND) == 0, "Full player inventory unexpectedly received diamonds");
        check(helper, backpackCount(backpack, Items.DIAMOND) + dropped.getItem().getCount() == 73, "Partial pickup duplicated or lost diamonds");
        dropped.setDead();
        helper.succeed();
    }
    public static void openMenuUsesLiveFunnelInventory(SmokeContext helper) {
        EntityPlayerMP player = player(helper);
        ItemStack backpack = backpack("frayed", true);
        player.inventory.setInventorySlotContents(0, backpack);
        BackpackScreenHandler menu = new BackpackScreenHandler(1, player.inventory, backpack);
        player.openContainer = menu;
        menu.getBackpackInventory().setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 2));
        EntityItem dropped = dropped(helper, new ItemStack(Items.DIAMOND, 5));
        dropped.onCollideWithPlayer(player);
        check(helper, dropped.isDead, "Funnel pickup with an open menu failed");
        check(helper, menu.inventorySlots.get(0).getStack().getCount() == 7, "Open menu retained a stale inventory after pickup");
        check(helper, backpackCount(backpack, Items.DIAMOND) == 7, "Live menu pickup did not save");
        menu.transferStackInSlot(player, 0);
        menu.getBackpackInventory().markDirty();
        check(helper, backpackCount(backpack, Items.DIAMOND) == 0, "Open menu restored extracted items from stale data");
        check(helper, playerCount(player, Items.DIAMOND) == 7, "Menu extraction duplicated or lost diamonds");
        check(helper, backpackCount(new ItemStack(backpack.writeToNBT(new NBTTagCompound())), Items.DIAMOND) == 0,
                "Extracted items returned after serialization");
        player.openContainer = player.inventoryContainer;
        helper.succeed();
    }
    public static void recoveryRowsAllowExtractionAndLockOpenBackpack(SmokeContext helper) {
        EntityPlayerMP player = player(helper);
        ItemStack backpack = backpack("frayed", false);
        player.inventory.setInventorySlotContents(0, backpack);
        saveSlot(backpack, 12, new ItemStack(Items.DIAMOND_CHESTPLATE));
        ItemStack nested = backpack("frayed", false);
        saveSlot(backpack, 13, nested);
        BackpackScreenHandler menu = new BackpackScreenHandler(1, player.inventory, backpack);
        player.openContainer = menu;
        check(helper, menu.getBackpackInventory().getSizeInventory() >= 14, "Saved overflow was truncated");
        check(helper, !menu.inventorySlots.get(12).isItemValid(new ItemStack(Items.DIAMOND)), "Recovery row accepted new items");
        menu.transferStackInSlot(player, 12);
        check(helper, playerCount(player, Items.DIAMOND_CHESTPLATE) == 1, "Recovery armor could not be extracted");
        check(helper, backpackCount(backpack, Items.DIAMOND_CHESTPLATE) == 0, "Recovery extraction did not persist");
        menu.transferStackInSlot(player, 13);
        check(helper, playerCount(player, backpack.getItem()) == 2, "Previously nested backpack remained stuck");
        check(helper, backpackCount(backpack, backpack.getItem()) == 0, "Nested backpack extraction did not persist");
        int openSlot = menu.getBackpackInventory().getSizeInventory() + 27;
        check(helper, menu.transferStackInSlot(player, openSlot).isEmpty(), "Shift click moved the open backpack");
        menu.slotClick(0, 0, ClickType.SWAP, player);
        menu.slotClick(openSlot, 0, ClickType.THROW, player);
        check(helper, player.inventory.getStackInSlot(0) == backpack, "Swap or drop moved the open backpack");
        check(helper, menu.canInteractWith(player), "Active backpack menu lost its owner");
        player.openContainer = player.inventoryContainer;
        helper.succeed();
    }
    public static void realUpgradeRecipePreservesOverflow(SmokeContext helper) {
        EntityPlayerMP player = player(helper);
        ItemStack backpack = backpack("frayed", true);
        saveSlot(backpack, 20, new ItemStack(Items.DIAMOND, 7));
        BackpackScreenHandler owner = new BackpackScreenHandler(1, player.inventory, backpack);
        InventoryCrafting crafting = new InventoryCrafting(owner, 3, 3);
        for (int i = 0; i < 9; i++) {
            crafting.setInventorySlotContents(i, i == 4 ? backpack : new ItemStack(Items.IRON_INGOT));
        }
        net.minecraft.item.crafting.IRecipe recipe = net.minecraft.item.crafting.CraftingManager.REGISTRY.getObject(Inmis.id("plated_backpack"));
        if (recipe == null) throw new AssertionError("Required recipe missing");
        check(helper, recipe.matches(crafting, helper.getLevel()), "Loaded plated upgrade recipe did not match");
        ItemStack upgraded = recipe.getCraftingResult(crafting);
        check(helper, ((BackpackItem) upgraded.getItem()).getTier().getName().equals("plated"), "Upgrade returned wrong backpack");
        check(helper, backpackCount(upgraded, Items.DIAMOND) == 7, "Upgrade truncated saved overflow");
        check(helper, inventory(upgraded).getStackInSlot(20).getCount() == 7, "Upgrade changed the saved recovery slot");
        check(helper, Inmis.getOrCreateAugments(upgraded, ((BackpackItem) upgraded.getItem()).getTier()).funnelling().enabled(),
                "Upgrade dropped augment settings");
        helper.succeed();
    }
    public static void deathSpillingPreservesMainOffhandAndArmor(SmokeContext helper) {
        boolean originalMainSpill = Inmis.CONFIG.spillMainBackpacksOnDeath;
        boolean originalArmorSpill = Inmis.CONFIG.spillArmorBackpacksOnDeath;
        boolean originalKeepInventory = helper.getLevel().getGameRules().getBoolean("keepInventory");
        try {
            helper.getLevel().getGameRules().setOrCreateGameRule("keepInventory", Boolean.toString(false));
            // Vanilla inventory indices: main inventory, offhand, chest armor.
            for (int sourceSlot : new int[]{1, 40, 38}) {
                Inmis.CONFIG.spillMainBackpacksOnDeath = sourceSlot != 38;
                Inmis.CONFIG.spillArmorBackpacksOnDeath = sourceSlot == 38;
                EntityPlayerMP player = player(helper, true);
                ItemStack backpack = backpack("frayed", false);
                inventory(backpack).setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 7));
                player.inventory.setInventorySlotContents(sourceSlot, backpack);
                List<EntityItem> drops = java.util.Collections.emptyList();
                try {
                    lethal(player, DamageSource.GENERIC);
                    drops = helper.getLevel().getEntitiesWithinAABB(EntityItem.class, player.getEntityBoundingBox().grow(2),
                            entity -> !entity.isDead);
                    int looseDiamonds = drops.stream().filter(entity -> (entity.getItem().getItem() == Items.DIAMOND))
                            .mapToInt(entity -> entity.getItem().getCount()).sum();
                    List<EntityItem> backpacks = drops.stream()
                            .filter(entity -> entity.getItem().getItem() instanceof BackpackItem).collect(java.util.stream.Collectors.toList());
                    int storedDiamonds = backpacks.stream()
                            .mapToInt(entity -> backpackCount(entity.getItem(), Items.DIAMOND)).sum();
                    check(helper, (player.getHealth() <= 0), "Death fixture did not reach vanilla death drops");
                    check(helper, looseDiamonds + storedDiamonds + playerCount(player, Items.DIAMOND) == 7,
                            "Death spilling lost or duplicated diamonds in source slot " + sourceSlot);
                    check(helper, looseDiamonds == 7, "Death did not spill backpack contents from source slot " + sourceSlot);
                    check(helper, backpacks.size() == 1 && Inmis.isBackpackEmpty(backpacks.get(0).getItem()),
                            "Death did not drop exactly one empty backpack from source slot " + sourceSlot);
                } finally {
                    drops.forEach(EntityItem::setDead);
                }
            }
        } finally {
            Inmis.CONFIG.spillMainBackpacksOnDeath = originalMainSpill;
            Inmis.CONFIG.spillArmorBackpacksOnDeath = originalArmorSpill;
            helper.getLevel().getGameRules().setOrCreateGameRule("keepInventory", Boolean.toString(originalKeepInventory));
        }
        helper.succeed();
    }
    public static void deathSpillingRespectsRetentionFlagsAndVanishing(SmokeContext helper) {
        boolean originalMainSpill = Inmis.CONFIG.spillMainBackpacksOnDeath;
        boolean originalArmorSpill = Inmis.CONFIG.spillArmorBackpacksOnDeath;
        boolean originalKeepInventory = helper.getLevel().getGameRules().getBoolean("keepInventory");
        try {
            for (int scenario = 0; scenario < 5; scenario++) {
                boolean keepInventory = scenario == 0;
                Inmis.CONFIG.spillMainBackpacksOnDeath = scenario == 0 || scenario == 3 || scenario == 4;
                Inmis.CONFIG.spillArmorBackpacksOnDeath = scenario == 0 || scenario == 2;
                helper.getLevel().getGameRules().setOrCreateGameRule("keepInventory", Boolean.toString(keepInventory));
                EntityPlayerMP player = player(helper, true);
                ItemStack backpack = backpack("frayed", false);
                inventory(backpack).setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 7));
                if (scenario == 4) {
                    backpack.addEnchantment(Enchantments.VANISHING_CURSE, 1);
                }
                int sourceSlot = scenario == 3 ? 38 : 1;
                player.inventory.setInventorySlotContents(sourceSlot, backpack);
                List<EntityItem> drops = java.util.Collections.emptyList();
                try {
                    lethal(player, DamageSource.GENERIC);
                    drops = helper.getLevel().getEntitiesWithinAABB(EntityItem.class, player.getEntityBoundingBox().grow(2),
                            entity -> !entity.isDead);
                    check(helper, drops.stream().noneMatch(entity -> (entity.getItem().getItem() == Items.DIAMOND)),
                            "Retention, disabled/category flags or vanishing curse leaked contents in scenario " + scenario);
                    List<EntityItem> backpacks = drops.stream()
                            .filter(entity -> entity.getItem().getItem() instanceof BackpackItem).collect(java.util.stream.Collectors.toList());
                    if (keepInventory) {
                        check(helper, backpacks.isEmpty() && player.inventory.getStackInSlot(sourceSlot) == backpack
                                        && backpackCount(backpack, Items.DIAMOND) == 7,
                                "keepInventory did not retain the full backpack");
                    } else if (scenario == 4) {
                        check(helper, backpacks.isEmpty(), "Vanishing-cursed backpack was dropped or spilled");
                    } else {
                        check(helper, backpacks.size() == 1 && backpackCount(backpacks.get(0).getItem(), Items.DIAMOND) == 7,
                                "Disabled or unrelated spill flag changed backpack contents in scenario " + scenario);
                    }
                } finally {
                    drops.forEach(EntityItem::setDead);
                }
            }
        } finally {
            Inmis.CONFIG.spillMainBackpacksOnDeath = originalMainSpill;
            Inmis.CONFIG.spillArmorBackpacksOnDeath = originalArmorSpill;
            helper.getLevel().getGameRules().setOrCreateGameRule("keepInventory", Boolean.toString(originalKeepInventory));
        }
        helper.succeed();
    }
    public static void deathSpillingRemainsInsideCancelledDropEvent(SmokeContext helper) {
        boolean originalMainSpill = Inmis.CONFIG.spillMainBackpacksOnDeath;
        boolean originalKeepInventory = helper.getLevel().getGameRules().getBoolean("keepInventory");
        EntityPlayerMP player = player(helper, true);
        int[] captured = new int[3];
        Consumer<PlayerDropsEvent> cancelDrops = event -> {
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
        Object listener = new Object() {
            @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.LOWEST)
            public void drop(PlayerDropsEvent event) { cancelDrops.accept(event); }
        };
        MinecraftForge.EVENT_BUS.register(listener);
        List<EntityItem> drops = java.util.Collections.emptyList();
        try {
            Inmis.CONFIG.spillMainBackpacksOnDeath = true;
            helper.getLevel().getGameRules().setOrCreateGameRule("keepInventory", Boolean.toString(false));
            ItemStack backpack = backpack("frayed", false);
            inventory(backpack).setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 7));
            player.inventory.setInventorySlotContents(1, backpack);
            lethal(player, DamageSource.GENERIC);
            drops = helper.getLevel().getEntitiesWithinAABB(EntityItem.class, player.getEntityBoundingBox().grow(2),
                    entity -> !entity.isDead);
            check(helper, captured[0] == 7 && captured[1] == 1 && captured[2] == 0,
                    "Spilled contents and empty backpack were not captured together");
            check(helper, drops.isEmpty(), "Cancelled death-drop event leaked spilled items into the world");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            drops.forEach(EntityItem::setDead);
            Inmis.CONFIG.spillMainBackpacksOnDeath = originalMainSpill;
            helper.getLevel().getGameRules().setOrCreateGameRule("keepInventory", Boolean.toString(originalKeepInventory));
        }
        helper.succeed();
    }

    private static EntityPlayerMP player(SmokeContext helper) {
        return player(helper, false);
    }

    private static EntityPlayerMP player(SmokeContext helper, boolean dropsOnDeath) {
        NativeTestPlayer player = new NativeTestPlayer(helper.getLevel(), "InmisTest");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPositionAndRotation(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        player.capabilities.isCreativeMode = false;
        player.capabilities.disableDamage = false;
        player.setHealth(20.0F);
        return player;
    }

    private static void lethal(EntityPlayerMP player, DamageSource source) {
        player.clearActivePotions();
        player.setAbsorptionAmount(0);
        player.hurtResistantTime = 0;
        player.setHealth(1.0F);
        boolean accepted = player.attackEntityFrom(source, 100.0F);
        Inmis.LOGGER.info("Native lethal damage: accepted={}, health={}, source={}, invulnerable={}, respawnTicks={}, disableDamage={}, remote={}, class={}",
                accepted, player.getHealth(), source.damageType, player.isEntityInvulnerable(source),
                net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(EntityPlayerMP.class,player,"respawnInvulnerabilityTicks","field_147101_bU"),
                player.capabilities.disableDamage, player.world.isRemote, player.getClass().getName());
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

    private static int playerCount(EntityPlayer player, Item item) {
        return player.inventory.mainInventory.stream().filter(entry -> (entry.getItem() == item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void fillPlayerInventory(EntityPlayer player) {
        for (int i = 0; i < 36; i++) {
            player.inventory.setInventorySlotContents(i, new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE), 64));
        }
    }

    private static EntityItem dropped(SmokeContext helper, ItemStack stack) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        EntityItem entity = new EntityItem(helper.getLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, stack);
        entity.setNoPickupDelay();
        helper.getLevel().spawnEntity(entity);
        return entity;
    }

    private static void assertRejected(SmokeContext helper, EntityPlayer player, ItemStack backpack, ItemStack rejected) {
        int before = rejected.getCount();
        EntityItem entity = dropped(helper, rejected);
        entity.onCollideWithPlayer(player);
        check(helper, !entity.isDead && entity.getItem().getCount() == before, "Restricted item was picked up or lost");
        check(helper, backpackCount(backpack, rejected.getItem()) == 0, "Restricted item entered backpack");
        entity.setDead();
    }

    private static void saveSlot(ItemStack backpack, int slot, ItemStack contents) {
        NBTTagList inventory = Inmis.getOrCreateInventory(backpack).copy();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger("Slot", slot);
        entry.setTag("Stack", contents.writeToNBT(new NBTTagCompound()));
        inventory.appendTag(entry);
        Inmis.tag(backpack).setTag("Inventory", inventory);
    }

    private static void check(SmokeContext helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
