package draylar.inmis.augment;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.ui.BackpackScreenHandler;
import draylar.inmis.mixin.AbstractArrowAccessor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumActionResult;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBow;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.properties.IProperty;
import net.minecraft.util.math.Vec3d;

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
    private static final java.util.Queue<Runnable> PENDING_REPLANTS = new java.util.concurrent.ConcurrentLinkedQueue<>();

    private BackpackAugmentHandler() {
    }

    public static boolean beforeItemPickup(EntityPlayer player, EntityItem entity, UUID target) {
        ItemStack stack = entity.getItem();
        if (stack.isEmpty() || entity.cannotPickup()) {
            return false;
        }
        if (target != null && !target.equals(player.getUniqueID())) {
            return false;
        }

        String owner = entity.getOwner();
        if (owner != null && !owner.equals(player.getName()) && entity.getAge() < 5800) return false;
        Item pickedUpItem = stack.getItem();
        FunnelResult result = funnelItemStackIntoBackpacks(player, stack);
        if (result.funnelCount() > 0) {
            player.onItemPickup(entity, result.funnelCount());
            player.addStat(net.minecraft.stats.StatList.getObjectsPickedUpStats(pickedUpItem), result.funnelCount());
        }

        if (!result.hasRemaining() && result.funnelCount() > 0) {
            entity.setDead();
            return true;
        }
        return false;
    }

    public static boolean beforeArrowPickup(EntityPlayer player, EntityArrow arrow) {
        ItemStack stack = ((AbstractArrowAccessor) arrow).inmis$callGetPickupItem().copy();
        FunnelResult result = funnelItemStackIntoBackpacks(player, stack);
        if (!result.hasRemaining()) {
            return true;
        }
        if (result.funnelCount() > 0) {
            player.inventory.addItemStackToInventory(stack);
            return true;
        }
        return false;
    }

    public static void onLootDroppedByEntity(Collection<EntityItem> drops, EntityPlayer player) {
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

    public static void onLootDroppedByBlock(Collection<EntityItem> drops, EntityPlayer player) {
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

    public static ItemStack locateAmmunition(EntityPlayer player, ItemStack weapon, ItemStack ammo) {
        LAST_QUIVER_INVENTORY.remove();
        if (!(weapon.getItem() instanceof ItemBow)) {
            return ItemStack.EMPTY;
        }
        Predicate<ItemStack> predicate = entry -> entry.getItem() instanceof net.minecraft.item.ItemArrow;
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

    public static void onPlayerPickupExperienceOrb(EntityPlayer player, EntityXPOrb orb) {
        if (orb.isDead) {
            return;
        }
        for (BackpackSnapshot snapshot : getSnapshotsWithAugment(player, BackpackAugmentType.REFORGE)) {
            BackpackInventory inventory = snapshot.inventory();
            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                if (EnchantmentHelper.getEnchantmentLevel(Enchantments.MENDING, stack) <= 0) {
                    continue;
                }
                if (!stack.isItemStackDamageable() || !stack.isItemDamaged()) {
                    continue;
                }
                if (stack.getCount() != 1 || stack.getMaxStackSize() != 1) {
                    continue;
                }
                int repairableAmount = orb.xpValue * 2;
                int maxRepairableDamage = Math.min(repairableAmount, stack.getItemDamage());
                stack.setItemDamage(stack.getItemDamage() - maxRepairableDamage);
                inventory.markDirty();
            }
        }
    }

    public static ItemStack locateTotemOfUndying(EntityPlayer player) {
        LAST_TOTEM_INVENTORY.remove();
        for (BackpackSnapshot snapshot : getSnapshotsWithAugment(player, BackpackAugmentType.IMMORTAL)) {
            BackpackInventory inventory = snapshot.inventory();
            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
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
            inventory.markDirty();
        }
        LAST_TOTEM_INVENTORY.remove();
    }

    public static void finishQuiverlinkUse() {
        BackpackInventory inventory = LAST_QUIVER_INVENTORY.get();
        if (inventory != null) {
            inventory.markDirty();
        }
        LAST_QUIVER_INVENTORY.remove();
    }

    public static void onPlayerTick(EntityPlayerMP player) {
        if (player.ticksExisted % 5 != 0) {
            return;
        }
        BlockPos pos = player.getPosition();
        int brightness = player.world.getLightFromNeighbors(pos);
        handleLightweaver(player, pos, brightness);
        handleSeedflow(player, pos);
    }

    public static void onBlockBroken(EntityPlayerMP player, IBlockState state, BlockPos pos) {
        if (!isFullyGrownCrop(state)) {
            return;
        }
        Item seedItem = getCropSeed(player.world, pos, state);
        if (seedItem == null) {
            return;
        }
        if (tryReplantCrop(player, pos, seedItem)) {
            return;
        }
        WorldServer level = (WorldServer) player.world;
        if (!isReplaceable(level, pos)) {
            BlockPos targetPos = new BlockPos(pos);
            // Forge 1.12 fires BreakEvent before removing the crop. addScheduledTask runs
            // immediately on this server thread, so defer to the actual end-of-tick event.
            PENDING_REPLANTS.add(() -> {
                if (!player.isDead && player.world == level) tryReplantCrop(player, targetPos, seedItem);
            });
        }
    }

    public static void finishServerTick() {
        Runnable replant;
        while ((replant = PENDING_REPLANTS.poll()) != null) replant.run();
    }

    private static boolean tryReplantCrop(EntityPlayerMP player, BlockPos pos, Item seedItem) {
        WorldServer level = (WorldServer) player.world;
        if (!isReplaceable(level, pos)) {
            return false;
        }
        for (BackpackSnapshot snapshot : getSnapshotsWithAugment(player, BackpackAugmentType.FARMHAND)) {
            BackpackInventory inventory = snapshot.inventory();
            ItemStack seed = inventory.findFirst(stack -> stack.getItem() == seedItem);
            if (seed.isEmpty()) {
                continue;
            }
            if (!plantSeed((WorldServer) player.world, player, seed, pos)) {
                continue;
            }
            seed.shrink(1);
            inventory.markDirty();
            return true;
        }
        return false;
    }

    private static void handleLightweaver(EntityPlayerMP player, BlockPos pos, int brightness) {
        for (BackpackSnapshot snapshot : getSnapshotsWithAugment(player, BackpackAugmentType.LIGHTWEAVER)) {
            BackpackAugmentsComponent.LightweaverSettings settings = snapshot.augments().lightweaver();
            if (brightness > settings.minimumLight()) {
                continue;
            }
            BackpackInventory inventory = snapshot.inventory();
            ItemStack torch = inventory.findFirst(stack -> stack.getItem() == Item.getItemFromBlock(net.minecraft.init.Blocks.TORCH));
            if (torch.isEmpty()) {
                continue;
            }
            EnumActionResult result = PlaceSoundControls.runWithOptions(!settings.placeSound(), true, () ->
                    UseItemOnBlockFaceContext.use((WorldServer)player.world, player, torch, pos.down(), EnumFacing.UP));
            if (result == EnumActionResult.SUCCESS) {
                inventory.markDirty();
                return;
            }
        }
    }

    private static void handleSeedflow(EntityPlayerMP player, BlockPos pos) {
        WorldServer level = (WorldServer) player.world;
        List<BlockPos> positions = new ArrayList<>();
        Vec3d forward = new Vec3d(player.getLookVec().x, 0, player.getLookVec().z);
        if (forward.lengthSquared() <= 1.0E-6) {
            forward = new Vec3d(0, 0, 1);
        } else {
            forward = forward.normalize();
        }
        Vec3d scanOrigin = new Vec3d(player.posX, player.posY, player.posZ).add(forward);
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
                inventory.markDirty();
            }
        }
    }

    private static Function<BlockPos, ItemStack> sequentialSeedSupplier(WorldServer level, BackpackAugmentsComponent.SeedflowSettings settings, BackpackInventory inventory) {
        int[] index = {0};
        return pos -> {
            while (index[0] < inventory.getSizeInventory()) {
                ItemStack stack = inventory.getStackInSlot(index[0]++);
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

    private static Function<BlockPos, ItemStack> randomSeedSupplier(WorldServer level, BackpackAugmentsComponent.SeedflowSettings settings, BackpackInventory inventory) {
        return pos -> {
            int count = 0;
            ItemStack result = ItemStack.EMPTY;
            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
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
                if (level.rand.nextInt(count) == 0) {
                    result = stack;
                }
            }
            return result;
        };
    }

    private static boolean plantSeed(WorldServer level, EntityPlayerMP player, ItemStack seed, BlockPos pos) {
        if (!(seed.getItem() instanceof net.minecraftforge.common.IPlantable)) return false;
        net.minecraftforge.common.IPlantable plantable = (net.minecraftforge.common.IPlantable)seed.getItem();
        IBlockState plant = plantable.getPlant(level, pos);
        if (!canPlaceCropSeedAt(level, seed, pos) || !player.canPlayerEdit(pos, EnumFacing.UP, seed)) return false;
        // Respect Forge's actual placement veto before consuming the stored seed.
        net.minecraftforge.common.util.BlockSnapshot snapshot = net.minecraftforge.common.util.BlockSnapshot.getBlockSnapshot(level, pos);
        level.setBlockState(pos, plant, 3);
        if (net.minecraftforge.event.ForgeEventFactory.onPlayerBlockPlace(player, snapshot, EnumFacing.UP, net.minecraft.util.EnumHand.MAIN_HAND).isCanceled()) {
            snapshot.restore(true, false); return false;
        }
        return true;
    }
    private static boolean isValidSeedflowTarget(WorldServer level, BlockPos pos) { return isReplaceable(level, pos); }
    private static boolean isReplaceable(WorldServer level, BlockPos pos) { return level.getBlockState(pos).getMaterial().isReplaceable(); }
    private static boolean canPlaceCropSeedAt(WorldServer level, ItemStack stack, BlockPos pos) {
        if (!(stack.getItem() instanceof net.minecraftforge.common.IPlantable) || !isReplaceable(level, pos)) return false;
        net.minecraftforge.common.IPlantable plantable = (net.minecraftforge.common.IPlantable)stack.getItem();
        IBlockState crop = plantable.getPlant(level, pos);
        if (!isAgeableCrop(crop.getBlock())) return false;
        return crop.getBlock().canPlaceBlockAt(level, pos) && level.getBlockState(pos.down()).getBlock().canSustainPlant(level.getBlockState(pos.down()), level, pos.down(), EnumFacing.UP, plantable);
    }
    private static boolean isSeedflowPlantable(Item item) { return item instanceof net.minecraftforge.common.IPlantable; }
    private static boolean isAgeableCrop(Block block) {
        if (!(block instanceof BlockBush) || block instanceof BlockSapling) {
            return false;
        }
        if (block instanceof BlockCrops) {
            return true;
        }
        IBlockState cropState = block.getDefaultState();
        for (IProperty<?> property : cropState.getPropertyKeys()) {
            if (property instanceof PropertyInteger && property.getName().equals("age")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFilterMatch(ItemStack stack, List<ResourceLocation> filters) {
        ResourceLocation id = stack.getItem().getRegistryName();
        return id != null && filters.contains(id);
    }

    private static boolean isFullyGrownCrop(IBlockState state) {
        if (!(state.getBlock() instanceof BlockBush)) {
            return false;
        }
        if (state.getBlock() instanceof BlockCrops) {
            BlockCrops crop = (BlockCrops) state.getBlock();
            return crop.isMaxAge(state);
        }
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (property instanceof PropertyInteger && property.getName().equals("age")) {
                PropertyInteger integerProperty = (PropertyInteger) property;
                int max = integerProperty.getAllowedValues().stream().max(Integer::compareTo).orElse(0);
                return state.getValue(integerProperty) == max;
            }
        }
        return false;
    }

    private static Item getCropSeed(World level, BlockPos pos, IBlockState state) {
        if (state.getBlock() instanceof BlockBush) {
            BlockBush bush = (BlockBush) state.getBlock();
            return bush.getItem(level, pos, state).getItem();
        }
        return null;
    }

    private static FunnelResult funnelItemStackIntoBackpacks(EntityPlayer player, ItemStack stack) {
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
        ResourceLocation id = stack.getItem().getRegistryName();
        boolean matched = id != null && settings.filters().contains(id);
        return settings.mode() == BackpackAugmentsComponent.FunnellingSettings.Mode.ALLOW ? matched : !matched;
    }

    private static void funnelDropsIntoBackpacks(Collection<EntityItem> drops, EntityPlayer player, List<BackpackSnapshot> snapshots) {
        drops.removeIf(drop -> {
            ItemStack copy = drop.getItem().copy();
            FunnelResult result = funnelItemStackIntoBackpackSnapshots(player, copy, snapshots);
            drop.setItem(copy);
            return !result.hasRemaining();
        });
    }

    private static FunnelResult funnelItemStackIntoBackpackSnapshots(EntityPlayer player, ItemStack stack, List<BackpackSnapshot> snapshots) {
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

    private static List<BackpackSnapshot> getSnapshotsWithAugment(EntityPlayer player, BackpackAugmentType augment) {
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

    public static List<BackpackInventory> getBackpackInventoriesWithAugment(EntityPlayer player, BackpackAugmentType augment) {
        List<BackpackInventory> inventories = new ArrayList<>();
        for (BackpackSnapshot snapshot : getSnapshotsWithAugment(player, augment)) {
            inventories.add(snapshot.inventory());
        }
        return inventories;
    }

    private static List<BackpackSnapshot> getSnapshotsWithAugments(EntityPlayer player, BackpackAugmentType first, BackpackAugmentType second) {
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

    public static boolean hasLootboundBackpacks(EntityPlayer player) {
        return !getSnapshotsWithAugments(player, BackpackAugmentType.FUNNELLING, BackpackAugmentType.LOOTBOUND).isEmpty();
    }

    private static BackpackInventory getBackpackInventory(EntityPlayer player, ItemStack stack, BackpackInfo tier) {
        if (player.openContainer instanceof BackpackScreenHandler) {
            BackpackScreenHandler menu = (BackpackScreenHandler) player.openContainer;
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
