package draylar.inmis.smoketest;

import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.item.BackpackItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.FMLCommonHandler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Only runs in the isolated native client/server launch profile. */
public final class InmisSmokeServer {
    private static EntityPlayerMP verificationPlayer;
    private static int ticks;
    private static boolean stopping;
    private static String rendererRequest;
    private static ItemStack originalChest;
    private static ItemStack originalBack;

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) throws Exception {
        if (!Boolean.getBoolean("inmis.smokeServer")) return;
        verificationPlayer = (EntityPlayerMP) event.player;
        String phase = read(Paths.get("phase.txt"));
        if (phase.equals("write")) {
            for(int i=0;i<verificationPlayer.inventory.getSizeInventory();i++)verificationPlayer.inventory.setInventorySlotContents(i,null);
            ItemStack backpack = backpack("withered");
            BackpackInventory inventory = new BackpackInventory(backpack, ((BackpackItem) backpack.getItem()).getTier(), 54);
            inventory.setInventorySlotContents(0, new ItemStack(Items.diamond, 64));
            inventory.setInventorySlotContents(50, new ItemStack(Items.diamond, 7));
            ItemStack[] filterItems = {new ItemStack(net.minecraft.init.Blocks.stone),new ItemStack(net.minecraft.init.Blocks.dirt),
                    new ItemStack(net.minecraft.init.Blocks.cobblestone),new ItemStack(net.minecraft.init.Blocks.sand),new ItemStack(net.minecraft.init.Blocks.gravel),
                    new ItemStack(Items.coal),new ItemStack(Items.iron_ingot),new ItemStack(Items.gold_ingot),new ItemStack(Items.redstone),
                    new ItemStack(Items.wheat),new ItemStack(Items.wheat_seeds),new ItemStack(Items.arrow)};
            for(int i=0;i<filterItems.length;i++)inventory.setInventorySlotContents(i+1,filterItems[i]);
            ItemStack armor = new ItemStack(Items.diamond_chestplate);
            armor.setStackDisplayName("Inmis persistence armor");
            armor.setItemDamage(23);
            armor.addEnchantment(Enchantment.protection, 3);
            inventory.setInventorySlotContents(53, armor);
            verificationPlayer.inventory.setInventorySlotContents(0, backpack);
            verificationPlayer.inventory.setInventorySlotContents(3, new ItemStack(Items.arrow, 23));
            if (draylar.inmis.compat.BaublesCompat.isLoaded()) {
                net.minecraft.inventory.IInventory back = baubles.api.BaublesApi.getBaubles(verificationPlayer);
                back.setInventorySlotContents(0,backpack("baby"));
            }
            verificationPlayer.inventoryContainer.detectAndSendChanges();
        } else {
            check(verificationPlayer.inventory.getStackInSlot(0).getItem() instanceof BackpackItem,
                    "Player backpack missing after actual server restart");
        }
        Inmis.LOGGER.info("Isolated smoke player logged in for phase {}", phase);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) throws Exception {
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
                    .filter(stack -> stack != null && stack.getItem() == Items.diamond).mapToInt(s -> s.stackSize).sum();
            int carriedDiamonds = java.util.Arrays.stream(verificationPlayer.inventory.mainInventory)
                    .filter(stack -> stack != null && stack.getItem() == Items.diamond).mapToInt(s -> s.stackSize).sum();
            int armor = java.util.Arrays.stream(verificationPlayer.inventory.mainInventory)
                    .filter(stack -> stack != null && stack.getItem() == Items.diamond_chestplate).mapToInt(s -> s.stackSize).sum()
                    + Inmis.getBackpackContents(backpack).stream().filter(stack -> stack != null && stack.getItem() == Items.diamond_chestplate)
                    .mapToInt(s -> s.stackSize).sum();
            if (savedDiamonds + carriedDiamonds != 71 || armor != 1)
                result = "FAIL: native inventory totals differ: diamonds=" + (savedDiamonds + carriedDiamonds) + ", armor=" + armor;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        server.getConfigurationManager().saveAllPlayerData();
        // initiateShutdown performs the normal dedicated-server world save.
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
        if (rendererRequest == null) {
            originalChest = copy(verificationPlayer.inventory.armorInventory[2]);
            if (draylar.inmis.compat.BaublesCompat.isLoaded()) originalBack = copy(nativeBack().getStackInSlot(0));
        }
        verificationPlayer.inventory.armorInventory[2] = chest ? dyedBackpack(0x36C7E8) : null;
        if (draylar.inmis.compat.BaublesCompat.isLoaded()) {
            nativeBack().setInventorySlotContents(0, curios ? dyedBackpack(0xBF51E8) : null);
        } else {
            check(!curios, "Curios renderer requested without Curios loaded");
        }
        verificationPlayer.inventoryContainer.detectAndSendChanges();
        rendererRequest = request;
        Inmis.LOGGER.info("Applied server renderer verification stage {}", request);
    }
    private static net.minecraft.inventory.IInventory nativeBack() {
        return baubles.api.BaublesApi.getBaubles(verificationPlayer);
    }
    private static void restoreRendererEquipment() {
        if (verificationPlayer == null || rendererRequest == null) return;
        verificationPlayer.inventory.armorInventory[2]=copy(originalChest);
        if (draylar.inmis.compat.BaublesCompat.isLoaded()) nativeBack().setInventorySlotContents(0,copy(originalBack));
        verificationPlayer.inventoryContainer.detectAndSendChanges();
    }
    private static ItemStack copy(ItemStack stack) { return stack == null ? null : stack.copy(); }
    private static ItemStack dyedBackpack(int rgb) {
        ItemStack stack = backpack("frayed");
        ((draylar.inmis.item.DyeableBackpackItem) stack.getItem()).setColor(stack, rgb);
        return stack;
    }
    private static ItemStack backpack(String name) {
        return new ItemStack(Inmis.BACKPACK_ITEMS.stream()
                .filter(item -> item.getTier().getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Fixture tier missing")));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
