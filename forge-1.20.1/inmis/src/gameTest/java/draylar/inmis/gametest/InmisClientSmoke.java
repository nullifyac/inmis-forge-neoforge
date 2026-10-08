package draylar.inmis.gametest;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.network.ServerNetworking;
import draylar.inmis.ui.BackpackHandledScreen;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;

import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;

import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in client/server verification. Never packaged into the player mod. */
@Mod.EventBusSubscriber(modid = "inmis_game_tests", value = Dist.CLIENT)
public final class InmisClientSmoke {
    private static int state;
    private static int ticks;
    private static int entered;
    private static String phase;
    private static boolean finished;
    private static int finishedAt;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("inmis.smokeClient")) return;
        Minecraft minecraft = Minecraft.getInstance();
        ticks++;
        if (finished) {
            // Keep native capabilities valid until the server restores equipment and saves.
            Path acknowledgement = java.nio.file.Paths.get("../server/server-result-" + phase + ".txt");
            try {
                if (Files.exists(acknowledgement) && Files.size(acknowledgement) > 0) {
                    minecraft.stop();
                    return;
                }
            } catch (java.io.IOException ignored) {
                // The result file may be between creation and its short write.
            }
            if (ticks - finishedAt > 400) {
                Inmis.LOGGER.error("Server did not acknowledge client verification before shutdown");
                minecraft.stop();
            }
            return;
        }
        try {
            if (phase == null) phase = new String(Files.readAllBytes(java.nio.file.Paths.get("../server/phase.txt")), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (state >= 7 && minecraft.player != null) holdRenderCamera(minecraft);
            if (ticks > 6000) throw new AssertionError("Client verification timed out at state " + state
                    + ", screen=" + minecraft.screen);
            if (state == 0 && minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
                String address = "127.0.0.1:" + Integer.getInteger("inmis.smokePort", 25576);
                ConnectScreen.startConnecting(minecraft.screen, minecraft,
                        net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),
                        new ServerData("Isolated Inmis verification", address, false), false);
                advance();
            } else if (state == 1 && minecraft.player != null && minecraft.level != null && ticks - entered > 60) {
                ItemStack backpack = minecraft.player.getInventory().getItem(0);
                check(backpack.getItem() instanceof BackpackItem, "Server-provided backpack missing from hotbar");
                BackpackItem item = (BackpackItem) backpack.getItem();
                check(item.getTier().getRowWidth() == 11 && item.getTier().getNumberOfRows() == 6,
                        "Client must retain default Withered dimensions 11x6");
                if (Inmis.CURIOS_LOADED) {
                    check(!draylar.inmis.compat.CuriosCompat.findFirstEquippedBackpack(minecraft.player).isEmpty(),
                            "Native Curios equipped backpack was not synchronized to the client");
                    minecraft.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                }
                if (!phase.equals("write")) check(inventoryCount(minecraft, Items.NETHERITE_CHESTPLATE) == 1,
                        "Named armor did not survive server/client restart");
                if (!phase.equals("write")) checkArmor(minecraft.player.getInventory().items.stream()
                        .filter(stack -> (stack.getItem() == Items.NETHERITE_CHESTPLATE)).findFirst().orElseThrow(() -> new AssertionError("Fixture armor missing")));
                minecraft.gameMode.useItem(minecraft.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                advance();
            } else if (state == 2 && minecraft.screen instanceof BackpackHandledScreen && ticks - entered > 30) {
                BackpackHandledScreen screen = (BackpackHandledScreen) minecraft.screen;
                BackpackScreenHandler menu = screen.getMenu();
                check(menu.slots.size() == 90, "Expected server 9x6 or recovery 9x6 slots, found " + menu.slots.size());
                check(screen.getXSize() == 178 && screen.getYSize() == 224, "Rendered menu has client-local geometry");
                check((menu.getSlot(0).getItem().getItem() == Items.DIAMOND) && menu.getSlot(0).getItem().getCount() == 64,
                        "First saved diamond stack missing");
                check((menu.getSlot(50).getItem().getItem() == Items.DIAMOND) && menu.getSlot(50).getItem().getCount() == 7,
                        "Late saved diamond stack missing");
                if (phase.equals("write")) {
                    check((menu.getSlot(53).getItem().getItem() == Items.NETHERITE_CHESTPLATE), "Late named armor missing");
                    checkArmor(menu.getSlot(53).getItem());
                } else {
                    check(!menu.getSlot(50).mayPlace(new ItemStack(Items.DIAMOND)), "Recovery slot accepts new items");
                }
                Screenshot.grab(minecraft.gameDirectory, "inmis-" + phase + "-menu.png", minecraft.getMainRenderTarget(),
                        message -> Inmis.LOGGER.info("Client verification screenshot: {}", message.getString()));
                // A real number-key swap packet must not move the currently open backpack.
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.SWAP, minecraft.player);
                screen.mouseClicked(screen.getGuiLeft() + screen.getXSize() - 11, screen.getGuiTop() + 11, 0);
                advance();
            } else if (state == 3 && minecraft.player != null && ticks - entered > 20) {
                BackpackHandledScreen screen = (BackpackHandledScreen) minecraft.screen;
                java.lang.reflect.Field settings = BackpackHandledScreen.class.getDeclaredField("showSettings");
                settings.setAccessible(true);
                check(settings.getBoolean(screen), "Upgrades settings button did not open its panel");
                Screenshot.grab(minecraft.gameDirectory, "inmis-" + phase + "-settings.png",
                        minecraft.getMainRenderTarget(),
                        message -> Inmis.LOGGER.info("Client settings screenshot: {}", message.getString()));
                check(minecraft.player.getInventory().getItem(0).getItem() instanceof BackpackItem,
                        "Number-key swap moved the active backpack");
                BackpackScreenHandler menu = (BackpackScreenHandler) minecraft.player.containerMenu;
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, phase.equals("write") ? 53 : 50,
                        0, ClickType.QUICK_MOVE, minecraft.player);
                advance();
            } else if (state == 4 && minecraft.player != null && ticks - entered > 30) {
                BackpackScreenHandler menu = (BackpackScreenHandler) minecraft.player.containerMenu;
                int extractedSlot = phase.equals("write") ? 53 : 50;
                check(menu.getSlot(extractedSlot).getItem().isEmpty(), "Extracted item remains in server-synchronized menu");
                check(inventoryCount(minecraft, Items.NETHERITE_CHESTPLATE) == 1, "Named armor is missing or duplicated");
                check(inventoryCount(minecraft, Items.DIAMOND) == (phase.equals("write") ? 0 : 7),
                        "Extracted diamonds missing or duplicated");
                minecraft.player.closeContainer();
                advance();
            } else if (state == 5 && minecraft.player != null && ticks - entered > 20) {
                minecraft.gameMode.useItem(minecraft.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                advance();
            } else if (state == 6 && minecraft.screen instanceof BackpackHandledScreen && ticks - entered > 30) {
                BackpackHandledScreen screen = (BackpackHandledScreen) minecraft.screen;
                BackpackScreenHandler menu = screen.getMenu();
                check(menu.slots.size() == (phase.equals("write") ? 90 : 63), "Reopening did not use current saved contents/capacity");
                check(menu.getSlot(0).getItem().getCount() == 64, "Reopening reverted inventory changes");
                if (phase.equals("write")) check(menu.getSlot(53).getItem().isEmpty(), "Reopening restored extracted armor");
                check(inventoryCount(minecraft, Items.NETHERITE_CHESTPLATE) == 1, "Armor duplicated after reopening");
                minecraft.player.closeContainer();
                advance();
            } else if (state == 7 && ticks - entered > 30) {
                requestRenderer("chest");
                advance();
            } else if (state == 8 && ticks - entered > 30) {
                checkDyedBackpack(minecraft.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST),
                        0x36C7E8, "Server-provided cyan chest backpack did not synchronize");
                if (Inmis.CURIOS_LOADED) check(CuriosRenderChecks.equipped(minecraft).isEmpty(),
                        "Native Curios slot was not cleared for the chest render check");
                capture(minecraft, "chest-render");
                if (Inmis.CURIOS_LOADED) {
                    requestRenderer("curios");
                    advance();
                } else {
                    finishPassed(minecraft);
                }
            } else if (state == 9 && ticks - entered > 30) {
                check(minecraft.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(),
                        "Chest slot was not cleared for the native Curios render check");
                checkDyedBackpack(CuriosRenderChecks.equipped(minecraft), 0xBF51E8,
                        "Server-provided purple Curios backpack did not synchronize");
                capture(minecraft, "curios-render");
                finishPassed(minecraft);
            }
        } catch (Throwable failure) {
            Inmis.LOGGER.error("Client smoke verification failed", failure);
            finish(minecraft, "FAIL " + phase + ": " + failure);
        }
    }

    private static void requestRenderer(String stage) throws java.io.IOException {
        Files.write(java.nio.file.Paths.get("../server/renderer-request.txt"),
                (phase + ":" + stage).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void checkDyedBackpack(ItemStack stack, int rgb, String message) {
        check(stack.getItem() instanceof BackpackItem
                && ((BackpackItem) stack.getItem()).getTier().getName().equals("frayed")
                && stack.getItem() instanceof net.minecraft.world.item.DyeableLeatherItem
                && ((net.minecraft.world.item.DyeableLeatherItem) stack.getItem()).getColor(stack) == rgb, message);
    }

    private static void holdRenderCamera(Minecraft minecraft) {
        minecraft.mouseHandler.releaseMouse();
        minecraft.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        minecraft.player.setXRot(0);
        minecraft.player.xRotO = 0;
        minecraft.player.setYRot(0);
        minecraft.player.yRotO = 0;
        minecraft.player.yBodyRot = minecraft.player.yBodyRotO = 0;
        minecraft.player.yHeadRot = minecraft.player.yHeadRotO = 0;
    }

    private static void capture(Minecraft minecraft, String suffix) {
        Screenshot.grab(minecraft.gameDirectory, "inmis-" + phase + "-" + suffix + ".png",
                minecraft.getMainRenderTarget(),
                message -> Inmis.LOGGER.info("Backpack renderer screenshot: {}", message.getString()));
    }

    private static void finishPassed(Minecraft minecraft) {
        finish(minecraft, "PASS " + phase
                + ": real menu packets, render, active-slot swap, extraction, reopen, persistence and equipped backpack render");
    }

    private static final class CuriosRenderChecks {
        private static ItemStack equipped(Minecraft minecraft) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(minecraft.player)
                    .orElseThrow(() -> new AssertionError("Curios player capability missing"))
                    .getStacksHandler("back").orElseThrow(() -> new AssertionError("Curios back slot missing"))
                    .getStacks().getStackInSlot(0);
        }
    }

    private static int inventoryCount(Minecraft minecraft, net.minecraft.world.item.Item item) {
        return minecraft.player.getInventory().items.stream().filter(stack -> (stack.getItem() == item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkArmor(ItemStack armor) {
        check(armor.getHoverName().getString().equals("Inmis persistence armor"), "Armor custom name was lost");
        check(armor.getDamageValue() == 23, "Armor damage was lost");
        check(net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION, armor) == 3, "Armor enchantment was lost");
    }

    private static void advance() {
        state++;
        entered = ticks;
    }

    private static void finish(Minecraft minecraft, String result) {
        finished = true;
        finishedAt = ticks;
        try {
            Files.write(java.nio.file.Paths.get("result-" + phase + ".txt"), (result + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception failure) {
            Inmis.LOGGER.error("Unable to write client verification result", failure);
        }
        Inmis.LOGGER.info(result);
    }
}
