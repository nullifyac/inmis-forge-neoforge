package draylar.inmis.augment;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.mixin.AbstractArrowAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

public final class BackpackAugmentHandler {

    private static final ThreadLocal<BackpackInventory> LAST_TOTEM_INVENTORY = new ThreadLocal<>();
    private static final ThreadLocal<BackpackInventory> LAST_QUIVER_INVENTORY = new ThreadLocal<>();

    private BackpackAugmentHandler() {
    }

    public static boolean beforeItemPickup(Player player, ItemEntity entity, UUID target) {
        ItemStack stack = entity.getItem();
        if (stack.isEmpty() || entity.hasPickUpDelay()) {
            return false;
        }
        if (target != null && !target.equals(player.getUUID())) {
            return false;
        }

        FunnelResult result = funnelItemStackIntoBackpacks(player, stack);
        if (result.funnelCount() > 0) {
            player.take(entity, result.funnelCount());
            player.awardStat(net.minecraft.stats.Stats.ITEM_PICKED_UP.get(stack.getItem()), result.funnelCount());
            player.onItemPickup(entity);
        }

        if (!result.hasRemaining() && result.funnelCount() > 0) {
            entity.discard();
            return true;
        }
        return false;
    }

    public static boolean beforeArrowPickup(Player player, AbstractArrow arrow) {
        ItemStack stack = ((AbstractArrowAccessor) arrow).inmis$callGetPickupItem().copy();
        FunnelResult result = funnelItemStackIntoBackpacks(player, stack);
        if (!result.hasRemaining()) {
            return true;
        }
        if (result.funnelCount() > 0) {
            player.getInventory().add(stack);
            return true;
        }
        return false;
    }

    public static void onLootDroppedByEntity(Collection<ItemEntity> drops, Player player) {
        if (drops.isEmpty()) {
            return;
        }
        List<BackpackSnapshot> snapshots = getSnapshotsWithAugments(player, BackpackAugmentType.FUNNELLING, BackpackAugmentType.LOOTBOUND);
        if (snapshots.isEmpty()) {
            return;
        }
        List<BackpackSnapshot> eligible = new ArrayList<>();
        for (BackpackSnapshot snapshot : snapshots) {
            if (snapshot.augments().lootbound().mobs()) {
                eligible.add(snapshot);
            }
        }
        if (!eligible.isEmpty()) {
            funnelDropsIntoBackpacks(drops, player, eligible);
        }
    }

    public static void onLootDroppedByBlock(Collection<ItemEntity> drops, Player player) {
        if (drops.isEmpty()) {
            return;
        }
        List<BackpackSnapshot> snapshots = getSnapshotsWithAugments(player, BackpackAugmentType.FUNNELLING, BackpackAugmentType.LOOTBOUND);
        if (snapshots.isEmpty()) {
            return;
        }
        List<BackpackSnapshot> eligible = new ArrayList<>();
        for (BackpackSnapshot snapshot : snapshots) {
            if (snapshot.augments().lootbound().blocks()) {
                eligible.add(snapshot);
            }
        }
        if (!eligible.isEmpty()) {
            funnelDropsIntoBackpacks(drops, player, eligible);
        }
    }

    public static ItemStack locateAmmunition(Player player, ItemStack weapon, ItemStack ammo) {
        LAST_QUIVER_INVENTORY.remove();
        if (!(weapon.getItem() instanceof ProjectileWeaponItem projectileWeapon)) {
            return ItemStack.EMPTY;
        }
        Predicate<ItemStack> predicate = projectileWeapon.getAllSupportedProjectiles();
        for (BackpackSnapshot snapshot : getSnapshotsWithAugment(player, BackpackAugmentType.QUIVERLINK)) {
            BackpackAugmentsComponent.QuiverlinkSettings settings = snapshot.augments().quiverlink();
            if (!ammo.isEmpty() && settings.priority() != BackpackAugmentsComponent.QuiverlinkSettings.Priority.BACKPACK) {
                continue;
            }
            ItemStack projectile = snapshot.inventory().findFirst(predicate);
            if (!projectile.isEmpty()) {
                LAST_QUIVER_INVENTORY.set(snapshot.inventory());
                return projectile;
            }
        }
        return ItemStack.EMPTY;
    }

    public static void onPlayerPickupExperienceOrb(Player player, ExperienceOrb orb) {
        if (orb.isRemoved()) {
            return;
        }
        for (BackpackSnapshot snapshot : getSnapshotsWithAugment(player, BackpackAugmentType.REFORGE)) {
            BackpackInventory inventory = snapshot.inventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MENDING, stack) <= 0) {
                    continue;
                }
                if (!stack.isDamageableItem() || !stack.isDamaged()) {
                    continue;
                }
                if (stack.getCount() != 1 || stack.getMaxStackSize() != 1) {
                    continue;
                }
                int repairableAmount = orb.getValue() * 2;
                int maxRepairableDamage = Math.min(repairableAmount, stack.getDamageValue());
                stack.setDamageValue(stack.getDamageValue() - maxRepairableDamage);
                inventory.setChanged();
            }
        }
    }

    public static ItemStack locateTotemOfUndying(Player player) {
        LAST_TOTEM_INVENTORY.remove();
        for (BackpackSnapshot snapshot : getSnapshotsWithAugment(player, BackpackAugmentType.IMMORTAL)) {
            BackpackInventory inventory = snapshot.inventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.is(Items.TOTEM_OF_UNDYING)) {
                    LAST_TOTEM_INVENTORY.set(inventory);
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static void finishTotemCheck(boolean used) {
        BackpackInventory inventory = LAST_TOTEM_INVENTORY.get();
        if (inventory != null && used) {
            inventory.setChanged();
        }
        LAST_TOTEM_INVENTORY.remove();
    }

    public static void finishQuiverlinkUse() {
        BackpackInventory inventory = LAST_QUIVER_INVENTORY.get();
        if (inventory != null) {
            inventory.setChanged();
        }
        LAST_QUIVER_INVENTORY.remove();
    }

    public static void onPlayerTick(ServerPlayer player) {
        if (player.tickCount % 5 != 0) {
            return;
        }
        BlockPos pos = player.blockPosition();
        int brightness = player.level.getMaxLocalRawBrightness(pos);
        handleLightweaver(player, pos, brightness);
        handleSeedflow(player, pos);
    }

    public static void onBlockBroken(ServerPlayer player, BlockState state, BlockPos pos) {
        if (!isFullyGrownCrop(state)) {
            return;
        }
        Item seedItem = getCropSeed(player.level, pos, state);
        if (seedItem == null) {
            return;
        }
        for (BackpackSnapshot snapshot : getSnapshotsWithAugment(player, BackpackAugmentType.FARMHAND)) {
            BackpackInventory inventory = snapshot.inventory();
            ItemStack seed = inventory.findFirst(stack -> stack.is(seedItem));
            if (seed.isEmpty()) {
                continue;
            }
            if (!plantSeed((ServerLevel) player.level, player, seed, pos)) {
                continue;
            }
            seed.shrink(1);
            inventory.setChanged();
            break;
        }
    }

    private static void handleLightweaver(ServerPlayer player, BlockPos pos, int brightness) {
        for (BackpackSnapshot snapshot : getSnapshotsWithAugment(player, BackpackAugmentType.LIGHTWEAVER)) {
            BackpackAugmentsComponent.LightweaverSettings settings = snapshot.augments().lightweaver();
            if (brightness > settings.minimumLight()) {
                continue;
            }
            BackpackInventory inventory = snapshot.inventory();
            ItemStack torch = inventory.findFirst(stack -> stack.is(Items.TORCH));
            if (torch.isEmpty()) {
                continue;
            }
            InteractionResult result = PlaceSoundControls.runWithOptions(!settings.placeSound(), true, () ->
                    torch.useOn(UseItemOnBlockFaceContext.create((ServerLevel) player.level, player, torch, pos.below(), Direction.UP)));
            if (result.consumesAction()) {
                inventory.setChanged();
                return;
            }
        }
    }

    private static void handleSeedflow(ServerPlayer player, BlockPos pos) {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(pos.offset(0, 0, 0));
        positions.add(pos.offset(1, 0, 0));
        positions.add(pos.offset(0, 0, 1));
        positions.add(pos.offset(1, 0, 1));

        for (BackpackSnapshot snapshot : getSnapshotsWithAugment(player, BackpackAugmentType.SEEDFLOW)) {
            if (positions.isEmpty()) {
                return;
            }
            BackpackAugmentsComponent.SeedflowSettings settings = snapshot.augments().seedflow();
            BackpackInventory inventory = snapshot.inventory();
            ServerLevel level = (ServerLevel) player.level;
            Function<BlockPos, ItemStack> seedSupplier = settings.randomizeSeeds()
                    ? randomSeedSupplier(level, settings, inventory)
                    : sequentialSeedSupplier(level, settings, inventory);

            boolean changed = false;
            Iterator<BlockPos> iterator = positions.iterator();
            while (iterator.hasNext()) {
                BlockPos targetPos = iterator.next();
                ItemStack seed = seedSupplier.apply(targetPos);
                if (seed.isEmpty()) {
                    break;
                }
                if (plantSeed(level, player, seed, targetPos)) {
                    seed.shrink(1);
                    iterator.remove();
                    changed = true;
                }
                if (positions.isEmpty()) {
                    break;
                }
            }
            if (changed) {
                inventory.setChanged();
            }
        }
    }

    private static Function<BlockPos, ItemStack> sequentialSeedSupplier(ServerLevel level, BackpackAugmentsComponent.SeedflowSettings settings, BackpackInventory inventory) {
        int[] index = {0};
        return pos -> {
            while (index[0] < inventory.getContainerSize()) {
                ItemStack stack = inventory.getItem(index[0]++);
                if (stack.isEmpty()) {
                    continue;
                }
                if (!isPlantableSeed(stack.getItem())) {
                    continue;
                }
                if (settings.useFilters() && !isFilterMatch(stack, settings.filters())) {
                    continue;
                }
                return stack;
            }
            return ItemStack.EMPTY;
        };
    }

    private static Function<BlockPos, ItemStack> randomSeedSupplier(ServerLevel level, BackpackAugmentsComponent.SeedflowSettings settings, BackpackInventory inventory) {
        return pos -> {
            int count = 0;
            ItemStack result = ItemStack.EMPTY;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                if (!isPlantableSeed(stack.getItem())) {
                    continue;
                }
                if (settings.useFilters() && !isFilterMatch(stack, settings.filters())) {
                    continue;
                }
                count++;
                if (level.random.nextInt(count) == 0) {
                    result = stack;
                }
            }
            return result;
        };
    }

    private static boolean plantSeed(ServerLevel level, ServerPlayer player, ItemStack seed, BlockPos pos) {
        if (!(seed.getItem() instanceof BlockItem item)) {
            return false;
        }
        InteractionResult result = PlaceSoundControls.runWithOptions(false, true, () ->
                item.useOn(UseItemOnBlockFaceContext.create(level, player, seed, pos.below(), Direction.UP)));
        return result.consumesAction();
    }

    private static boolean isPlantableSeed(Item item) {
        if (!(item instanceof BlockItem blockItem)) {
            return false;
        }
        return blockItem.getBlock() instanceof BushBlock;
    }

    private static boolean isFilterMatch(ItemStack stack, List<ResourceLocation> filters) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && filters.contains(id);
    }

    private static boolean isFullyGrownCrop(BlockState state) {
        if (!(state.getBlock() instanceof BushBlock)) {
            return false;
        }
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integerProperty && property.getName().equals("age")) {
                int max = integerProperty.getPossibleValues().stream().max(Integer::compareTo).orElse(0);
                return state.getValue(integerProperty) == max;
            }
        }
        return false;
    }

    private static Item getCropSeed(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof BushBlock bush) {
            return bush.getCloneItemStack(level, pos, state).getItem();
        }
        return null;
    }

    private static FunnelResult funnelItemStackIntoBackpacks(Player player, ItemStack stack) {
        List<BackpackSnapshot> snapshots = getSnapshotsWithAugment(player, BackpackAugmentType.FUNNELLING);
        if (snapshots.isEmpty()) {
            return FunnelResult.IGNORE;
        }

        int funnelCount = 0;
        for (BackpackSnapshot snapshot : snapshots) {
            BackpackAugmentsComponent.FunnellingSettings settings = snapshot.augments().funnelling();
            if (!passesFunnellingFilters(stack, settings)) {
                continue;
            }
            int beforeCount = stack.getCount();
            ItemStack remaining = snapshot.inventory().addItem(stack);
            stack.setCount(remaining.getCount());
            funnelCount += (beforeCount - remaining.getCount());
            if (stack.isEmpty()) {
                break;
            }
        }
        return new FunnelResult(!stack.isEmpty(), funnelCount);
    }

    private static boolean passesFunnellingFilters(ItemStack stack, BackpackAugmentsComponent.FunnellingSettings settings) {
        if (settings.filters().isEmpty()) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        boolean matched = id != null && settings.filters().contains(id);
        return settings.mode() == BackpackAugmentsComponent.FunnellingSettings.Mode.ALLOW ? matched : !matched;
    }

    private static void funnelDropsIntoBackpacks(Collection<ItemEntity> drops, Player player, List<BackpackSnapshot> snapshots) {
        drops.removeIf(drop -> {
            ItemStack copy = drop.getItem().copy();
            FunnelResult result = funnelItemStackIntoBackpackSnapshots(player, copy, snapshots);
            drop.setItem(copy);
            return !result.hasRemaining();
        });
    }

    private static FunnelResult funnelItemStackIntoBackpackSnapshots(Player player, ItemStack stack, List<BackpackSnapshot> snapshots) {
        int funnelCount = 0;
        for (BackpackSnapshot snapshot : snapshots) {
            BackpackAugmentsComponent.FunnellingSettings settings = snapshot.augments().funnelling();
            if (!passesFunnellingFilters(stack, settings)) {
                continue;
            }
            int beforeCount = stack.getCount();
            ItemStack remaining = snapshot.inventory().addItem(stack);
            stack.setCount(remaining.getCount());
            funnelCount += (beforeCount - remaining.getCount());
            if (stack.isEmpty()) {
                break;
            }
        }
        return new FunnelResult(!stack.isEmpty(), funnelCount);
    }

    private static List<BackpackSnapshot> getSnapshotsWithAugment(Player player, BackpackAugmentType augment) {
        List<BackpackSnapshot> snapshots = new ArrayList<>();
        for (ItemStack stack : BackpackAugmentHelper.getBackpackStacks(player)) {
            if (!(stack.getItem() instanceof BackpackItem backpackItem)) {
                continue;
            }
            BackpackInfo tier = backpackItem.getTier();
            if (!BackpackAugments.isUnlocked(tier, augment)) {
                continue;
            }
            BackpackAugmentsComponent augments = Inmis.getOrCreateAugments(stack, tier);
            if (!isAugmentEnabled(augment, augments)) {
                continue;
            }
            snapshots.add(new BackpackSnapshot(stack, tier, augments, new BackpackInventory(stack, tier)));
        }
        return snapshots;
    }

    public static List<BackpackInventory> getBackpackInventoriesWithAugment(Player player, BackpackAugmentType augment) {
        List<BackpackInventory> inventories = new ArrayList<>();
        for (BackpackSnapshot snapshot : getSnapshotsWithAugment(player, augment)) {
            inventories.add(snapshot.inventory());
        }
        return inventories;
    }

    private static List<BackpackSnapshot> getSnapshotsWithAugments(Player player, BackpackAugmentType first, BackpackAugmentType second) {
        List<BackpackSnapshot> snapshots = new ArrayList<>();
        for (ItemStack stack : BackpackAugmentHelper.getBackpackStacks(player)) {
            if (!(stack.getItem() instanceof BackpackItem backpackItem)) {
                continue;
            }
            BackpackInfo tier = backpackItem.getTier();
            if (!BackpackAugments.isUnlocked(tier, first) || !BackpackAugments.isUnlocked(tier, second)) {
                continue;
            }
            BackpackAugmentsComponent augments = Inmis.getOrCreateAugments(stack, tier);
            if (!isAugmentEnabled(first, augments) || !isAugmentEnabled(second, augments)) {
                continue;
            }
            snapshots.add(new BackpackSnapshot(stack, tier, augments, new BackpackInventory(stack, tier)));
        }
        return snapshots;
    }

    public static boolean hasLootboundBackpacks(Player player) {
        return !getSnapshotsWithAugments(player, BackpackAugmentType.FUNNELLING, BackpackAugmentType.LOOTBOUND).isEmpty();
    }

    private static boolean isAugmentEnabled(BackpackAugmentType augment, BackpackAugmentsComponent augments) {
        return switch (augment) {
            case FUNNELLING -> augments.funnelling().enabled();
            case QUIVERLINK -> augments.quiverlink().enabled();
            case LOOTBOUND -> augments.lootbound().enabled();
            case LIGHTWEAVER -> augments.lightweaver().enabled();
            case SEEDFLOW -> augments.seedflow().enabled();
            case HOPPER_BRIDGE -> augments.hopperBridge().enabled();
            case FARMHAND -> augments.farmhandEnabled();
            case IMBUED_HIDE -> augments.imbuedHideEnabled();
            case IMMORTAL -> augments.immortalEnabled();
            case REFORGE -> augments.reforgeEnabled();
        };
    }

    private record FunnelResult(boolean hasRemaining, int funnelCount) {
        private static final FunnelResult IGNORE = new FunnelResult(false, 0);
    }

    private record BackpackSnapshot(ItemStack stack, BackpackInfo tier, BackpackAugmentsComponent augments, BackpackInventory inventory) {
    }
}
