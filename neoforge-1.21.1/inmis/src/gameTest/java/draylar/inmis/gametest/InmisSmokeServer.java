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
        Path clientResult = Path.of("../client/result-" + phase + ".txt");
        if (!Files.exists(clientResult) && ticks < 12000) return;
        stopping = true;
        String result = Files.exists(clientResult) ? Files.readString(clientResult).trim() : "FAIL: client timed out";
        if (result.startsWith("PASS") && verificationPlayer == null) result = "FAIL: no player logged into the verification server";
        if (result.startsWith("PASS") && verificationPlayer != null) {
            ItemStack backpack = verificationPlayer.getInventory().getItem(0);
            int savedDiamonds = Inmis.getBackpackContents(backpack).stream().filter(stack -> stack.is(Items.DIAMOND))
                    .mapToInt(ItemStack::getCount).sum();
            int carriedDiamonds = verificationPlayer.getInventory().items.stream().filter(stack -> stack.is(Items.DIAMOND))
                    .mapToInt(ItemStack::getCount).sum();
            long armorCount = java.util.stream.Stream.of(verificationPlayer.getInventory().items.stream(),
                            verificationPlayer.getInventory().armor.stream(), verificationPlayer.getInventory().offhand.stream(),
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
}
