package draylar.inmis.smoketest;

import com.mojang.authlib.GameProfile;
import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.ui.BackpackScreenHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class AdditionalChecks {
    public static void openingPayloadUsesServerDimensions(SmokeContext context) {
        ItemStack stack = backpack("withered");
        FakePlayer player = new FakePlayer(context.getLevel(), new GameProfile(UUID.randomUUID(), "InmisPayload"));
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            buffer.writeItem(stack);
            buffer.writeVarInt(9);
            buffer.writeVarInt(6);
            buffer.writeVarInt(54);
            buffer.writeInt(0);
            BackpackScreenHandler menu = new BackpackScreenHandler(1, player.inventory, buffer);
            check(menu.slots.size() == 90 && menu.getDimension().getWidth() == 178
                    && menu.getDimension().getHeight() == 224, "Client-local tier dimensions overrode opening payload");
            check(((BackpackItem) stack.getItem()).getTier().getRowWidth() == 11,
                    "Fixture must keep local Withered width different from server width");
            check(!menu.slots.get(81).mayPickup(player), "Payload lost the protected active hotbar slot");
        } finally { buffer.release(); }
        context.succeed();
    }

    public static void augmentPacketAndNbtPreserveImmutableSettings(SmokeContext context) {
        List<ResourceLocation> filters = new ArrayList<>(Arrays.asList(new ResourceLocation("minecraft", "diamond")));
        BackpackAugmentsComponent settings = BackpackAugmentsComponent.DEFAULT.withFunnelling(
                new BackpackAugmentsComponent.FunnellingSettings(true,
                        BackpackAugmentsComponent.FunnellingSettings.Mode.DISALLOW, filters));
        filters.clear();
        check(settings.funnelling().filters().size() == 1, "Settings retained mutable caller filter list");
        check(settings.equals(BackpackAugmentsComponent.fromTag(settings.toTag())), "NBT settings round trip changed values");
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            settings.write(buffer);
            check(settings.equals(BackpackAugmentsComponent.read(buffer)), "Protocol settings round trip changed values");
        } finally { buffer.release(); }
        context.succeed();
    }

    public static void prohibitedItemsCannotEnterThroughMenuShiftClicks(SmokeContext context) {
        boolean previousShulkers = Inmis.CONFIG.disableShulkers;
        try {
            Inmis.CONFIG.disableShulkers = true;
            FakePlayer player = new FakePlayer(context.getLevel(), new GameProfile(UUID.randomUUID(), "InmisMenuInsert"));
            ItemStack backpack = backpack("frayed");
            player.inventory.setItem(0, backpack);
            player.inventory.setItem(1, backpack("frayed"));
            player.inventory.setItem(2, new ItemStack(Items.SHULKER_BOX));
            BackpackScreenHandler menu = new BackpackScreenHandler(1, player.inventory, backpack);
            int firstHotbar = menu.slots.size() - 9;
            check(menu.quickMoveStack(player, firstHotbar + 1).isEmpty(), "Shift click nested another backpack");
            check(menu.quickMoveStack(player, firstHotbar + 2).isEmpty(), "Shift click admitted prohibited shulker box");
            check(player.inventory.getItem(1).getItem() instanceof BackpackItem
                    && player.inventory.getItem(2).getItem() == Items.SHULKER_BOX, "Rejected menu insertion lost items");
        } finally {
            Inmis.CONFIG.disableShulkers = previousShulkers;
        }
        context.succeed();
    }

    public static void legacyBackpackedImportPreservesOverflowAndAmbiguousData(SmokeContext context) {
        boolean previous = Inmis.CONFIG.importBackpackedItems;
        draylar.inmis.compat.BackpackedImportController.setOverride(null);
        try {
            Inmis.CONFIG.importBackpackedItems = true;
            ItemStack stack = backpack("frayed");
            ListNBT legacy = new ListNBT();
            CompoundNBT entry = new ItemStack(Items.DIAMOND, 7).save(new CompoundNBT());
            entry.putByte("Slot", (byte) 50);
            legacy.add(entry);
            stack.getOrCreateTag().put("Items", legacy);
            BackpackInfo tier = ((BackpackItem) stack.getItem()).getTier();
            check(new BackpackInventory(stack, tier).getItem(50).getCount() == 7, "Backpacked overflow was truncated");
            check(!stack.getOrCreateTag().contains("Items"), "Successful import did not consume legacy data");
            ItemStack ambiguous = backpack("frayed");
            legacy.add(entry.copy());
            ambiguous.getOrCreateTag().put("Items", legacy);
            Inmis.getOrCreateInventory(ambiguous, tier);
            check(ambiguous.getOrCreateTag().getList("Items", 10).size() == 2,
                    "Ambiguous duplicate-slot Backpacked data was deleted");
        } finally {
            Inmis.CONFIG.importBackpackedItems = previous;
            draylar.inmis.compat.BackpackedImportController.setOverride(null);
        }
        context.succeed();
    }

    private static ItemStack backpack(String name) {
        return new ItemStack(Inmis.BACKPACKS.stream().map(entry -> entry.get())
                .filter(item -> item.getTier().getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Fixture tier is missing")));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
