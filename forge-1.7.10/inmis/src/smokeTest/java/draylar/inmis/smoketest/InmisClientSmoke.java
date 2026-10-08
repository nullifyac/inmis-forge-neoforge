package draylar.inmis.smoketest;

import draylar.inmis.Inmis;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.network.ServerNetworking;
import draylar.inmis.ui.BackpackHandledScreen;
import draylar.inmis.ui.BackpackScreenHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.multiplayer.ServerData;

import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.enchantment.Enchantment;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.gameevent.TickEvent;

import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in client/server verification. Never packaged into the player mod. */
public final class InmisClientSmoke {
    public static void register() {
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new InmisClientSmoke());
    }

    private static int state;
    private static int ticks;
    private static int entered;
    private static String phase;
    private static boolean finished;
    private static int finishedAt;
    private static int settingsCheckStage;
    private static int originalGuiScale;
    private static BackpackAugmentsComponent originalSettings;
    private static boolean expectedFunnelling;
    private static java.util.List<net.minecraft.util.ResourceLocation> expectedFilters;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("inmis.smokeClient")) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        ticks++;
        if (finished) {
            // Keep native capabilities valid until the server restores equipment and saves.
            Path acknowledgement = java.nio.file.Paths.get("../server/server-result-" + phase + ".txt");
            try {
                if (Files.exists(acknowledgement) && Files.size(acknowledgement) > 0) {
                    minecraft.shutdown();
                    return;
                }
            } catch (java.io.IOException ignored) {
                // The result file may be between creation and its short write.
            }
            if (ticks - finishedAt > 400) {
                Inmis.LOGGER.error("Server did not acknowledge client verification before shutdown");
                minecraft.shutdown();
            }
            return;
        }
        try {
            if (phase == null) phase = new String(Files.readAllBytes(java.nio.file.Paths.get("../server/phase.txt")), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (state >= 7 && minecraft.thePlayer != null) holdRenderCamera(minecraft);
            if (ticks > 6000) throw new AssertionError("Client verification timed out at state " + state
                    + ", screen=" + minecraft.currentScreen);
            if (state == 0 && minecraft.currentScreen instanceof GuiMainMenu) {
                // The multiplayer screen initializes Forge's server-list data before a join.
                minecraft.displayGuiScreen(new GuiMultiplayer(minecraft.currentScreen));
            } else if (state == 0 && minecraft.currentScreen instanceof GuiMultiplayer) {
                // Use the normal Forge join path so its play-client handshake latch is initialized.
                cpw.mods.fml.client.FMLClientHandler.instance().connectToServer(minecraft.currentScreen,
                        new ServerData("Inmis local verification", "127.0.0.1:"
                                + Integer.getInteger("inmis.smokePort", 25576)));
                advance();
            } else if (state == 1 && minecraft.thePlayer != null && minecraft.theWorld != null && ticks - entered > 60) {
                ItemStack backpack = minecraft.thePlayer.inventory.getStackInSlot(0);
                check(backpack.getItem() instanceof BackpackItem, "Server-provided backpack missing from hotbar");
                check(backpack.getDisplayName().equals("Withered Backpack"),
                        "Native backpack display name did not resolve its .name translation");
                BackpackItem item = (BackpackItem) backpack.getItem();
                check(item.getTier().getRowWidth() == 11 && item.getTier().getNumberOfRows() == 6,
                        "Client must retain default Withered dimensions 11x6");
                if (draylar.inmis.compat.BaublesCompat.isLoaded()) {
                    check(!(draylar.inmis.compat.BaublesCompat.findFirstEquippedBackpack(minecraft.thePlayer) == null),
                            "Native Baubles equipped backpack was not synchronized to the client");
                    minecraft.gameSettings.thirdPersonView = 1;
                }
                if (!phase.equals("write")) check(inventoryCount(minecraft, Items.diamond_chestplate) == 1,
                        "Named armor did not survive server/client restart");
                if (!phase.equals("write")) checkArmor(java.util.Arrays.stream(minecraft.thePlayer.inventory.mainInventory)
                        .filter(stack -> stack != null && (stack.getItem() == Items.diamond_chestplate)).findFirst().orElseThrow(() -> new AssertionError("Fixture armor missing")));
                minecraft.playerController.sendUseItem(minecraft.thePlayer, minecraft.theWorld, minecraft.thePlayer.getHeldItem());
                advance();
            } else if (state == 2 && minecraft.currentScreen instanceof BackpackHandledScreen && ticks - entered > 30) {
                BackpackHandledScreen screen = (BackpackHandledScreen) minecraft.currentScreen;
                BackpackScreenHandler menu = screen.getMenu();
                check(((net.minecraft.util.IChatComponent) field(screen, "title")).getUnformattedText().equals("Withered Backpack"),
                        "Native backpack menu title was not localized correctly");
                check(menu.inventorySlots.size() == 90, "Expected server 9x6 or recovery 9x6 slots, found " + menu.inventorySlots.size());
                check(screen.getXSize() == 178 && screen.getYSize() == 224, "Rendered menu has client-local geometry");
                check((menu.getSlot(0).getStack().getItem() == Items.diamond) && menu.getSlot(0).getStack().stackSize == 64,
                        "First saved diamond stack missing");
                check((menu.getSlot(50).getStack().getItem() == Items.diamond) && menu.getSlot(50).getStack().stackSize == 7,
                        "Late saved diamond stack missing");
                if (phase.equals("write")) {
                    check((menu.getSlot(53).getStack().getItem() == Items.diamond_chestplate), "Late named armor missing");
                    checkArmor(menu.getSlot(53).getStack());
                } else {
                    check(!menu.getSlot(50).isItemValid(new ItemStack(Items.diamond)), "Recovery slot accepts new items");
                }
                ScreenShotHelper.saveScreenshot(minecraft.mcDataDir, "inmis-" + phase + "-menu.png", minecraft.displayWidth, minecraft.displayHeight, minecraft.getFramebuffer());
                // A real number-key swap packet must not move the currently open backpack.
                minecraft.playerController.windowClick(menu.windowId, 0, 0, 2, minecraft.thePlayer);
                screen.mouseClicked(screen.getGuiLeft() + screen.getXSize() - 11, screen.getGuiTop() + 11, 0);
                advance();
            } else if (state == 3 && minecraft.thePlayer != null && ticks - entered > 20) {
                BackpackHandledScreen screen = (BackpackHandledScreen) minecraft.currentScreen;
                java.lang.reflect.Field settings = BackpackHandledScreen.class.getDeclaredField("showSettings");
                settings.setAccessible(true);
                check(settings.getBoolean(screen), "Upgrades settings button did not open its panel");
                if (!verifySettingsControls(minecraft, screen)) return;
                ScreenShotHelper.saveScreenshot(minecraft.mcDataDir, "inmis-" + phase + "-settings.png", minecraft.displayWidth, minecraft.displayHeight, minecraft.getFramebuffer());
                check(minecraft.thePlayer.inventory.getStackInSlot(0).getItem() instanceof BackpackItem,
                        "Number-key swap moved the active backpack");
                BackpackScreenHandler menu = (BackpackScreenHandler) minecraft.thePlayer.openContainer;
                minecraft.playerController.windowClick(menu.windowId, phase.equals("write") ? 53 : 50,
                        0, 1, minecraft.thePlayer);
                advance();
            } else if (state == 4 && minecraft.thePlayer != null && ticks - entered > 30) {
                BackpackScreenHandler menu = (BackpackScreenHandler) minecraft.thePlayer.openContainer;
                int extractedSlot = phase.equals("write") ? 53 : 50;
                check((menu.getSlot(extractedSlot).getStack() == null), "Extracted item remains in server-synchronized menu");
                check(inventoryCount(minecraft, Items.diamond_chestplate) == 1, "Named armor is missing or duplicated");
                check(inventoryCount(minecraft, Items.diamond) == (phase.equals("write") ? 0 : 7),
                        "Extracted diamonds missing or duplicated");
                minecraft.thePlayer.closeScreen();
                advance();
            } else if (state == 5 && minecraft.thePlayer != null && ticks - entered > 20) {
                minecraft.playerController.sendUseItem(minecraft.thePlayer, minecraft.theWorld, minecraft.thePlayer.getHeldItem());
                advance();
            } else if (state == 6 && minecraft.currentScreen instanceof BackpackHandledScreen && ticks - entered > 30) {
                BackpackHandledScreen screen = (BackpackHandledScreen) minecraft.currentScreen;
                BackpackScreenHandler menu = screen.getMenu();
                check(menu.inventorySlots.size() == (phase.equals("write") ? 90 : 63), "Reopening did not use current saved contents/capacity");
                check(menu.getSlot(0).getStack().stackSize == 64, "Reopening reverted inventory changes");
                if (phase.equals("write")) check((menu.getSlot(53).getStack() == null), "Reopening restored extracted armor");
                check(inventoryCount(minecraft, Items.diamond_chestplate) == 1, "Armor duplicated after reopening");
                minecraft.thePlayer.closeScreen();
                advance();
            } else if (state == 7 && ticks - entered > 30) {
                requestRenderer("chest");
                advance();
            } else if (state == 8 && ticks - entered > 30) {
                checkDyedBackpack(minecraft.thePlayer.inventory.armorItemInSlot(2),
                        0x36C7E8, "Server-provided cyan chest backpack did not synchronize");
                if (draylar.inmis.compat.BaublesCompat.isLoaded()) check((BaublesRenderChecks.equipped(minecraft) == null),
                        "Native Baubles slot was not cleared for the chest render check");
                capture(minecraft, "chest-render");
                if (draylar.inmis.compat.BaublesCompat.isLoaded()) {
                    requestRenderer("baubles");
                    advance();
                } else {
                    finishPassed(minecraft);
                }
            } else if (state == 9 && ticks - entered > 30) {
                check((minecraft.thePlayer.inventory.armorItemInSlot(2) == null),
                        "Chest slot was not cleared for the native Baubles render check");
                checkDyedBackpack(BaublesRenderChecks.equipped(minecraft), 0xBF51E8,
                        "Server-provided purple Baubles backpack did not synchronize");
                capture(minecraft, "baubles-render");
                finishPassed(minecraft);
            }
        } catch (Throwable failure) {
            Inmis.LOGGER.error("Client smoke verification failed", failure);
            finish(minecraft, "FAIL " + phase + ": " + failure);
        }
    }

    /** Drives actual production GUI clicks, then observes the separate server-synchronized backpack. */
    private static boolean verifySettingsControls(Minecraft minecraft, BackpackHandledScreen screen) throws Exception {
        ItemStack serverStack = minecraft.thePlayer.inventory.getStackInSlot(0);
        check(serverStack != screen.getMenu().getBackpackStack(), "Opening descriptor must be separate from synchronized inventory");
        BackpackAugmentsComponent serverSettings = Inmis.getOrCreateAugments(serverStack,
                ((BackpackItem) serverStack.getItem()).getTier());
        if (settingsCheckStage == 0) {
            originalGuiScale = minecraft.gameSettings.guiScale;
            minecraft.gameSettings.guiScale = 3;
            resizeScreen(minecraft, screen);
            originalSettings = settings(screen);
            setField(screen, "settingsScroll", 18);
            Object layout = layout(screen);
            check(layoutInt(layout, "scrollMax") >= 18, "Fixture must expose settings scrolling");
            int toggleX = layoutInt(layout, "toggleX") + 3;
            int headerY = layoutInt(layout, "contentTop") - 8;
            screen.mouseClicked(toggleX, headerY, 0);
            check(settings(screen).toTag().equals(originalSettings.toTag()), "Hidden header row changed settings");
            screen.drawScreen(toggleX, headerY, 0);
            check(field(screen, "queuedTooltip") == null, "Hidden header row produced a tooltip");

            int visibleHeight = layoutInt(layout, "visibleHeight");
            int rowCount = ((java.util.List<?>) field(screen, "unlockedAugments")).size();
            int hiddenIndex = Math.min((visibleHeight - 6 + 17) / 18, rowCount - 1);
            setField(screen, "settingsScroll", Math.max(0, hiddenIndex * 18 + 6 - visibleHeight));
            layout = layout(screen);
            int footerY = layoutInt(layout, "contentTop") + visibleHeight + 1;
            int hiddenToggleY = layoutInt(layout, "listStartY") + hiddenIndex * 18 + 4;
            check(footerY >= hiddenToggleY && footerY < hiddenToggleY + 10,
                    "Footer fixture must intersect an existing scissored toggle");
            screen.mouseClicked(toggleX, footerY, 0);
            check(settings(screen).toTag().equals(originalSettings.toTag()), "Hidden footer row changed settings");
            screen.drawScreen(toggleX, footerY, 0);
            check(field(screen, "queuedTooltip") == null, "Hidden footer row produced a tooltip");

            setField(screen, "settingsScroll", 0);
            layout = layout(screen);
            expectedFunnelling = !originalSettings.funnelling().enabled();
            screen.mouseClicked(layoutInt(layout, "toggleX") + 3, layoutInt(layout, "listStartY") + 8, 0);
            check(settings(screen).funnelling().enabled() == expectedFunnelling, "Visible row did not toggle through GUI");
            settingsCheckStage = 1;
            entered = ticks;
            return false;
        }
        if (settingsCheckStage == 1) {
            if (serverSettings.funnelling().enabled() != expectedFunnelling && ticks - entered < 100) return false;
            check(serverSettings.funnelling().enabled() == expectedFunnelling, "Visible GUI toggle did not reach server inventory");
            Object initial = layout(screen);
            int wantedButtonY = layoutInt(initial, "contentTop") + 12;
            int scroll = layoutInt(initial, "settingsStartY") + 36 - wantedButtonY;
            setField(screen, "settingsScroll", scroll);
            Object layout = layout(screen);
            int buttonY = layoutInt(layout, "settingsStartY") + 36;
            check(buttonY >= layoutInt(layout, "contentTop") && buttonY + 10 <= layoutInt(layout, "contentTop") + layoutInt(layout, "visibleHeight"),
                    "Set Filters button must be inside clipped content");
            screen.mouseClicked(layoutInt(layout, "contentX") + 2, buttonY + 2, 0);
            java.util.List<net.minecraft.util.ResourceLocation> filters = settings(screen).funnelling().filters();
            check(filters.size() > 9, "Set Filters must derive more than one page from synchronized native slots");
            int gridY = layoutInt(layout, "settingsStartY") + 48;
            screen.mouseClicked(layoutInt(layout, "toggleX") + 3, gridY - 7, 0);
            check(((Integer) field(screen, "filterPage")) == 1, "Scrolled filter next-page button failed");
            net.minecraft.util.ResourceLocation removed = filters.get(9);
            screen.mouseClicked(layoutInt(layout, "contentX") + 2, gridY + 2, 0);
            java.util.List<net.minecraft.util.ResourceLocation> after = settings(screen).funnelling().filters();
            check(after.size() == filters.size() - 1 && !after.contains(removed), "Scrolled second-page removal changed wrong filter");
            screen.mouseClicked(layoutInt(layout, "toggleX") - 9, gridY - 7, 0);
            check(((Integer) field(screen, "filterPage")) == 0, "Scrolled filter previous-page button failed");
            expectedFilters = new java.util.ArrayList<>(after);
            settingsCheckStage = 2;
            entered = ticks;
            return false;
        }
        if (settingsCheckStage == 2) {
            if (!serverSettings.funnelling().filters().equals(expectedFilters) && ticks - entered < 100) return false;
            check(serverSettings.funnelling().filters().equals(expectedFilters), "GUI filter paging/removal did not reach server inventory");
            Inmis.setAugments(screen.getMenu().getBackpackStack(), screen.getMenu().getItem().getTier(), originalSettings);
            ServerNetworking.sendUpdateBackpackAugments(screen.getMenu().windowId, originalSettings);
            settingsCheckStage = 3;
            entered = ticks;
            return false;
        }
        if (settingsCheckStage == 3) {
            if (!serverSettings.funnelling().toTag().equals(originalSettings.funnelling().toTag()) && ticks - entered < 100) return false;
            check(serverSettings.funnelling().toTag().equals(originalSettings.funnelling().toTag()), "Server did not restore original settings before save");
            minecraft.gameSettings.guiScale = originalGuiScale;
            resizeScreen(minecraft, screen);
            settingsCheckStage = 4;
            Inmis.LOGGER.info("Client GUI checks passed: clipped header/footer click and hover, visible network toggle, synchronized filter paging/removal");
        }
        return true;
    }

    private static void resizeScreen(Minecraft minecraft, BackpackHandledScreen screen) {
        net.minecraft.client.gui.ScaledResolution resolution = new net.minecraft.client.gui.ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        screen.setWorldAndResolution(minecraft, resolution.getScaledWidth(), resolution.getScaledHeight());
    }

    private static BackpackAugmentsComponent settings(BackpackHandledScreen screen) {
        return Inmis.getOrCreateAugments(screen.getMenu().getBackpackStack(), screen.getMenu().getItem().getTier());
    }

    private static Object layout(BackpackHandledScreen screen) throws Exception {
        java.lang.reflect.Method method = BackpackHandledScreen.class.getDeclaredMethod("getSettingsLayout");
        method.setAccessible(true);
        return method.invoke(screen);
    }

    private static int layoutInt(Object layout, String name) throws Exception {
        java.lang.reflect.Field field = layout.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(layout);
    }

    private static Object field(BackpackHandledScreen screen, String name) throws Exception {
        java.lang.reflect.Field field = BackpackHandledScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }

    private static void setField(BackpackHandledScreen screen, String name, int value) throws Exception {
        java.lang.reflect.Field field = BackpackHandledScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(screen, value);
    }

    private static void requestRenderer(String stage) throws java.io.IOException {
        Files.write(java.nio.file.Paths.get("../server/renderer-request.txt"),
                (phase + ":" + stage).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void checkDyedBackpack(ItemStack stack, int rgb, String message) {
        check(stack != null && stack.getItem() instanceof BackpackItem
                && ((BackpackItem) stack.getItem()).getTier().getName().equals("frayed")
                && stack.getItem() instanceof draylar.inmis.item.DyeableBackpackItem
                && ((draylar.inmis.item.DyeableBackpackItem) stack.getItem()).getColor(stack) == rgb, message);
    }

    private static void holdRenderCamera(Minecraft minecraft) {
        minecraft.setIngameNotInFocus();
        minecraft.gameSettings.thirdPersonView = 1;
        minecraft.thePlayer.rotationPitch = minecraft.thePlayer.prevRotationPitch = 0;
        minecraft.thePlayer.rotationYaw = minecraft.thePlayer.prevRotationYaw = 0;
        minecraft.thePlayer.renderYawOffset = minecraft.thePlayer.prevRenderYawOffset = 0;
        minecraft.thePlayer.rotationYawHead = minecraft.thePlayer.prevRotationYawHead = 0;
    }

    private static void capture(Minecraft minecraft, String suffix) {
        ScreenShotHelper.saveScreenshot(minecraft.mcDataDir, "inmis-" + phase + "-" + suffix + ".png", minecraft.displayWidth, minecraft.displayHeight, minecraft.getFramebuffer());
    }

    private static void finishPassed(Minecraft minecraft) {
        finish(minecraft, "PASS " + phase
                + ": real menu packets, clipped settings clicks/hover, network toggle, filter paging/removal, render, active-slot swap, extraction, reopen, persistence and equipped backpack render");
    }

    private static final class BaublesRenderChecks {
        private static ItemStack equipped(Minecraft minecraft) {
            return draylar.inmis.compat.BaublesCompat.findFirstEquippedBackpack(minecraft.thePlayer);
        }
    }

    private static int inventoryCount(Minecraft minecraft, net.minecraft.item.Item item) {
        return java.util.Arrays.stream(minecraft.thePlayer.inventory.mainInventory).filter(stack -> stack != null && (stack.getItem() == item)).mapToInt(stack -> stack.stackSize).sum();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkArmor(ItemStack armor) {
        check(armor.getDisplayName().equals("Inmis persistence armor"), "Armor custom name was lost");
        check(armor.getItemDamage() == 23, "Armor damage was lost");
        check(net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, armor) == 3, "Armor enchantment was lost");
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
