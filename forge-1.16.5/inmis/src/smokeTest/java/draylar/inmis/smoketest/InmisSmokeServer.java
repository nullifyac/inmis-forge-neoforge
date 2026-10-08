package draylar.inmis.smoketest;

import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.item.BackpackItem;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Only runs in the isolated native client/server launch profile. */
@Mod.EventBusSubscriber(modid = "inmis_smoke_tests")
public final class InmisSmokeServer {
    private static ServerPlayerEntity verificationPlayer;
    private static int ticks;
    private static boolean stopping;
    private static String rendererRequest;
    private static ItemStack originalChest;
    private static ItemStack originalBack;

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) throws Exception {
        if (!Boolean.getBoolean("inmis.smokeServer")) return;
        verificationPlayer = (ServerPlayerEntity) event.getPlayer();
        String phase = read(Paths.get("phase.txt"));
        if (phase.equals("write")) {
            verificationPlayer.inventory.clearContent();
            ItemStack backpack = backpack("withered");
            BackpackInventory inventory = new BackpackInventory(backpack, ((BackpackItem) backpack.getItem()).getTier(), 54);
            inventory.setItem(0, new ItemStack(Items.DIAMOND, 64));
            inventory.setItem(50, new ItemStack(Items.DIAMOND, 7));
            ItemStack armor = new ItemStack(Items.NETHERITE_CHESTPLATE);
            armor.setHoverName(new StringTextComponent("Inmis persistence armor"));
            armor.setDamageValue(23);
            armor.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 3);
            inventory.setItem(53, armor);
            verificationPlayer.inventory.setItem(0, backpack);
            verificationPlayer.inventory.setItem(3, new ItemStack(Items.ARROW, 23));
            if (Inmis.CURIOS_LOADED) {
                top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler back =
                        top.theillusivec4.curios.api.CuriosApi.getCuriosHelper().getCuriosHandler(verificationPlayer)
                                .resolve().orElseThrow(() -> new AssertionError("Native Curios handler missing"))
                                .getStacksHandler("back").orElseThrow(() -> new AssertionError("Native back slot missing")).getStacks();
                check(back.insertItem(0, backpack("baby"), false).isEmpty(), "Native Curios rendering fixture could not equip backpack");
            }
            verificationPlayer.inventoryMenu.broadcastChanges();
        } else {
            check(verificationPlayer.inventory.getItem(0).getItem() instanceof BackpackItem,
                    "Player backpack missing after actual server restart");
        }
        Inmis.LOGGER.info("Isolated smoke player logged in for phase {}", phase);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) throws Exception {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("inmis.smokeServer") || stopping) return;
        ticks++;
        String phase = read(Paths.get("phase.txt"));
        applyRendererRequest(phase);
        Path clientResult = Paths.get("../client/result-" + phase + ".txt");
        if ((!Files.exists(clientResult) || Files.size(clientResult) == 0) && ticks < 12000) return;
        stopping = true;
        String result = Files.exists(clientResult) ? read(clientResult) : "FAIL: client timed out";
        restoreRendererEquipment();
        if (result.startsWith("PASS") && verificationPlayer == null) result = "FAIL: no native player logged in";
        if (result.startsWith("PASS") && verificationPlayer != null) {
            ItemStack backpack = verificationPlayer.inventory.getItem(0);
            int savedDiamonds = Inmis.getBackpackContents(backpack).stream()
                    .filter(stack -> stack.getItem() == Items.DIAMOND).mapToInt(ItemStack::getCount).sum();
            int carriedDiamonds = verificationPlayer.inventory.items.stream()
                    .filter(stack -> stack.getItem() == Items.DIAMOND).mapToInt(ItemStack::getCount).sum();
            int armor = verificationPlayer.inventory.items.stream()
                    .filter(stack -> stack.getItem() == Items.NETHERITE_CHESTPLATE).mapToInt(ItemStack::getCount).sum()
                    + Inmis.getBackpackContents(backpack).stream().filter(stack -> stack.getItem() == Items.NETHERITE_CHESTPLATE)
                    .mapToInt(ItemStack::getCount).sum();
            if (savedDiamonds + carriedDiamonds != 71 || armor != 1)
                result = "FAIL: native inventory totals differ: diamonds=" + (savedDiamonds + carriedDiamonds) + ", armor=" + armor;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        server.getPlayerList().saveAll();
        server.saveAllChunks(false, true, true);
        Files.write(Paths.get("server-result-" + phase + ".txt"), (result + "\n").getBytes(StandardCharsets.UTF_8));
        Inmis.LOGGER.info("Server smoke verification: {}", result);
        server.halt(false);
    }

    private static String read(Path path) throws java.io.IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
    }
    private static void applyRendererRequest(String phase) throws java.io.IOException {
        Path requestFile = Paths.get("renderer-request.txt");
        if (verificationPlayer == null || !Files.exists(requestFile)) return;
        String request = read(requestFile);
        boolean chest = request.equals(phase + ":chest");
        boolean curios = request.equals(phase + ":curios");
        if ((!chest && !curios) || request.equals(rendererRequest)) return;
        if (originalChest == null) {
            originalChest = verificationPlayer.getItemBySlot(net.minecraft.inventory.EquipmentSlotType.CHEST).copy();
            if (Inmis.CURIOS_LOADED) originalBack = nativeBack().getStackInSlot(0).copy();
        }
        verificationPlayer.setItemSlot(net.minecraft.inventory.EquipmentSlotType.CHEST,
                chest ? dyedBackpack(0x36C7E8) : ItemStack.EMPTY);
        if (Inmis.CURIOS_LOADED) {
            nativeBack().setStackInSlot(0, curios ? dyedBackpack(0xBF51E8) : ItemStack.EMPTY);
        } else {
            check(!curios, "Curios renderer requested without Curios loaded");
        }
        verificationPlayer.inventoryMenu.broadcastChanges();
        rendererRequest = request;
        Inmis.LOGGER.info("Applied server renderer verification stage {}", request);
    }
    private static top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler nativeBack() {
        return top.theillusivec4.curios.api.CuriosApi.getCuriosHelper().getCuriosHandler(verificationPlayer)
                .resolve().orElseThrow(() -> new AssertionError("Native Curios handler missing"))
                .getStacksHandler("back").orElseThrow(() -> new AssertionError("Native back slot missing")).getStacks();
    }
    private static void restoreRendererEquipment() {
        if (verificationPlayer == null || originalChest == null) return;
        verificationPlayer.setItemSlot(net.minecraft.inventory.EquipmentSlotType.CHEST, originalChest.copy());
        if (Inmis.CURIOS_LOADED) nativeBack().setStackInSlot(0, originalBack.copy());
        verificationPlayer.inventoryMenu.broadcastChanges();
    }
    private static ItemStack dyedBackpack(int rgb) {
        ItemStack stack = backpack("frayed");
        ((net.minecraft.item.IDyeableArmorItem) stack.getItem()).setColor(stack, rgb);
        return stack;
    }
    private static ItemStack backpack(String name) {
        return new ItemStack(Inmis.BACKPACKS.stream().map(entry -> entry.get())
                .filter(item -> item.getTier().getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Fixture tier missing")));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
