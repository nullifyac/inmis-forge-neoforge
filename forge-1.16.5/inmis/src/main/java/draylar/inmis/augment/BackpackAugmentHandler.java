package draylar.inmis.augment;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.ui.BackpackScreenHandler;
import draylar.inmis.mixin.AbstractArrowAccessor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.entity.item.ExperienceOrbEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShootableItem;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.BushBlock;
import net.minecraft.block.CropsBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.BlockState;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.Property;
import net.minecraft.util.math.vector.Vector3d;

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

    public static boolean beforeItemPickup(PlayerEntity player, ItemEntity entity, UUID target) {
        ItemStack stack = entity.getItem();
        if (stack.isEmpty() || entity.hasPickUpDelay()) {
            return false;
        }
        if (target != null && !target.equals(player.getUUID())) {
            return false;
        }

        Item pickedUpItem = stack.getItem();
        FunnelResult result = funnelItemStackIntoBackpacks(player, stack);
        if (result.funnelCount() > 0) {
            player.take(entity, result.funnelCount());
            player.awardStat(net.minecraft.stats.Stats.ITEM_PICKED_UP.get(pickedUpItem), result.funnelCount());
            player.onItemPickup(entity);
        }

        if (!result.hasRemaining() && result.funnelCount() > 0) {
            entity.remove();
            return true;
        }
        return false;
    }

    public static boolean beforeArrowPickup(PlayerEntity player, AbstractArrowEntity arrow) {
        if (arrow instanceof TridentEntity) {
            return false;
        }
        ItemStack stack = ((AbstractArrowAccessor) arrow).inmis$callGetPickupItem().copy();
        FunnelResult result = funnelItemStackIntoBackpacks(player, stack);
        if (!result.hasRemaining()) {
            return true;
        }
        if (result.funnelCount() > 0) {
            player.inventory.add(stack);
            return true;
        }
        return false;
    }

    public static void onLootDroppedByEntity(Collection<ItemEntity> drops, PlayerEntity player) {
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

    public static void onLootDroppedByBlock(Collection<ItemEntity> drops, PlayerEntity player) {
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

    public static ItemStack locateAmmunition(PlayerEntity player, ItemStack weapon, ItemStack ammo) {
        LAST_QUIVER_INVENTORY.remove();
        if (!(weapon.getItem() instanceof ShootableItem)) {
            return ItemStack.EMPTY;
        }
        ShootableItem projectileWeapon = (ShootableItem) weapon.getItem();
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

    public static void onPlayerPickupExperienceOrb(PlayerEntity player, ExperienceOrbEntity orb) {
        if (orb.removed) {
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

    public static ItemStack locateTotemOfUndying(PlayerEntity player) {
        LAST_TOTEM_INVENTORY.remove();
        for (BackpackSnapshot snapshot : getSnapshotsWithAugment(player, BackpackAugmentType.IMMORTAL)) {
            BackpackInventory inventory = snapshot.inventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
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

    public static void onPlayerTick(ServerPlayerEntity player) {
        if (player.tickCount % 5 != 0) {
            return;
        }
        BlockPos pos = player.blockPosition();
        int brightness = player.level.getMaxLocalRawBrightness(pos);
        handleLightweaver(player, pos, brightness);
        handleSeedflow(player, pos);
    }

    public static void onBlockBroken(ServerPlayerEntity player, BlockState state, BlockPos pos) {
        if (!isFullyGrownCrop(state)) {
            return;
        }
        Item seedItem = getCropSeed(player.level, pos, state);
        if (seedItem == null) {
            return;
        }
        if (tryReplantCrop(player, pos, seedItem)) {
            return;
        }
        ServerWorld level = (ServerWorld) player.level;
        if (!isReplaceable(level, pos)) {
            BlockPos targetPos = new BlockPos(pos);
            level.getServer().execute(() -> tryReplantCrop(player, targetPos, seedItem));
        }
    }

    private static boolean tryReplantCrop(ServerPlayerEntity player, BlockPos pos, Item seedItem) {
        ServerWorld level = (ServerWorld) player.level;
        if (!isReplaceable(level, pos)) {
            return false;
        }
        for (BackpackSnapshot snapshot : getSnapshotsWithAugment(player, BackpackAugmentType.FARMHAND)) {
            BackpackInventory inventory = snapshot.inventory();
            ItemStack seed = inventory.findFirst(stack -> stack.getItem() == seedItem);
            if (seed.isEmpty()) {
                continue;
            }
            if (!plantSeed((ServerWorld) player.level, player, seed, pos)) {
                continue;
            }
            seed.shrink(1);
            inventory.setChanged();
            return true;
        }
        return false;
    }

    private static void handleLightweaver(ServerPlayerEntity player, BlockPos pos, int brightness) {
        for (BackpackSnapshot snapshot : getSnapshotsWithAugment(player, BackpackAugmentType.LIGHTWEAVER)) {
            BackpackAugmentsComponent.LightweaverSettings settings = snapshot.augments().lightweaver();
            if (brightness > settings.minimumLight()) {
                continue;
            }
            BackpackInventory inventory = snapshot.inventory();
            ItemStack torch = inventory.findFirst(stack -> stack.getItem() == Items.TORCH);
            if (torch.isEmpty()) {
                continue;
            }
            ActionResultType result = PlaceSoundControls.runWithOptions(!settings.placeSound(), true, () ->
                    torch.useOn(UseItemOnBlockFaceContext.create((ServerWorld) player.level, player, torch, pos.below(), Direction.UP)));
            if (result.consumesAction()) {
                inventory.setChanged();
                return;
            }
        }
    }

    private static void handleSeedflow(ServerPlayerEntity player, BlockPos pos) {
        ServerWorld level = (ServerWorld) player.level;
        List<BlockPos> positions = new ArrayList<>();
        Vector3d forward = player.getLookAngle().multiply(1, 0, 1);
        if (forward.lengthSqr() <= 1.0E-6) {
            forward = new Vector3d(0, 0, 1);
        } else {
            forward = forward.normalize();
        }
        Vector3d scanOrigin = player.position().add(forward);
        positions.add(new BlockPos(scanOrigin.x - 0.5, scanOrigin.y + 0.5, scanOrigin.z - 0.5));
        positions.add(new BlockPos(scanOrigin.x + 0.5, scanOrigin.y + 0.5, scanOrigin.z - 0.5));
        positions.add(new BlockPos(scanOrigin.x + 0.5, scanOrigin.y + 0.5, scanOrigin.z + 0.5));
        positions.add(new BlockPos(scanOrigin.x - 0.5, scanOrigin.y + 0.5, scanOrigin.z + 0.5));
        positions.removeIf(targetPos -> !isValidSeedflowTarget(level, targetPos));

        for (BackpackSnapshot snapshot : getSnapshotsWithAugment(player, BackpackAugmentType.SEEDFLOW)) {
            if (positions.isEmpty()) {
                return;
            }
            BackpackAugmentsComponent.SeedflowSettings settings = snapshot.augments().seedflow();
            BackpackInventory inventory = snapshot.inventory();
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

    private static Function<BlockPos, ItemStack> sequentialSeedSupplier(ServerWorld level, BackpackAugmentsComponent.SeedflowSettings settings, BackpackInventory inventory) {
        int[] index = {0};
        return pos -> {
            while (index[0] < inventory.getContainerSize()) {
                ItemStack stack = inventory.getItem(index[0]++);
                if (stack.isEmpty()) {
                    continue;
                }
                if (!isSeedflowPlantable(stack.getItem())) {
                    continue;
                }
                if (settings.useFilters() && !isFilterMatch(stack, settings.filters())) {
                    continue;
                }
                if (!canPlaceCropSeedAt(level, stack, pos)) {
                    continue;
                }
                return stack;
            }
            return ItemStack.EMPTY;
        };
    }

    private static Function<BlockPos, ItemStack> randomSeedSupplier(ServerWorld level, BackpackAugmentsComponent.SeedflowSettings settings, BackpackInventory inventory) {
        return pos -> {
            int count = 0;
            ItemStack result = ItemStack.EMPTY;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                if (!isSeedflowPlantable(stack.getItem())) {
                    continue;
                }
                if (settings.useFilters() && !isFilterMatch(stack, settings.filters())) {
                    continue;
                }
                if (!canPlaceCropSeedAt(level, stack, pos)) {
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

    private static boolean plantSeed(ServerWorld level, ServerPlayerEntity player, ItemStack seed, BlockPos pos) {
        if (!(seed.getItem() instanceof BlockItem)) {
            return false;
        }
        BlockItem item = (BlockItem) seed.getItem();
        ActionResultType result = PlaceSoundControls.runWithOptions(false, true, () ->
                item.useOn(UseItemOnBlockFaceContext.create(level, player, seed, pos.below(), Direction.UP)));
        return result.consumesAction();
    }

    private static boolean isValidSeedflowTarget(ServerWorld level, BlockPos pos) {
        return isReplaceable(level, pos);
    }

    private static boolean isReplaceable(ServerWorld level, BlockPos pos) {
        return level.getBlockState(pos).getMaterial().isReplaceable();
    }

    private static boolean canPlaceCropSeedAt(ServerWorld level, ItemStack stack, BlockPos pos) {
        if (!(stack.getItem() instanceof BlockItem)) {
            return false;
        }
        BlockItem blockItem = (BlockItem) stack.getItem();
        if (!isReplaceable(level, pos)) {
            return false;
        }
        BlockState placement = blockItem.getBlock().defaultBlockState();
        return placement.canSurvive(level, pos);
    }

    private static boolean isSeedflowPlantable(Item item) {
        if (!(item instanceof BlockItem)) {
            return false;
        }
        BlockItem blockItem = (BlockItem) item;
        return isAgeableCrop(blockItem.getBlock());
    }

    private static boolean isAgeableCrop(Block block) {
        if (!(block instanceof BushBlock) || block instanceof SaplingBlock) {
            return false;
        }
        if (block instanceof CropsBlock) {
            return true;
        }
        BlockState cropState = block.defaultBlockState();
        for (Property<?> property : cropState.getProperties()) {
            if (property instanceof IntegerProperty && property.getName().equals("age")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFilterMatch(ItemStack stack, List<ResourceLocation> filters) {
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && filters.contains(id);
    }

    private static boolean isFullyGrownCrop(BlockState state) {
        if (!(state.getBlock() instanceof BushBlock)) {
            return false;
        }
        if (state.getBlock() instanceof CropsBlock) {
            CropsBlock crop = (CropsBlock) state.getBlock();
            return crop.isMaxAge(state);
        }
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty && property.getName().equals("age")) {
                IntegerProperty integerProperty = (IntegerProperty) property;
                int max = integerProperty.getPossibleValues().stream().max(Integer::compareTo).orElse(0);
                return state.getValue(integerProperty) == max;
            }
        }
        return false;
    }

    private static Item getCropSeed(World level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof BushBlock) {
            BushBlock bush = (BushBlock) state.getBlock();
            return bush.getCloneItemStack(level, pos, state).getItem();
        }
        return null;
    }

    private static FunnelResult funnelItemStackIntoBackpacks(PlayerEntity player, ItemStack stack) {
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
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        boolean matched = id != null && settings.filters().contains(id);
        return settings.mode() == BackpackAugmentsComponent.FunnellingSettings.Mode.ALLOW ? matched : !matched;
    }

    private static void funnelDropsIntoBackpacks(Collection<ItemEntity> drops, PlayerEntity player, List<BackpackSnapshot> snapshots) {
        drops.removeIf(drop -> {
            ItemStack copy = drop.getItem().copy();
            FunnelResult result = funnelItemStackIntoBackpackSnapshots(player, copy, snapshots);
            drop.setItem(copy);
            return !result.hasRemaining();
        });
    }

    private static FunnelResult funnelItemStackIntoBackpackSnapshots(PlayerEntity player, ItemStack stack, List<BackpackSnapshot> snapshots) {
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

    private static List<BackpackSnapshot> getSnapshotsWithAugment(PlayerEntity player, BackpackAugmentType augment) {
        List<BackpackSnapshot> snapshots = new ArrayList<>();
        for (ItemStack stack : BackpackAugmentHelper.getBackpackStacks(player)) {
            if (!(stack.getItem() instanceof BackpackItem)) {
                continue;
            }
            BackpackItem backpackItem = (BackpackItem) stack.getItem();
            BackpackInfo tier = backpackItem.getTier();
            if (!BackpackAugments.isUnlocked(tier, augment)) {
                continue;
            }
            BackpackAugmentsComponent augments = Inmis.getOrCreateAugments(stack, tier);
            if (!isAugmentEnabled(augment, augments)) {
                continue;
            }
            snapshots.add(new BackpackSnapshot(stack, tier, augments, getBackpackInventory(player, stack, tier)));
        }
        return snapshots;
    }

    public static List<BackpackInventory> getBackpackInventoriesWithAugment(PlayerEntity player, BackpackAugmentType augment) {
        List<BackpackInventory> inventories = new ArrayList<>();
        for (BackpackSnapshot snapshot : getSnapshotsWithAugment(player, augment)) {
            inventories.add(snapshot.inventory());
        }
        return inventories;
    }

    private static List<BackpackSnapshot> getSnapshotsWithAugments(PlayerEntity player, BackpackAugmentType first, BackpackAugmentType second) {
        List<BackpackSnapshot> snapshots = new ArrayList<>();
        for (ItemStack stack : BackpackAugmentHelper.getBackpackStacks(player)) {
            if (!(stack.getItem() instanceof BackpackItem)) {
                continue;
            }
            BackpackItem backpackItem = (BackpackItem) stack.getItem();
            BackpackInfo tier = backpackItem.getTier();
            if (!BackpackAugments.isUnlocked(tier, first) || !BackpackAugments.isUnlocked(tier, second)) {
                continue;
            }
            BackpackAugmentsComponent augments = Inmis.getOrCreateAugments(stack, tier);
            if (!isAugmentEnabled(first, augments) || !isAugmentEnabled(second, augments)) {
                continue;
            }
            snapshots.add(new BackpackSnapshot(stack, tier, augments, getBackpackInventory(player, stack, tier)));
        }
        return snapshots;
    }

    public static boolean hasLootboundBackpacks(PlayerEntity player) {
        return !getSnapshotsWithAugments(player, BackpackAugmentType.FUNNELLING, BackpackAugmentType.LOOTBOUND).isEmpty();
    }

    private static BackpackInventory getBackpackInventory(PlayerEntity player, ItemStack stack, BackpackInfo tier) {
        if (player.containerMenu instanceof BackpackScreenHandler) {
            BackpackScreenHandler menu = (BackpackScreenHandler) player.containerMenu;
            if (menu.getBackpackStack() != stack) {
                return new BackpackInventory(stack, tier);
            }
            BackpackInventory inventory = menu.getBackpackInventory();
            if (inventory != null) {
                return inventory;
            }
        }
        return new BackpackInventory(stack, tier);
    }

    private static boolean isAugmentEnabled(BackpackAugmentType augment, BackpackAugmentsComponent augments) {
        switch (augment) {
            case FUNNELLING: return augments.funnelling().enabled();
            case QUIVERLINK: return augments.quiverlink().enabled();
            case LOOTBOUND: return augments.lootbound().enabled();
            case LIGHTWEAVER: return augments.lightweaver().enabled();
            case SEEDFLOW: return augments.seedflow().enabled();
            case HOPPER_BRIDGE: return augments.hopperBridge().enabled();
            case FARMHAND: return augments.farmhandEnabled();
            case IMBUED_HIDE: return augments.imbuedHideEnabled();
            case IMMORTAL: return augments.immortalEnabled();
            case REFORGE: return augments.reforgeEnabled();
            default: throw new IllegalArgumentException("Unknown augment: " + augment);
        }
    }

    private static final class FunnelResult {
        private final boolean hasRemaining;
        private final int funnelCount;

        private FunnelResult(boolean hasRemaining, int funnelCount) {
            this.hasRemaining = hasRemaining;
            this.funnelCount = funnelCount;
        }

        public boolean hasRemaining() {
            return hasRemaining;
        }

        public int funnelCount() {
            return funnelCount;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FunnelResult)) {
                return false;
            }
            FunnelResult that = (FunnelResult) other;
            return hasRemaining == that.hasRemaining
                    && funnelCount == that.funnelCount;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(hasRemaining, funnelCount);
        }

        @Override
        public String toString() {
            return "FunnelResult[hasRemaining=" + hasRemaining + ", funnelCount=" + funnelCount + "]";
        }
        // No backpacks or no applicable funneling means the whole stack is still pending normal pickup.
        private static final FunnelResult IGNORE = new FunnelResult(true, 0);
    }

    private static final class BackpackSnapshot {
        private final ItemStack stack;
        private final BackpackInfo tier;
        private final BackpackAugmentsComponent augments;
        private final BackpackInventory inventory;

        private BackpackSnapshot(ItemStack stack, BackpackInfo tier, BackpackAugmentsComponent augments, BackpackInventory inventory) {
            this.stack = stack;
            this.tier = tier;
            this.augments = augments;
            this.inventory = inventory;
        }

        public ItemStack stack() {
            return stack;
        }

        public BackpackInfo tier() {
            return tier;
        }

        public BackpackAugmentsComponent augments() {
            return augments;
        }

        public BackpackInventory inventory() {
            return inventory;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof BackpackSnapshot)) {
                return false;
            }
            BackpackSnapshot that = (BackpackSnapshot) other;
            return java.util.Objects.equals(stack, that.stack)
                    && java.util.Objects.equals(tier, that.tier)
                    && java.util.Objects.equals(augments, that.augments)
                    && java.util.Objects.equals(inventory, that.inventory);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(stack, tier, augments, inventory);
        }

        @Override
        public String toString() {
            return "BackpackSnapshot[stack=" + stack + ", tier=" + tier + ", augments=" + augments + ", inventory=" + inventory + "]";
        }
    }
}
