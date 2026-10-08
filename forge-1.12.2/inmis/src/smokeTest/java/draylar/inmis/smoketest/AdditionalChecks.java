package draylar.inmis.smoketest;

import com.mojang.authlib.GameProfile;
import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.config.BackpackInfo;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.ui.BackpackScreenHandler;
import draylar.inmis.network.ServerNetworking;
import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class AdditionalChecks {
    public static void openingPayloadUsesServerDimensions(SmokeContext context) throws java.io.IOException {
        ItemStack stack = backpack("withered");
        FakePlayer player = new FakePlayer(context.getLevel(), new GameProfile(UUID.randomUUID(), "InmisPayload"));
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            buffer.writeItemStack(stack);
            buffer.writeVarInt(9);
            buffer.writeVarInt(6);
            buffer.writeVarInt(54);
            buffer.writeInt(0);
            BackpackScreenHandler menu = new BackpackScreenHandler(1, player.inventory, buffer);
            check(menu.inventorySlots.size() == 90 && menu.getDimension().getWidth() == 178
                    && menu.getDimension().getHeight() == 224, "Client-local tier dimensions overrode opening payload");
            check(((BackpackItem) stack.getItem()).getTier().getRowWidth() == 11,
                    "Fixture must keep local Withered width different from server width");
            check(!menu.inventorySlots.get(81).canTakeStack(player), "Payload lost the protected active hotbar slot");
            new BackpackInventory(stack, ((BackpackItem)stack.getItem()).getTier()).setInventorySlotContents(0,new ItemStack(Items.DIAMOND,7));
            stack.setStackDisplayName("Opening metadata");
            Inmis.setBackpackAugments(stack,BackpackAugmentsComponent.DEFAULT.withFarmhandEnabled(true));
            NBTTagCompound saved=Inmis.tag(stack).copy();buffer.clear();
            BackpackScreenHandler.writeOpeningData(buffer,player.inventory,stack);
            ItemStack description=buffer.readItemStack();
            check(!Inmis.tag(description).hasKey("Inventory")&&description.getDisplayName().equals("Opening metadata")
                    &&Inmis.tag(description).getCompoundTag("Augments").equals(saved.getCompoundTag("Augments"))
                    &&Inmis.tag(stack).equals(saved),"Actual opening descriptor leaked inventory or changed saved contents/metadata");
            buffer.clear();
            BackpackScreenHandler.writeOpeningData(buffer,player.inventory,stack);
            byte[] descriptor = new byte[buffer.readableBytes()];
            buffer.readBytes(descriptor);
            ServerNetworking.OpeningPacket wire = new ServerNetworking.OpeningPacket(descriptor);
            buffer.clear(); wire.toBytes(buffer);
            ServerNetworking.OpeningPacket decoded = new ServerNetworking.OpeningPacket(); decoded.fromBytes(buffer);
            ServerNetworking.OpeningHandler receiver = new ServerNetworking.OpeningHandler();
            receiver.onMessage(decoded,null);
            ServerNetworking.clearClientOpening();
            boolean missing = false;
            try { ServerNetworking.consumeClientOpening().release(); }
            catch (IllegalStateException expected) { missing = true; }
            check(missing,"Connection reset retained a stale opening descriptor");
            receiver.onMessage(decoded,null);
            PacketBuffer fresh = ServerNetworking.consumeClientOpening();
            try {
                check(fresh.readItemStack().getDisplayName().equals("Opening metadata"),
                        "Fresh connection descriptor was not consumed after reset");
            } finally { fresh.release(); ServerNetworking.clearClientOpening(); }
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
            player.inventory.setInventorySlotContents(0, backpack);
            player.inventory.setInventorySlotContents(1, backpack("frayed"));
            player.inventory.setInventorySlotContents(2, new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.PURPLE_SHULKER_BOX)));
            BackpackScreenHandler menu = new BackpackScreenHandler(1, player.inventory, backpack);
            int firstHotbar = menu.inventorySlots.size() - 9;
            check(menu.transferStackInSlot(player, firstHotbar + 1).isEmpty(), "Shift click nested another backpack");
            check(menu.transferStackInSlot(player, firstHotbar + 2).isEmpty(), "Shift click admitted prohibited shulker box");
            check(player.inventory.getStackInSlot(1).getItem() instanceof BackpackItem
                    && player.inventory.getStackInSlot(2).getItem() == net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.PURPLE_SHULKER_BOX), "Rejected menu insertion lost items");
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
            NBTTagList legacy = new NBTTagList();
            NBTTagCompound entry = new ItemStack(Items.DIAMOND, 7).writeToNBT(new NBTTagCompound());
            entry.setByte("Slot", (byte) 50);
            legacy.appendTag(entry);
            Inmis.tag(stack).setTag("Items", legacy);
            BackpackInfo tier = ((BackpackItem) stack.getItem()).getTier();
            check(new BackpackInventory(stack, tier).getStackInSlot(50).getCount() == 7, "Backpacked overflow was truncated");
            check(!Inmis.tag(stack).hasKey("Items"), "Successful import did not consume legacy data");
            ItemStack ambiguous = backpack("frayed");
            legacy.appendTag(entry.copy());
            Inmis.tag(ambiguous).setTag("Items", legacy);
            Inmis.getOrCreateInventory(ambiguous, tier);
            check(Inmis.tag(ambiguous).getTagList("Items", 10).tagCount() == 2,
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
