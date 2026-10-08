package draylar.inmis.gametest;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.component.BackpackComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Seeds only this opt-in isolated server, and validates real player-data saves before shutdown. */
@EventBusSubscriber(modid = "inmis_game_tests")
public final class InmisSmokeServer {
    private static ServerPlayer verificationPlayer;
    private static int ticks;
    private static boolean stopping;
    private static String rendererRequest;
    private static ItemStack originalChest;
    private static ItemStack originalBack;

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) throws Exception {
        if (!Boolean.getBoolean("inmis.smokeServer")) return;
        verificationPlayer = (ServerPlayer) event.getEntity();
        String phase = Files.readString(Path.of("phase.txt")).trim();
        if (phase.equals("write")) {
            verificationPlayer.getInventory().clearContent();
            ItemStack backpack = Inmis.BACKPACKS.stream().map(java.util.function.Supplier::get)
                    .filter(item -> item.getTier().getName().equals("withered")).findFirst().orElseThrow().getDefaultInstance();
            List<ItemStack> contents = new ArrayList<>(Collections.nCopies(54, ItemStack.EMPTY));
            contents.set(0, new ItemStack(Items.DIAMOND, 64));
            contents.set(50, new ItemStack(Items.DIAMOND, 7));
            ItemStack armor = new ItemStack(Items.NETHERITE_CHESTPLATE);
            armor.set(DataComponents.CUSTOM_NAME, Component.literal("Inmis persistence armor"));
            armor.setDamageValue(23);
            armor.enchant(verificationPlayer.level().holderLookup(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.PROTECTION), 3);
            contents.set(53, armor);
            backpack.set(Inmis.BACKPACK_COMPONENT.get(), new BackpackComponent(contents));
            verificationPlayer.getInventory().setItem(0, backpack);
            verificationPlayer.getInventory().setItem(3, new ItemStack(Items.ARROW, 23));
            verificationPlayer.inventoryMenu.broadcastChanges();
        } else {
            if (!(verificationPlayer.getInventory().getItem(0).getItem() instanceof BackpackItem)) {
                throw new AssertionError("Player backpack missing after actual server restart");
            }
        }
        Inmis.LOGGER.info("Isolated smoke player logged in for phase {}", phase);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("inmis.smokeServer") || stopping) return;
        ticks++;
        String phase = Files.readString(Path.of("phase.txt")).trim();
        applyRendererRequest(phase);
        Path clientResult = Path.of("../client/result-" + phase + ".txt");
        if ((!Files.exists(clientResult) || Files.size(clientResult) == 0) && ticks < 12000) return;
        stopping = true;
        String result = Files.exists(clientResult) ? Files.readString(clientResult).trim() : "FAIL: client timed out";
        restoreRendererEquipment();
        if (result.startsWith("PASS") && verificationPlayer == null) result = "FAIL: no player logged into the verification server";
        if (result.startsWith("PASS") && verificationPlayer != null) {
            ItemStack backpack = verificationPlayer.getInventory().getItem(0);
            int savedDiamonds = Inmis.getBackpackContents(backpack).stream().filter(stack -> stack.is(Items.DIAMOND))
                    .mapToInt(ItemStack::getCount).sum();
            int carriedDiamonds = verificationPlayer.getInventory().getNonEquipmentItems().stream().filter(stack -> stack.is(Items.DIAMOND))
                    .mapToInt(ItemStack::getCount).sum();
            long armorCount = java.util.stream.Stream.of(verificationPlayer.getInventory().getNonEquipmentItems().stream(),
                            draylar.inmis.util.PlayerInventorySections.armor(verificationPlayer.getInventory()).stream(), draylar.inmis.util.PlayerInventorySections.offhand(verificationPlayer.getInventory()).stream(),
                            Inmis.getBackpackContents(backpack).stream()).flatMap(java.util.function.Function.identity())
                    .filter(stack -> stack.is(Items.NETHERITE_CHESTPLATE)).mapToInt(ItemStack::getCount).sum();
            if (savedDiamonds + carriedDiamonds != 71 || armorCount != 1) {
                result = "FAIL: actual server inventory totals differ: diamonds=" + (savedDiamonds + carriedDiamonds) + ", armor=" + armorCount;
            }
        }
        event.getServer().getPlayerList().saveAll();
        event.getServer().saveEverything(false, true, true);
        Files.writeString(Path.of("server-result-" + phase + ".txt"), result + "\n");
        Inmis.LOGGER.info("Server smoke verification: {}", result);
        event.getServer().halt(false);
    }

    private static void applyRendererRequest(String phase) throws java.io.IOException {
        Path requestFile = Path.of("renderer-request.txt");
        if (verificationPlayer == null || !Files.exists(requestFile)) return;
        String request = Files.readString(requestFile).trim();
        boolean chest = request.equals(phase + ":chest");
        boolean curios = request.equals(phase + ":curios");
        if ((!chest && !curios) || request.equals(rendererRequest)) return;
        if (originalChest == null) {
            originalChest = verificationPlayer.getItemBySlot(EquipmentSlot.CHEST).copy();
            if (Inmis.CURIOS_LOADED) originalBack = CuriosRenderEquipment.get().copy();
        }
        verificationPlayer.setItemSlot(EquipmentSlot.CHEST, chest ? dyedBackpack(0x36C7E8) : ItemStack.EMPTY);
        if (Inmis.CURIOS_LOADED) CuriosRenderEquipment.set(curios ? dyedBackpack(0xBF51E8) : ItemStack.EMPTY);
        else if (curios) throw new AssertionError("Curios renderer requested without Curios loaded");
        verificationPlayer.inventoryMenu.broadcastChanges();
        rendererRequest = request;
        Inmis.LOGGER.info("Applied server renderer verification stage {}", request);
    }

    private static void restoreRendererEquipment() {
        if (verificationPlayer == null || originalChest == null) return;
        verificationPlayer.setItemSlot(EquipmentSlot.CHEST, originalChest.copy());
        if (Inmis.CURIOS_LOADED) CuriosRenderEquipment.set(originalBack.copy());
        verificationPlayer.inventoryMenu.broadcastChanges();
    }

    private static ItemStack dyedBackpack(int rgb) {
        ItemStack stack = Inmis.BACKPACKS.stream().map(java.util.function.Supplier::get)
                .filter(item -> item.getTier().getName().equals("frayed")).findFirst().orElseThrow().getDefaultInstance();
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
        return stack;
    }

    private static final class CuriosRenderEquipment {
        private static top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler back() {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(verificationPlayer)
                    .orElseThrow(() -> new AssertionError("Native Curios handler missing"))
                    .getStacksHandler("back").orElseThrow(() -> new AssertionError("Native back slot missing")).getStacks();
        }
        private static ItemStack get() { return back().getStackInSlot(0); }
        private static void set(ItemStack stack) { back().setStackInSlot(0, stack); }
    }
}
