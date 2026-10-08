package draylar.inmis.gametest;

import draylar.inmis.Inmis;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.network.ServerNetworking;
import draylar.inmis.ui.BackpackHandledScreen;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in client/server verification. Never packaged into the player mod. */
@EventBusSubscriber(modid = "inmis_game_tests", value = Dist.CLIENT)
public final class InmisClientSmoke {
    private static int state;
    private static int ticks;
    private static int entered;
    private static String phase;
    private static boolean finished;
    private static int finishedAt;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("inmis.smokeClient")) return;
        Minecraft minecraft = Minecraft.getInstance();
        ticks++;
        if (finished) {
            waitForServerSave(minecraft);
            return;
        }
        try {
            if (phase == null) phase = Files.readString(Path.of("../server/phase.txt")).trim();
            if (ticks > 6000) throw new AssertionError("Client verification timed out at state " + state
                    + ", screen=" + minecraft.screen);
            if (state >= 8 && minecraft.player != null) holdRenderCamera(minecraft);
            if (state == 0 && minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
                ConnectScreen.startConnecting(minecraft.screen, minecraft,
                        ServerAddress.parseString("127.0.0.1:25576"),
                        new ServerData("Isolated Inmis verification", "127.0.0.1:25576", ServerData.Type.OTHER), false, null);
                advance();
            } else if (state == 1 && minecraft.player != null && minecraft.level != null && ticks - entered > 60) {
                ItemStack backpack = minecraft.player.getInventory().getItem(0);
                check(backpack.getItem() instanceof BackpackItem, "Server-provided backpack missing from hotbar");
                BackpackItem item = (BackpackItem) backpack.getItem();
                check(item.getTier().getRowWidth() == 11 && item.getTier().getNumberOfRows() == 6,
                        "Client must retain default Withered dimensions 11x6");
                if (!phase.equals("write")) check(inventoryCount(minecraft, Items.NETHERITE_CHESTPLATE) == 1,
                        "Named armor did not survive server/client restart");
                if (!phase.equals("write")) checkArmor(minecraft.player.getInventory().getNonEquipmentItems().stream()
                        .filter(stack -> stack.is(Items.NETHERITE_CHESTPLATE)).findFirst().orElseThrow());
                ServerNetworking.sendOpenBackpack();
                advance();
            } else if (state == 2 && minecraft.screen instanceof BackpackHandledScreen screen && ticks - entered > 30) {
                BackpackScreenHandler menu = screen.getMenu();
                check(menu.slots.size() == 90, "Expected server 9x6 or recovery 9x6 slots, found " + menu.slots.size());
                check(screen.getXSize() == 178 && screen.getYSize() == 224, "Rendered menu has client-local geometry");
                check(menu.getSlot(0).getItem().is(Items.DIAMOND) && menu.getSlot(0).getItem().getCount() == 64,
                        "First saved diamond stack missing");
                check(menu.getSlot(50).getItem().is(Items.DIAMOND) && menu.getSlot(50).getItem().getCount() == 7,
                        "Late saved diamond stack missing");
                if (phase.equals("write")) {
                    check(menu.getSlot(53).getItem().is(Items.NETHERITE_CHESTPLATE), "Late named armor missing");
                    checkArmor(menu.getSlot(53).getItem());
                } else {
                    check(!menu.getSlot(50).mayPlace(new ItemStack(Items.DIAMOND)), "Recovery slot accepts new items");
                }
                Screenshot.grab(minecraft.gameDirectory, "inmis-" + phase + "-menu.png", minecraft.getMainRenderTarget(), 1,
                        message -> Inmis.LOGGER.info("Client verification screenshot: {}", message.getString()));
                // A real number-key swap packet must not move the currently open backpack.
                minecraft.gameMode.handleContainerInput(menu.containerId, 0, 0, ContainerInput.SWAP, minecraft.player);
                MouseButtonEvent settingsClick = new MouseButtonEvent(
                        screen.getGuiLeft() + screen.getXSize() - 11, screen.getGuiTop() + 11,
                        new MouseButtonInfo(0, 0));
                check(screen.mouseClicked(settingsClick, false), "Upgrades panel did not consume its mouse click");
                check(screen.mouseReleased(settingsClick), "Upgrades panel release reached the inventory slots");
                advance();
            } else if (state == 3 && minecraft.player != null && ticks - entered > 20) {
                check(minecraft.player.getInventory().getItem(0).getItem() instanceof BackpackItem,
                        "Number-key swap moved the active backpack");
                BackpackScreenHandler menu = (BackpackScreenHandler) minecraft.player.containerMenu;
                Screenshot.grab(minecraft.gameDirectory, "inmis-" + phase + "-settings.png", minecraft.getMainRenderTarget(), 1,
                        message -> Inmis.LOGGER.info("Upgrades panel screenshot: {}", message.getString()));
                minecraft.gameMode.handleContainerInput(menu.containerId, phase.equals("write") ? 53 : 50,
                        0, ContainerInput.QUICK_MOVE, minecraft.player);
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
                ServerNetworking.sendOpenBackpack();
                advance();
            } else if (state == 6 && minecraft.screen instanceof BackpackHandledScreen screen && ticks - entered > 30) {
                BackpackScreenHandler menu = screen.getMenu();
                check(menu.slots.size() == (phase.equals("write") ? 90 : 63), "Reopening did not use current saved contents/capacity");
                check(menu.getSlot(0).getItem().getCount() == 64, "Reopening reverted inventory changes");
                if (phase.equals("write")) check(menu.getSlot(53).getItem().isEmpty(), "Reopening restored extracted armor");
                check(inventoryCount(minecraft, Items.NETHERITE_CHESTPLATE) == 1, "Armor duplicated after reopening");
                minecraft.player.closeContainer();
                advance();
            } else if (state == 7 && ticks - entered > 30) {
                Files.writeString(Path.of("../server/renderer-request.txt"), phase + ":chest");
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                advance();
            } else if (state == 8 && ticks - entered > 30) {
                checkDyedBackpack(minecraft.player.getItemBySlot(EquipmentSlot.CHEST), 0x36C7E8,
                        "Server-synchronized chest renderer fixture is missing or has the wrong tier/color");
                if (Inmis.CURIOS_LOADED) check(CuriosRenderChecks.equipped(minecraft).isEmpty(),
                        "Native Curios slot is not empty for the chest renderer capture");
                capture(minecraft, "chest-render");
                if (Inmis.CURIOS_LOADED) {
                    Files.writeString(Path.of("../server/renderer-request.txt"), phase + ":curios");
                    advance();
                } else {
                    state = 10;
                    entered = ticks;
                }
            } else if (state == 9 && ticks - entered > 30) {
                check(minecraft.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                        "Server did not clear chest equipment before the Curios renderer capture");
                checkDyedBackpack(CuriosRenderChecks.equipped(minecraft), 0xBF51E8,
                        "Server-synchronized Curios renderer fixture is missing or has the wrong tier/color");
                capture(minecraft, "curios-render");
                advance();
            } else if (state == 10 && ticks - entered > 20) {
                finishPassed(minecraft);
            }
        } catch (Throwable failure) {
            Inmis.LOGGER.error("Client smoke verification failed", failure);
            finish(minecraft, "FAIL " + phase + ": " + failure);
        }
    }

    private static void checkDyedBackpack(ItemStack stack, int rgb, String message) {
        DyedItemColor color = stack.get(DataComponents.DYED_COLOR);
        check(stack.getItem() instanceof BackpackItem item && item.getTier().getName().equals("frayed")
                && color != null && color.rgb() == rgb, message);
    }

    private static void holdRenderCamera(Minecraft minecraft) {
        minecraft.mouseHandler.releaseMouse();
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        var player = minecraft.player;
        player.setXRot(0);
        player.setYRot(0);
        player.xRotO = 0;
        player.yRotO = 0;
        player.yBodyRot = 0;
        player.yBodyRotO = 0;
        player.yHeadRot = 0;
        player.yHeadRotO = 0;
    }

    private static void capture(Minecraft minecraft, String suffix) {
        Screenshot.grab(minecraft.gameDirectory, "inmis-" + phase + "-" + suffix + ".png",
                minecraft.getMainRenderTarget(), 1,
                message -> Inmis.LOGGER.info("Backpack renderer screenshot: {}", message.getString()));
    }

    private static void finishPassed(Minecraft minecraft) {
        finish(minecraft, "PASS " + phase
                + ": real menu packets, render, active-slot swap, extraction, reopen, persistence and equipped backpack render");
    }

    private static final class CuriosRenderChecks {
        private static ItemStack equipped(Minecraft minecraft) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(minecraft.player).orElseThrow()
                    .getStacksHandler("back").orElseThrow().getStacks().getStackInSlot(0);
        }
    }

    private static int inventoryCount(Minecraft minecraft, net.minecraft.world.item.Item item) {
        return minecraft.player.getInventory().getNonEquipmentItems().stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkArmor(ItemStack armor) {
        check(armor.getHoverName().getString().equals("Inmis persistence armor"), "Armor custom name was lost");
        check(armor.getDamageValue() == 23, "Armor damage was lost");
        check(armor.getEnchantments().entrySet().stream().anyMatch(entry ->
                entry.getKey().is(Enchantments.PROTECTION) && entry.getIntValue() == 3), "Armor enchantment was lost");
    }

    private static void advance() {
        state++;
        entered = ticks;
    }

    private static void finish(Minecraft minecraft, String result) {
        finished = true;
        finishedAt = ticks;
        try {
            Files.writeString(Path.of("result-" + phase + ".txt"), result + "\n");
        } catch (Exception failure) {
            Inmis.LOGGER.error("Unable to write client verification result", failure);
        }
        Inmis.LOGGER.info(result);
    }

    private static void waitForServerSave(Minecraft minecraft) {
        try {
            Path acknowledgement = Path.of("../server/server-result-" + phase + ".txt");
            if (Files.exists(acknowledgement) && Files.size(acknowledgement) > 0) {
                minecraft.stop();
            } else if (ticks - finishedAt >= 400) {
                String failure = "FAIL " + phase + ": server save acknowledgement timed out";
                Files.writeString(Path.of("result-" + phase + ".txt"), failure + "\n");
                Inmis.LOGGER.error(failure);
                minecraft.stop();
            }
        } catch (Exception failure) {
            Inmis.LOGGER.error("Unable to wait for server save acknowledgement", failure);
            minecraft.stop();
        }
    }
}
