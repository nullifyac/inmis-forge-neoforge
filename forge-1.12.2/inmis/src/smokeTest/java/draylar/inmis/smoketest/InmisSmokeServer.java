package draylar.inmis.smoketest;

import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.item.BackpackItem;
import net.minecraft.init.Enchantments;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Only runs in the isolated native client/server launch profile. */
@Mod.EventBusSubscriber(modid = "inmis_smoke_tests")
public final class InmisSmokeServer {
    private static EntityPlayerMP verificationPlayer;
    private static int ticks;
    private static boolean stopping;
    private static String rendererRequest;
    private static ItemStack originalChest;
    private static ItemStack originalBack;

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) throws Exception {
        if (!Boolean.getBoolean("inmis.smokeServer")) return;
        verificationPlayer = (EntityPlayerMP) event.player;
        String phase = read(Paths.get("phase.txt"));
        if (phase.equals("write")) {
            verificationPlayer.inventory.clear();
            ItemStack backpack = backpack("withered");
            BackpackInventory inventory = new BackpackInventory(backpack, ((BackpackItem) backpack.getItem()).getTier(), 54);
            inventory.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 64));
            inventory.setInventorySlotContents(50, new ItemStack(Items.DIAMOND, 7));
            ItemStack[] filterExamples = {new ItemStack(net.minecraft.init.Blocks.STONE), new ItemStack(net.minecraft.init.Blocks.DIRT),
                    new ItemStack(net.minecraft.init.Blocks.COBBLESTONE), new ItemStack(net.minecraft.init.Blocks.SAND),
                    new ItemStack(net.minecraft.init.Blocks.GRAVEL), new ItemStack(Items.COAL), new ItemStack(Items.IRON_INGOT),
                    new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.REDSTONE), new ItemStack(Items.DYE, 1, 4),
                    new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT_SEEDS)};
            for (int i = 0; i < filterExamples.length; i++) inventory.setInventorySlotContents(i + 1, filterExamples[i]);
            ItemStack armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
            armor.setStackDisplayName("Inmis persistence armor");
            armor.setItemDamage(23);
            armor.addEnchantment(Enchantments.PROTECTION, 3);
            inventory.setInventorySlotContents(53, armor);
            verificationPlayer.inventory.setInventorySlotContents(0, backpack);
            verificationPlayer.inventory.setInventorySlotContents(3, new ItemStack(Items.ARROW, 23));
            if (Inmis.BAUBLES_LOADED) {
                baubles.api.cap.IBaublesItemHandler back = baubles.api.BaublesApi.getBaublesHandler(verificationPlayer);
                check(back.insertItem(5,backpack("baby"),false).isEmpty(),"Native Baubles rendering fixture could not equip backpack");
            }
            verificationPlayer.inventoryContainer.detectAndSendChanges();
        } else {
            check(verificationPlayer.inventory.getStackInSlot(0).getItem() instanceof BackpackItem,
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
            ItemStack backpack = verificationPlayer.inventory.getStackInSlot(0);
            int savedDiamonds = Inmis.getBackpackContents(backpack).stream()
                    .filter(stack -> stack.getItem() == Items.DIAMOND).mapToInt(ItemStack::getCount).sum();
            int carriedDiamonds = verificationPlayer.inventory.mainInventory.stream()
                    .filter(stack -> stack.getItem() == Items.DIAMOND).mapToInt(ItemStack::getCount).sum();
            int armor = verificationPlayer.inventory.mainInventory.stream()
                    .filter(stack -> stack.getItem() == Items.DIAMOND_CHESTPLATE).mapToInt(ItemStack::getCount).sum()
                    + Inmis.getBackpackContents(backpack).stream().filter(stack -> stack.getItem() == Items.DIAMOND_CHESTPLATE)
                    .mapToInt(ItemStack::getCount).sum();
            if (savedDiamonds + carriedDiamonds != 71 || armor != 1)
                result = "FAIL: native inventory totals differ: diamonds=" + (savedDiamonds + carriedDiamonds) + ", armor=" + armor;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        server.getPlayerList().saveAllPlayerData();
        server.saveAllWorlds(false);
        Files.write(Paths.get("server-result-" + phase + ".txt"), (result + "\n").getBytes(StandardCharsets.UTF_8));
        Inmis.LOGGER.info("Server smoke verification: {}", result);
        server.initiateShutdown();
    }

    private static String read(Path path) throws java.io.IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
    }
    private static void applyRendererRequest(String phase) throws java.io.IOException {
        Path requestFile = Paths.get("renderer-request.txt");
        if (verificationPlayer == null || !Files.exists(requestFile)) return;
        String request = read(requestFile);
        boolean chest = request.equals(phase + ":chest");
        boolean curios = request.equals(phase + ":baubles");
        if ((!chest && !curios) || request.equals(rendererRequest)) return;
        if (originalChest == null) {
            originalChest = verificationPlayer.getItemStackFromSlot(net.minecraft.inventory.EntityEquipmentSlot.CHEST).copy();
            if (Inmis.BAUBLES_LOADED) originalBack = NativeBaublesAccess.handler().getStackInSlot(5).copy();
        }
        verificationPlayer.setItemStackToSlot(net.minecraft.inventory.EntityEquipmentSlot.CHEST,
                chest ? dyedBackpack(0x36C7E8) : ItemStack.EMPTY);
        if (Inmis.BAUBLES_LOADED) {
            NativeBaublesAccess.handler().setStackInSlot(5, curios ? dyedBackpack(0xBF51E8) : ItemStack.EMPTY);
        } else {
            check(!curios, "Baubles renderer requested without Baubles loaded");
        }
        verificationPlayer.inventoryContainer.detectAndSendChanges();
        rendererRequest = request;
        Inmis.LOGGER.info("Applied server renderer verification stage {}", request);
    }
    private static final class NativeBaublesAccess {
        private static baubles.api.cap.IBaublesItemHandler handler() {
            return baubles.api.BaublesApi.getBaublesHandler(verificationPlayer);
        }
    }
    private static void restoreRendererEquipment() {
        if (verificationPlayer == null || originalChest == null) return;
        verificationPlayer.setItemStackToSlot(net.minecraft.inventory.EntityEquipmentSlot.CHEST, originalChest.copy());
        if (Inmis.BAUBLES_LOADED) NativeBaublesAccess.handler().setStackInSlot(5, originalBack.copy());
        verificationPlayer.inventoryContainer.detectAndSendChanges();
    }
    private static ItemStack dyedBackpack(int rgb) {
        ItemStack stack = backpack("frayed");
        ((draylar.inmis.item.DyeableBackpackItem) stack.getItem()).setColor(stack, rgb);
        return stack;
    }
    private static ItemStack backpack(String name) {
        return new ItemStack(Inmis.BACKPACKS.stream().map(entry -> entry.get())
                .filter(item -> item.getTier().getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Fixture tier missing")));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
