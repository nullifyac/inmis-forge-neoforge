package draylar.inmis.ui;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import java.io.IOException;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.Tessellator;
import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackAugmentType;
import draylar.inmis.augment.BackpackAugments;
import draylar.inmis.api.Dimension;
import draylar.inmis.api.Rectangle;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.network.ServerNetworking;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class BackpackHandledScreen extends GuiContainer {

    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation("inmis", "textures/gui/backpack_container.png");
    private static final ResourceLocation SLOT_TEXTURE = new ResourceLocation("inmis", "textures/gui/backpack_slot.png");
    private static final ResourceLocation SETTINGS_ICON = new ResourceLocation("inmis", "textures/gui/sprites/backpack/settings.png");
    private static final ResourceLocation TOGGLE_ON_ICON = new ResourceLocation("inmis", "textures/gui/sprites/backpack/toggle_on.png");
    private static final ResourceLocation TOGGLE_OFF_ICON = new ResourceLocation("inmis", "textures/gui/sprites/backpack/toggle_off.png");

    private static final ITextComponent UPGRADES_LABEL = new TextComponentTranslation("inmis.gui.backpack_upgrades");
    private static final ITextComponent UPGRADES_TOOLTIP = new TextComponentTranslation("inmis.gui.backpack_upgrades.tooltip");
    private static final ITextComponent FILTERS_LABEL = new TextComponentTranslation("inmis.gui.filters");
    private static final ITextComponent SET_FILTERS_LABEL = new TextComponentTranslation("inmis.gui.set_filters");

    private static final int SETTINGS_BUTTON_SIZE = 10;
    private static final int SETTINGS_TEXTURE_SIZE = 10;
    private static final int PANEL_MIN_WIDTH = 80;
    private static final int PANEL_MAX_WIDTH = 176;
    private static final int PANEL_PADDING = 6;
    private static final int PANEL_SAFE_MARGIN_X = 20;
    private static final int PANEL_SAFE_MARGIN_Y = 48;
    private static final int ROW_HEIGHT = 18;
    private static final int TOGGLE_SIZE = 10;
    private static final int FILTER_COLUMNS = 3;
    private static final int FILTER_ROWS = 3;
    private static final int FILTER_SLOT_SIZE = 18;
    private static final int SETTINGS_HEADER_HEIGHT = 12;
    private static final int TEXT_LINE_HEIGHT = 12;
    private static final int FILTER_LABEL_HEIGHT = 10;
    private static final int FILTER_GRID_HEIGHT = FILTER_ROWS * FILTER_SLOT_SIZE;

    private final BackpackScreenHandler menu;
    private final InventoryPlayer playerInventory;
    private final ITextComponent title;
    private final int titleLabelX = 8;
    private final int titleLabelY = 7;
    private final int inventoryLabelX;
    private final int inventoryLabelY;
    private final int guiTitleColor = Integer.decode(Inmis.CONFIG.guiTitleColor);
    private boolean showSettings;
    private boolean suppressNextRelease;
    private BackpackAugmentType selectedAugment;
    private int filterPage;
    private int settingsScroll;
    private List<BackpackAugmentType> unlockedAugments = java.util.Collections.emptyList();
    private List<String> queuedTooltip;

    public BackpackHandledScreen(BackpackScreenHandler handler, InventoryPlayer player, ITextComponent title) {
        super(handler);
        this.menu = handler;
        this.playerInventory = player;
        this.title = new TextComponentString(handler.getBackpackStack().getDisplayName());

        Dimension dimension = handler.getDimension();
        this.xSize = dimension.getWidth();
        this.ySize = dimension.getHeight();
        this.inventoryLabelX = handler.getPlayerInvSlotPosition(dimension, 0, 0).x;
        this.inventoryLabelY = this.ySize - 94;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.unlockedAugments = BackpackAugments.getUnlocked(getMenu().getItem().getTier());
        if (!this.unlockedAugments.isEmpty() && (selectedAugment == null || !this.unlockedAugments.contains(selectedAugment))) {
            this.selectedAugment = this.unlockedAugments.get(0);
        }
        if (this.unlockedAugments.isEmpty()) {
            this.selectedAugment = null;
        }
        this.filterPage = 0;
        this.settingsScroll = 0;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float delta, int mouseX, int mouseY) {
        GraphicsAdapter graphics = new GraphicsAdapter();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int x = this.guiLeft;
        int y = this.guiTop;
        renderBackgroundTexture(graphics, new Rectangle(x, y, xSize, ySize), delta, 0xFFFFFFFF);
        for (Slot slot : getMenu().inventorySlots) {
            graphics.blit(SLOT_TEXTURE, x + slot.xPos - 1, y + slot.yPos - 1, 0, 0, 18, 18, 18, 18);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float delta) {
        queuedTooltip = null;
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, delta);
        GraphicsAdapter graphics = new GraphicsAdapter();
        renderSettingsIcon(graphics, mouseX, mouseY);
        if (showSettings) {
            renderSettingsPanel(graphics, mouseX, mouseY);
        }
        if (queuedTooltip != null) {
            this.drawHoveringText(queuedTooltip, mouseX, mouseY);
        } else {
            this.renderHoveredToolTip(mouseX, mouseY);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        GraphicsAdapter graphics = new GraphicsAdapter();
        graphics.drawString(this.fontRenderer, title, titleLabelX, titleLabelY, guiTitleColor, false);
        graphics.drawString(this.fontRenderer, playerInventory.getDisplayName(), inventoryLabelX, inventoryLabelY, guiTitleColor, false);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (isMouseOverSettingsIcon(mouseX, mouseY) && button == 0) {
            this.showSettings = !this.showSettings;
            this.suppressNextRelease = true;
            return;
        }
        if (showSettings && handleSettingsClick(mouseX, mouseY, button)) {
            this.suppressNextRelease = true;
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        if (suppressNextRelease) {
            suppressNextRelease = false;
            return;
        }
        super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || !showSettings) return;
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        SettingsLayout layout = getSettingsLayout();
        if (layout.scrollMax > 0 && isWithin(mouseX, mouseY, layout.x, layout.y, layout.width, layout.height)) {
            settingsScroll = MathHelper.clamp(settingsScroll - Integer.signum(wheel) * ROW_HEIGHT, 0, layout.scrollMax);
        }
    }

    public BackpackScreenHandler getMenu() {
        return menu;
    }

    private int textWidth(ITextComponent text) {
        return fontRenderer.getStringWidth(text.getFormattedText());
    }

    private void renderSettingsIcon(GraphicsAdapter graphics, int mouseX, int mouseY) {
        int x = getSettingsIconX();
        int y = getSettingsIconY();
        graphics.blit(SETTINGS_ICON, x, y, 0, 0, SETTINGS_BUTTON_SIZE, SETTINGS_BUTTON_SIZE, SETTINGS_TEXTURE_SIZE, SETTINGS_TEXTURE_SIZE);
        if (isMouseOverSettingsIcon(mouseX, mouseY)) {
            graphics.fill(x, y, x + SETTINGS_BUTTON_SIZE, y + SETTINGS_BUTTON_SIZE, 0x66FFFFFF);
            queueTooltip(UPGRADES_TOOLTIP);
        }
    }

    private boolean handleSettingsClick(double mouseX, double mouseY, int button) {
        if (unlockedAugments.isEmpty()) {
            return false;
        }
        SettingsLayout layout = getSettingsLayout();
        if (!isWithin(mouseX, mouseY, layout.x, layout.y, layout.width, layout.height)) {
            return false;
        }
        // Scrolling can place a hidden row behind the header or footer. Consume the
        // panel click there without changing settings or forwarding it to inventory slots.
        if (!isWithinSettingsContent(layout, mouseX, mouseY)) {
            return true;
        }

        int rowY = layout.listStartY;
        for (BackpackAugmentType type : unlockedAugments) {
            int rowX = layout.x + 2;
            int rowWidth = layout.width - 4;
            if (isWithin(mouseX, mouseY, rowX, rowY, rowWidth, ROW_HEIGHT)) {
                int toggleX = layout.toggleX;
                int toggleY = rowY + (ROW_HEIGHT - TOGGLE_SIZE) / 2;
                if (isWithin(mouseX, mouseY, toggleX, toggleY, TOGGLE_SIZE, TOGGLE_SIZE)) {
                    toggleAugment(type);
                } else {
                    if (selectedAugment != type) {
                        selectedAugment = type;
                        filterPage = 0;
                        settingsScroll = 0;
                    }
                }
                return true;
            }
            rowY += ROW_HEIGHT;
        }

        if (selectedAugment == null || layout.settingsStartY < 0) {
            return true;
        }
        handleAugmentSettingsClick(layout, mouseX, mouseY, button);
        return true;
    }

    private void renderSettingsPanel(GraphicsAdapter graphics, int mouseX, int mouseY) {
        if (unlockedAugments.isEmpty()) {
            return;
        }
        SettingsLayout layout = getSettingsLayout();
        graphics.fill(layout.x, layout.y, layout.x + layout.width, layout.y + layout.height, 0xCC2A241E);
        graphics.drawString(this.fontRenderer, UPGRADES_LABEL, layout.contentX, layout.y + PANEL_PADDING, 0xFFE0CDB7, false);

        graphics.enableScissor(layout.x, layout.contentTop, layout.x + layout.width, layout.contentTop + layout.visibleHeight);
        BackpackAugmentsComponent augments = getAugments();
        boolean overContent = isWithinSettingsContent(layout, mouseX, mouseY);
        int rowY = layout.listStartY;
        for (BackpackAugmentType type : unlockedAugments) {
            boolean selected = type == selectedAugment;
            if (selected) {
                graphics.fill(layout.x + 2, rowY, layout.x + layout.width - 2, rowY + ROW_HEIGHT, 0x553E352C);
            }
            graphics.blit(type.icon(), layout.contentX, rowY + 1, 0, 0, 16, 16, 16, 16);
            graphics.drawString(this.fontRenderer, type.label(), layout.contentX + 20, rowY + 5, 0xFFE0CDB7, false);
            drawToggleIcon(graphics, layout.toggleX, rowY + (ROW_HEIGHT - TOGGLE_SIZE) / 2, isAugmentEnabled(type, augments));
            if (overContent && isWithin(mouseX, mouseY, layout.x + 2, rowY, layout.width - 4, ROW_HEIGHT)) {
                queueTooltip(buildAugmentTooltip(type));
            }
            rowY += ROW_HEIGHT;
        }

        if (selectedAugment != null && layout.settingsStartY >= 0) {
            renderAugmentSettings(graphics, layout, overContent ? mouseX : Integer.MIN_VALUE,
                    overContent ? mouseY : Integer.MIN_VALUE, augments);
        }
        graphics.disableScissor();
    }

    private void renderAugmentSettings(GraphicsAdapter graphics, SettingsLayout layout, int mouseX, int mouseY,
                                       BackpackAugmentsComponent augments) {
        int y = layout.settingsStartY;
        ITextComponent settingsTitle = new TextComponentTranslation("inmis.gui.upgrade_settings", selectedAugment.label());
        graphics.drawString(this.fontRenderer, settingsTitle, layout.contentX, y, 0xFFE0CDB7, false);
        y += 12;

        switch (selectedAugment) {
            case FUNNELLING: renderFunnellingSettings(graphics, layout, y, mouseX, mouseY, augments.funnelling()); break;
            case QUIVERLINK: renderQuiverlinkSettings(graphics, layout, y, mouseX, mouseY, augments.quiverlink()); break;
            case LOOTBOUND: renderLootboundSettings(graphics, layout, y, mouseX, mouseY, augments.lootbound()); break;
            case LIGHTWEAVER: renderLightweaverSettings(graphics, layout, y, mouseX, mouseY, augments.lightweaver()); break;
            case SEEDFLOW: renderSeedflowSettings(graphics, layout, y, mouseX, mouseY, augments.seedflow()); break;
            case HOPPER_BRIDGE: renderHopperBridgeSettings(graphics, layout, y, mouseX, mouseY, augments.hopperBridge()); break;
            default: {
                break;
            }
        }
    }

    private void renderFunnellingSettings(GraphicsAdapter graphics, SettingsLayout layout, int startY, int mouseX, int mouseY,
                                          BackpackAugmentsComponent.FunnellingSettings settings) {
        ITextComponent modeValue = new TextComponentTranslation("augment.backpacked.funnelling.mode." + settings.mode().getSerializedName());
        ITextComponent label = new TextComponentTranslation("augment.backpacked.funnelling.mode", modeValue);
        graphics.drawString(this.fontRenderer, label, layout.contentX, startY, 0xFFD8C6B2, false);
        if (isWithin(mouseX, mouseY, layout.contentX, startY, textWidth(label), 10)) {
            ITextComponent tooltip = new TextComponentTranslation("augment.backpacked.funnelling.mode." + settings.mode().getSerializedName() + ".tooltip");
            queueTooltip(tooltip);
        }
        int y = startY + TEXT_LINE_HEIGHT;
        graphics.drawString(this.fontRenderer, FILTERS_LABEL, layout.contentX, y, 0xFFD8C6B2, false);
        int buttonY = y + TEXT_LINE_HEIGHT;
        int buttonColor = isWithin(mouseX, mouseY, layout.contentX, buttonY, textWidth(SET_FILTERS_LABEL), 10)
                ? 0xFFE0CDB7
                : 0xFFD8C6B2;
        graphics.drawString(this.fontRenderer, SET_FILTERS_LABEL, layout.contentX, buttonY, buttonColor, false);
        if (isWithin(mouseX, mouseY, layout.contentX, buttonY, textWidth(SET_FILTERS_LABEL), 10)) {
            queueTooltip(new TextComponentTranslation("inmis.gui.set_filters.tooltip"));
        }
        int gridY = buttonY + TEXT_LINE_HEIGHT;
        renderFilterGrid(graphics, layout, gridY, settings.filters(), mouseX, mouseY);
    }

    private void renderQuiverlinkSettings(GraphicsAdapter graphics, SettingsLayout layout, int startY, int mouseX, int mouseY,
                                          BackpackAugmentsComponent.QuiverlinkSettings settings) {
        ITextComponent label = new TextComponentTranslation("augment.backpacked.quiverlink.priority");
        ITextComponent value = new TextComponentTranslation("augment.backpacked.quiverlink.priority." + settings.priority().getSerializedName());
        ITextComponent text = new TextComponentString(label.getFormattedText() + ": ").appendSibling(value);
        graphics.drawString(this.fontRenderer, text, layout.contentX, startY, 0xFFD8C6B2, false);
        if (isWithin(mouseX, mouseY, layout.contentX, startY, textWidth(text), 10)) {
            queueTooltip(new TextComponentTranslation("augment.backpacked.quiverlink.priority.tooltip"));
        }
    }

    private void renderLootboundSettings(GraphicsAdapter graphics, SettingsLayout layout, int startY, int mouseX, int mouseY,
                                         BackpackAugmentsComponent.LootboundSettings settings) {
        int y = startY;
        ITextComponent blocks = new TextComponentTranslation("augment.backpacked.lootbound.blocks");
        graphics.drawString(this.fontRenderer, blocks, layout.contentX, y, 0xFFD8C6B2, false);
        drawToggleIcon(graphics, layout.toggleX, y, settings.blocks());
        if (isWithin(mouseX, mouseY, layout.contentX, y, textWidth(blocks), 10)) {
            queueTooltip(new TextComponentTranslation("augment.backpacked.lootbound.blocks.tooltip"));
        }
        y += 12;
        ITextComponent mobs = new TextComponentTranslation("augment.backpacked.lootbound.mobs");
        graphics.drawString(this.fontRenderer, mobs, layout.contentX, y, 0xFFD8C6B2, false);
        drawToggleIcon(graphics, layout.toggleX, y, settings.mobs());
        if (isWithin(mouseX, mouseY, layout.contentX, y, textWidth(mobs), 10)) {
            queueTooltip(new TextComponentTranslation("augment.backpacked.lootbound.mobs.tooltip"));
        }
    }

    private void renderLightweaverSettings(GraphicsAdapter graphics, SettingsLayout layout, int startY, int mouseX, int mouseY,
                                           BackpackAugmentsComponent.LightweaverSettings settings) {
        ITextComponent label = new TextComponentTranslation("augment.backpacked.lightweaver.light_level");
        ITextComponent text = new TextComponentString(label.getFormattedText() + ": " + settings.minimumLight());
        graphics.drawString(this.fontRenderer, text, layout.contentX, startY, 0xFFD8C6B2, false);
        int minusX = layout.toggleX - 14;
        graphics.drawString(this.fontRenderer, "-", minusX, startY, 0xFFD8C6B2, false);
        graphics.drawString(this.fontRenderer, "+", layout.toggleX, startY, 0xFFD8C6B2, false);
        if (isWithin(mouseX, mouseY, layout.contentX, startY, textWidth(text), 10)) {
            queueTooltip(new TextComponentTranslation("augment.backpacked.lightweaver.light_level.tooltip"));
        }
        int y = startY + 12;
        ITextComponent sound = new TextComponentTranslation("augment.backpacked.lightweaver.place_sound");
        graphics.drawString(this.fontRenderer, sound, layout.contentX, y, 0xFFD8C6B2, false);
        drawToggleIcon(graphics, layout.toggleX, y, settings.placeSound());
        if (isWithin(mouseX, mouseY, layout.contentX, y, textWidth(sound), 10)) {
            queueTooltip(new TextComponentTranslation("augment.backpacked.lightweaver.place_sound.tooltip"));
        }
    }

    private void renderSeedflowSettings(GraphicsAdapter graphics, SettingsLayout layout, int startY, int mouseX, int mouseY,
                                        BackpackAugmentsComponent.SeedflowSettings settings) {
        int y = startY;
        ITextComponent randomize = new TextComponentTranslation("augment.backpacked.seedflow.randomize_seeds");
        graphics.drawString(this.fontRenderer, randomize, layout.contentX, y, 0xFFD8C6B2, false);
        drawToggleIcon(graphics, layout.toggleX, y, settings.randomizeSeeds());
        if (isWithin(mouseX, mouseY, layout.contentX, y, textWidth(randomize), 10)) {
            queueTooltip(new TextComponentTranslation("augment.backpacked.seedflow.randomize_seeds.tooltip"));
        }
        y += 12;
        ITextComponent useFilters = new TextComponentTranslation("augment.backpacked.seedflow.use_filters");
        graphics.drawString(this.fontRenderer, useFilters, layout.contentX, y, 0xFFD8C6B2, false);
        drawToggleIcon(graphics, layout.toggleX, y, settings.useFilters());
        if (isWithin(mouseX, mouseY, layout.contentX, y, textWidth(useFilters), 10)) {
            queueTooltip(new TextComponentTranslation("augment.backpacked.seedflow.use_filters.tooltip"));
        }
        y += 12;
        graphics.drawString(this.fontRenderer, FILTERS_LABEL, layout.contentX, y, 0xFFD8C6B2, false);
        renderFilterGrid(graphics, layout, y + 10, settings.filters(), mouseX, mouseY);
    }

    private void renderHopperBridgeSettings(GraphicsAdapter graphics, SettingsLayout layout, int startY, int mouseX, int mouseY,
                                            BackpackAugmentsComponent.HopperBridgeSettings settings) {
        int y = startY;
        ITextComponent insert = new TextComponentTranslation("augment.backpacked.hopper_bridge.insert");
        graphics.drawString(this.fontRenderer, insert, layout.contentX, y, 0xFFD8C6B2, false);
        drawToggleIcon(graphics, layout.toggleX, y, settings.insert());
        if (isWithin(mouseX, mouseY, layout.contentX, y, textWidth(insert), 10)) {
            queueTooltip(new TextComponentTranslation("augment.backpacked.hopper_bridge.insert.tooltip"));
        }
        y += 12;
        ITextComponent extract = new TextComponentTranslation("augment.backpacked.hopper_bridge.extract");
        graphics.drawString(this.fontRenderer, extract, layout.contentX, y, 0xFFD8C6B2, false);
        drawToggleIcon(graphics, layout.toggleX, y, settings.extract());
        if (isWithin(mouseX, mouseY, layout.contentX, y, textWidth(extract), 10)) {
            queueTooltip(new TextComponentTranslation("augment.backpacked.hopper_bridge.extract.tooltip"));
        }
        y += 12;
        ITextComponent modeLabel = new TextComponentTranslation("augment.backpacked.hopper_bridge.filter_mode");
        ITextComponent modeValue = new TextComponentTranslation("augment.backpacked.hopper_bridge.filter_mode." + settings.filterMode().getSerializedName());
        ITextComponent text = new TextComponentString(modeLabel.getFormattedText() + ": ").appendSibling(modeValue);
        graphics.drawString(this.fontRenderer, text, layout.contentX, y, 0xFFD8C6B2, false);
        if (isWithin(mouseX, mouseY, layout.contentX, y, textWidth(text), 10)) {
            queueTooltip(new TextComponentTranslation("augment.backpacked.hopper_bridge.filter_mode.tooltip"));
        }
        y += 12;
        graphics.drawString(this.fontRenderer, FILTERS_LABEL, layout.contentX, y, 0xFFD8C6B2, false);
        renderFilterGrid(graphics, layout, y + 10, settings.filters(), mouseX, mouseY);
    }

    private void renderFilterGrid(GraphicsAdapter graphics, SettingsLayout layout, int startY, List<ResourceLocation> filters,
                                  int mouseX, int mouseY) {
        int filtersPerPage = FILTER_COLUMNS * FILTER_ROWS;
        int pageCount = Math.max(1, (filters.size() + filtersPerPage - 1) / filtersPerPage);
        if (filterPage >= pageCount) {
            filterPage = pageCount - 1;
        }

        int startIndex = filterPage * filtersPerPage;
        for (int i = 0; i < filtersPerPage; i++) {
            int slotX = layout.contentX + (i % FILTER_COLUMNS) * FILTER_SLOT_SIZE;
            int slotY = startY + (i / FILTER_COLUMNS) * FILTER_SLOT_SIZE;
            graphics.fill(slotX, slotY, slotX + FILTER_SLOT_SIZE, slotY + FILTER_SLOT_SIZE, 0x55202020);
            if (startIndex + i < filters.size()) {
                ItemStack stack = stackFromFilter(filters.get(startIndex + i));
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, slotX + 1, slotY + 1);
                }
            }
        }

        if (pageCount > 1) {
            int arrowY = startY - 10;
            graphics.drawString(this.fontRenderer, "<", layout.toggleX - 12, arrowY, 0xFFD8C6B2, false);
            graphics.drawString(this.fontRenderer, ">", layout.toggleX, arrowY, 0xFFD8C6B2, false);
        }
    }

    private boolean handleAugmentSettingsClick(SettingsLayout layout, double mouseX, double mouseY, int button) {
        BackpackAugmentsComponent augments = getAugments();
        int y = layout.settingsStartY + 12;
        switch (selectedAugment) {
            case FUNNELLING: {
                ITextComponent modeLabel = new TextComponentTranslation("augment.backpacked.funnelling.mode",
                        new TextComponentTranslation("augment.backpacked.funnelling.mode." + augments.funnelling().mode().getSerializedName()));
                if (isWithin(mouseX, mouseY, layout.contentX, y, textWidth(modeLabel), 10)) {
                    BackpackAugmentsComponent.FunnellingSettings.Mode next =
                            augments.funnelling().mode() == BackpackAugmentsComponent.FunnellingSettings.Mode.ALLOW
                                    ? BackpackAugmentsComponent.FunnellingSettings.Mode.DISALLOW
                                    : BackpackAugmentsComponent.FunnellingSettings.Mode.ALLOW;
                    applyAugments(augments.withFunnelling(augments.funnelling().withMode(next)));
                    return true;
                }
                int setFiltersY = y + (TEXT_LINE_HEIGHT * 2);
                if (isWithin(mouseX, mouseY, layout.contentX, setFiltersY, textWidth(SET_FILTERS_LABEL), 10)) {
                    applyAugments(augments.withFunnelling(augments.funnelling().withFilters(buildFiltersFromBackpack())));
                    filterPage = 0;
                    return true;
                }
                int gridY = y + (TEXT_LINE_HEIGHT * 3);
                return handleFilterClick(layout, gridY, augments.funnelling().filters(), button, selectedAugment, mouseX, mouseY);
            }
            case QUIVERLINK: {
                ITextComponent label = new TextComponentTranslation("augment.backpacked.quiverlink.priority");
                ITextComponent value = new TextComponentTranslation("augment.backpacked.quiverlink.priority." + augments.quiverlink().priority().getSerializedName());
                ITextComponent text = new TextComponentString(label.getFormattedText() + ": ").appendSibling(value);
                if (isWithin(mouseX, mouseY, layout.contentX, y, textWidth(text), 10)) {
                    BackpackAugmentsComponent.QuiverlinkSettings.Priority next =
                            augments.quiverlink().priority() == BackpackAugmentsComponent.QuiverlinkSettings.Priority.BACKPACK
                                    ? BackpackAugmentsComponent.QuiverlinkSettings.Priority.INVENTORY
                                    : BackpackAugmentsComponent.QuiverlinkSettings.Priority.BACKPACK;
                    applyAugments(augments.withQuiverlink(augments.quiverlink().withPriority(next)));
                    return true;
                }
                break;
            }
            case LOOTBOUND: {
                ITextComponent blocks = new TextComponentTranslation("augment.backpacked.lootbound.blocks");
                if (isWithin(mouseX, mouseY, layout.toggleX, y, TOGGLE_SIZE, TOGGLE_SIZE)) {
                    applyAugments(augments.withLootbound(augments.lootbound().withBlocks(!augments.lootbound().blocks())));
                    return true;
                }
                if (isWithin(mouseX, mouseY, layout.contentX, y, textWidth(blocks), 10)) {
                    return true;
                }
                y += 12;
                if (isWithin(mouseX, mouseY, layout.toggleX, y, TOGGLE_SIZE, TOGGLE_SIZE)) {
                    applyAugments(augments.withLootbound(augments.lootbound().withMobs(!augments.lootbound().mobs())));
                    return true;
                }
                break;
            }
            case LIGHTWEAVER: {
                int minusX = layout.toggleX - 14;
                if (isWithin(mouseX, mouseY, minusX, y, 8, 10)) {
                    int next = Math.max(0, augments.lightweaver().minimumLight() - 1);
                    applyAugments(augments.withLightweaver(augments.lightweaver().withMinimumLight(next)));
                    return true;
                }
                if (isWithin(mouseX, mouseY, layout.toggleX, y, 8, 10)) {
                    int next = Math.min(15, augments.lightweaver().minimumLight() + 1);
                    applyAugments(augments.withLightweaver(augments.lightweaver().withMinimumLight(next)));
                    return true;
                }
                y += 12;
                if (isWithin(mouseX, mouseY, layout.toggleX, y, TOGGLE_SIZE, TOGGLE_SIZE)) {
                    applyAugments(augments.withLightweaver(augments.lightweaver().withPlaceSound(!augments.lightweaver().placeSound())));
                    return true;
                }
                break;
            }
            case SEEDFLOW: {
                if (isWithin(mouseX, mouseY, layout.toggleX, y, TOGGLE_SIZE, TOGGLE_SIZE)) {
                    applyAugments(augments.withSeedflow(augments.seedflow().withRandomizeSeeds(!augments.seedflow().randomizeSeeds())));
                    return true;
                }
                y += 12;
                if (isWithin(mouseX, mouseY, layout.toggleX, y, TOGGLE_SIZE, TOGGLE_SIZE)) {
                    applyAugments(augments.withSeedflow(augments.seedflow().withUseFilters(!augments.seedflow().useFilters())));
                    return true;
                }
                return handleFilterClick(layout, y + 22, augments.seedflow().filters(), button, selectedAugment, mouseX, mouseY);
            }
            case HOPPER_BRIDGE: {
                if (isWithin(mouseX, mouseY, layout.toggleX, y, TOGGLE_SIZE, TOGGLE_SIZE)) {
                    applyAugments(augments.withHopperBridge(augments.hopperBridge().withInsert(!augments.hopperBridge().insert())));
                    return true;
                }
                y += 12;
                if (isWithin(mouseX, mouseY, layout.toggleX, y, TOGGLE_SIZE, TOGGLE_SIZE)) {
                    applyAugments(augments.withHopperBridge(augments.hopperBridge().withExtract(!augments.hopperBridge().extract())));
                    return true;
                }
                y += 12;
                ITextComponent modeLabel = new TextComponentTranslation("augment.backpacked.hopper_bridge.filter_mode");
                ITextComponent modeValue = new TextComponentTranslation("augment.backpacked.hopper_bridge.filter_mode." + augments.hopperBridge().filterMode().getSerializedName());
                ITextComponent text = new TextComponentString(modeLabel.getFormattedText() + ": ").appendSibling(modeValue);
                if (isWithin(mouseX, mouseY, layout.contentX, y, textWidth(text), 10)) {
                    BackpackAugmentsComponent.HopperBridgeSettings.FilterMode next = cycleFilterMode(augments.hopperBridge().filterMode());
                    applyAugments(augments.withHopperBridge(augments.hopperBridge().withFilterMode(next)));
                    return true;
                }
                return handleFilterClick(layout, y + 22, augments.hopperBridge().filters(), button, selectedAugment, mouseX, mouseY);
            }
            default: {
                break;
            }
        }
        return false;
    }

    private boolean handleFilterClick(SettingsLayout layout, int startY, List<ResourceLocation> filters, int button,
                                      BackpackAugmentType type, double mouseX, double mouseY) {
        int filtersPerPage = FILTER_COLUMNS * FILTER_ROWS;
        int pageCount = Math.max(1, (filters.size() + filtersPerPage - 1) / filtersPerPage);
        if (filterPage >= pageCount) {
            filterPage = pageCount - 1;
        }

        if (pageCount > 1) {
            int arrowY = startY - 10;
            if (isWithin(mouseX, mouseY, layout.toggleX - 12, arrowY, 8, 8)) {
                filterPage = Math.max(0, filterPage - 1);
                return true;
            }
            if (isWithin(mouseX, mouseY, layout.toggleX, arrowY, 8, 8)) {
                filterPage = Math.min(pageCount - 1, filterPage + 1);
                return true;
            }
        }

        int startIndex = filterPage * filtersPerPage;
        for (int i = 0; i < filtersPerPage; i++) {
            int slotX = layout.contentX + (i % FILTER_COLUMNS) * FILTER_SLOT_SIZE;
            int slotY = startY + (i / FILTER_COLUMNS) * FILTER_SLOT_SIZE;
            if (!isWithin(mouseX, mouseY, slotX, slotY, FILTER_SLOT_SIZE, FILTER_SLOT_SIZE)) {
                continue;
            }
            int index = startIndex + i;
            if (index < filters.size()) {
                List<ResourceLocation> updated = new ArrayList<>(filters);
                updated.remove(index);
                applyFilterUpdate(type, updated);
                return true;
            }
            ItemStack source = getFilterSourceStack();
            if (source.isEmpty()) {
                return true;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(source.getItem());
            if (id == null) {
                return true;
            }
            if (filters.contains(id)) {
                return true;
            }
            List<ResourceLocation> updated = new ArrayList<>(filters);
            updated.add(id);
            applyFilterUpdate(type, updated);
            return true;
        }
        return false;
    }

    private void applyFilterUpdate(BackpackAugmentType type, List<ResourceLocation> filters) {
        BackpackAugmentsComponent augments = getAugments();
        switch (type) {
            case FUNNELLING: applyAugments(augments.withFunnelling(augments.funnelling().withFilters(filters))); break;
            case SEEDFLOW: applyAugments(augments.withSeedflow(augments.seedflow().withFilters(filters))); break;
            case HOPPER_BRIDGE: applyAugments(augments.withHopperBridge(augments.hopperBridge().withFilters(filters))); break;
            default: {
                break;
            }
        }
    }

    private ItemStack getFilterSourceStack() {
        ItemStack carried = this.mc.player.inventory.getItemStack();
        if (!carried.isEmpty()) {
            return carried;
        }
        Slot slot = this.getSlotUnderMouse();
        if (slot != null && slot.getHasStack()) {
            return slot.getStack();
        }
        return ItemStack.EMPTY;
    }

    private ItemStack stackFromFilter(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item != null ? new ItemStack(item) : ItemStack.EMPTY;
    }

    private List<ResourceLocation> buildFiltersFromBackpack() {
        List<ItemStack> contents = new ArrayList<>();
        // Opening metadata need not contain storage NBT; use the native synchronized slots.
        for (int index = 0; index < getMenu().inventorySlots.size() - 36; index++) {
            contents.add(getMenu().getSlot(index).getStack());
        }
        java.util.LinkedHashSet<ResourceLocation> ids = new java.util.LinkedHashSet<>();
        for (ItemStack stack : contents) {
            if (stack.isEmpty() || !stack.isStackable()) {
                continue;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null) {
                ids.add(id);
            }
        }
        return new java.util.ArrayList<>(ids);
    }

    private void toggleAugment(BackpackAugmentType type) {
        BackpackAugmentsComponent augments = getAugments();
        BackpackAugmentsComponent updated;
        switch (type) {
            case FUNNELLING: updated = augments.withFunnelling(augments.funnelling().withEnabled(!augments.funnelling().enabled())); break;
            case QUIVERLINK: updated = augments.withQuiverlink(augments.quiverlink().withEnabled(!augments.quiverlink().enabled())); break;
            case LOOTBOUND: updated = augments.withLootbound(augments.lootbound().withEnabled(!augments.lootbound().enabled())); break;
            case LIGHTWEAVER: updated = augments.withLightweaver(augments.lightweaver().withEnabled(!augments.lightweaver().enabled())); break;
            case SEEDFLOW: updated = augments.withSeedflow(augments.seedflow().withEnabled(!augments.seedflow().enabled())); break;
            case HOPPER_BRIDGE: updated = augments.withHopperBridge(augments.hopperBridge().withEnabled(!augments.hopperBridge().enabled())); break;
            case FARMHAND: updated = augments.withFarmhandEnabled(!augments.farmhandEnabled()); break;
            case IMBUED_HIDE: updated = augments.withImbuedHideEnabled(!augments.imbuedHideEnabled()); break;
            case IMMORTAL: updated = augments.withImmortalEnabled(!augments.immortalEnabled()); break;
            case REFORGE: updated = augments.withReforgeEnabled(!augments.reforgeEnabled()); break;
            default: throw new IllegalArgumentException("Unknown augment");
        }
        applyAugments(updated);
    }

    private boolean isAugmentEnabled(BackpackAugmentType type, BackpackAugmentsComponent augments) {
        switch (type) {
            case FUNNELLING: return augments.funnelling().enabled();
            case QUIVERLINK: return augments.quiverlink().enabled();
            case LOOTBOUND: return augments.lootbound().enabled();
            case LIGHTWEAVER: return augments.lightweaver().enabled();
            case SEEDFLOW: return augments.seedflow().enabled();
            case HOPPER_BRIDGE: return augments.hopperBridge().enabled();
            case FARMHAND: return augments.farmhandEnabled();
            case IMBUED_HIDE: return augments.imbuedHideEnabled();
            case IMMORTAL: return augments.immortalEnabled();
            case REFORGE: return augments.reforgeEnabled();
            default: throw new IllegalArgumentException("Unknown setting");
        }
    }

    private BackpackAugmentsComponent getAugments() {
        ItemStack stack = getMenu().getBackpackStack();
        return Inmis.getOrCreateAugments(stack, getMenu().getItem().getTier());
    }

    private void applyAugments(BackpackAugmentsComponent augments) {
        ItemStack stack = getMenu().getBackpackStack();
        Inmis.setBackpackAugments(stack, augments);
        ServerNetworking.sendUpdateBackpackAugments(menu.windowId, augments);
    }

    private BackpackAugmentsComponent.HopperBridgeSettings.FilterMode cycleFilterMode(
            BackpackAugmentsComponent.HopperBridgeSettings.FilterMode mode) {
        switch (mode) {
            case OFF: return BackpackAugmentsComponent.HopperBridgeSettings.FilterMode.BOTH;
            case BOTH: return BackpackAugmentsComponent.HopperBridgeSettings.FilterMode.INSERT;
            case INSERT: return BackpackAugmentsComponent.HopperBridgeSettings.FilterMode.EXTRACT;
            case EXTRACT: return BackpackAugmentsComponent.HopperBridgeSettings.FilterMode.OFF;
            default: throw new IllegalArgumentException("Unknown setting");
        }
    }

    private int getSettingsIconX() {
        return guiLeft + xSize - SETTINGS_BUTTON_SIZE - 6;
    }

    private int getSettingsIconY() {
        return guiTop + 6;
    }

    private boolean isMouseOverSettingsIcon(double mouseX, double mouseY) {
        return isWithin(mouseX, mouseY, getSettingsIconX(), getSettingsIconY(), SETTINGS_BUTTON_SIZE, SETTINGS_BUTTON_SIZE);
    }

    private boolean isWithin(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private boolean isWithinSettingsContent(SettingsLayout layout, double mouseX, double mouseY) {
        return isWithin(mouseX, mouseY, layout.x, layout.contentTop, layout.width, layout.visibleHeight);
    }

    private void drawToggleIcon(GraphicsAdapter graphics, int x, int y, boolean enabled) {
        ResourceLocation icon = enabled ? TOGGLE_ON_ICON : TOGGLE_OFF_ICON;
        graphics.blit(icon, x, y, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, 10, 10);
    }

    private List<String> buildAugmentTooltip(BackpackAugmentType type) {
        List<String> lines = new ArrayList<>();
        lines.add(type.label().getFormattedText());
        lines.addAll(this.fontRenderer.listFormattedStringToWidth(type.description().getFormattedText().replace("\\n", "\n"), 180));
        return lines;
    }

    private void queueTooltip(ITextComponent tooltip) {
        queuedTooltip = this.fontRenderer.listFormattedStringToWidth(tooltip.getFormattedText().replace("\\n", "\n"), 200);
    }

    private void queueTooltip(List<String> tooltip) {
        queuedTooltip = tooltip;
    }

    private SettingsLayout getSettingsLayout() {
        int listHeight = unlockedAugments.size() * ROW_HEIGHT;
        int settingsHeight = getSettingsSectionHeight(selectedAugment);
        int contentHeight = listHeight + (settingsHeight > 0 ? 6 + settingsHeight : 0);
        int maxSettingsHeight = getMaxSettingsSectionHeight();
        int maxContentHeight = listHeight + (maxSettingsHeight > 0 ? 6 + maxSettingsHeight : 0);
        int rawPanelHeight = PANEL_PADDING + 12 + maxContentHeight + PANEL_PADDING;
        int maxPanelHeight = Math.max(80, this.height - (PANEL_SAFE_MARGIN_Y * 2));
        int panelHeight = Math.min(rawPanelHeight, maxPanelHeight);

        int availableWidth = Math.max(0, guiLeft - PANEL_SAFE_MARGIN_X - 6);
        int panelWidth = Math.min(PANEL_MAX_WIDTH, availableWidth);
        if (availableWidth >= PANEL_MIN_WIDTH) {
            panelWidth = Math.max(panelWidth, PANEL_MIN_WIDTH);
        }

        int x = guiLeft - panelWidth - 6;
        if (x < 4) {
            x = 4;
        }
        int y = Math.max(PANEL_SAFE_MARGIN_Y, (this.height - panelHeight) / 2);
        if (y + panelHeight > this.height - PANEL_SAFE_MARGIN_Y) {
            y = Math.max(PANEL_SAFE_MARGIN_Y, this.height - panelHeight - PANEL_SAFE_MARGIN_Y);
        }

        int contentX = x + PANEL_PADDING;
        int contentTop = y + PANEL_PADDING + 12;
        int visibleHeight = panelHeight - PANEL_PADDING - 12 - PANEL_PADDING;
        int scrollMax = Math.max(0, contentHeight - visibleHeight);
        settingsScroll = MathHelper.clamp(settingsScroll, 0, scrollMax);
        int listStartY = contentTop - settingsScroll;
        int settingsStartY = settingsHeight > 0 ? listStartY + listHeight + 6 : -1;
        int toggleX = x + panelWidth - PANEL_PADDING - TOGGLE_SIZE;
        return new SettingsLayout(x, y, panelWidth, panelHeight, contentX, contentTop, visibleHeight, listStartY,
                settingsStartY, toggleX, settingsScroll, scrollMax);
    }

    private int getSettingsSectionHeight(BackpackAugmentType type) {
        if (type == null) {
            return 0;
        }
        switch (type) {
            case FUNNELLING: return SETTINGS_HEADER_HEIGHT + (TEXT_LINE_HEIGHT * 3) + FILTER_GRID_HEIGHT;
            case QUIVERLINK: return SETTINGS_HEADER_HEIGHT + TEXT_LINE_HEIGHT;
            case LOOTBOUND: return SETTINGS_HEADER_HEIGHT + (TEXT_LINE_HEIGHT * 2);
            case LIGHTWEAVER: return SETTINGS_HEADER_HEIGHT + (TEXT_LINE_HEIGHT * 2);
            case SEEDFLOW: return SETTINGS_HEADER_HEIGHT + (TEXT_LINE_HEIGHT * 2) + FILTER_LABEL_HEIGHT + FILTER_GRID_HEIGHT;
            case HOPPER_BRIDGE: return SETTINGS_HEADER_HEIGHT + (TEXT_LINE_HEIGHT * 3) + FILTER_LABEL_HEIGHT + FILTER_GRID_HEIGHT;
            default: return 0;
        }
    }

    private int getMaxSettingsSectionHeight() {
        int maxHeight = 0;
        for (BackpackAugmentType type : unlockedAugments) {
            maxHeight = Math.max(maxHeight, getSettingsSectionHeight(type));
        }
        return maxHeight;
    }

    private static final class SettingsLayout {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int contentX;
        private final int contentTop;
        private final int visibleHeight;
        private final int listStartY;
        private final int settingsStartY;
        private final int toggleX;
        private final int scrollOffset;
        private final int scrollMax;
        public SettingsLayout(int x, int y, int width, int height, int contentX, int contentTop, int visibleHeight, int listStartY, int settingsStartY, int toggleX, int scrollOffset, int scrollMax) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.contentX = contentX;
            this.contentTop = contentTop;
            this.visibleHeight = visibleHeight;
            this.listStartY = listStartY;
            this.settingsStartY = settingsStartY;
            this.toggleX = toggleX;
            this.scrollOffset = scrollOffset;
            this.scrollMax = scrollMax;
        }
        public int x() { return x; }
        public int y() { return y; }
        public int width() { return width; }
        public int height() { return height; }
        public int contentX() { return contentX; }
        public int contentTop() { return contentTop; }
        public int visibleHeight() { return visibleHeight; }
        public int listStartY() { return listStartY; }
        public int settingsStartY() { return settingsStartY; }
        public int toggleX() { return toggleX; }
        public int scrollOffset() { return scrollOffset; }
        public int scrollMax() { return scrollMax; }
    }

    public void renderBackgroundTexture(GraphicsAdapter graphics, Rectangle bounds, float delta, int color) {
        float alpha = ((color >> 24) & 0xFF) / 255f;
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;
        GlStateManager.color(red, green, blue, alpha);
        int x = bounds.x, y = bounds.y, width = bounds.width, height = bounds.height;
        int xTextureOffset = 0;
        int yTextureOffset = 66;

        graphics.blit(GUI_TEXTURE, x, y, 106 + xTextureOffset, 124 + yTextureOffset, 8, 8, 256, 256);
        graphics.blit(GUI_TEXTURE, x + width - 8, y, 248 + xTextureOffset, 124 + yTextureOffset, 8, 8, 256, 256);
        graphics.blit(GUI_TEXTURE, x, y + height - 8, 106 + xTextureOffset, 182 + yTextureOffset, 8, 8, 256, 256);
        graphics.blit(GUI_TEXTURE, x + width - 8, y + height - 8, 248 + xTextureOffset, 182 + yTextureOffset, 8, 8, 256, 256);

        drawTexturedQuad(graphics, GUI_TEXTURE, x + 8, x + width - 8, y, y + 8, getZOffset(),
                (114 + xTextureOffset) / 256f, (248 + xTextureOffset) / 256f,
                (124 + yTextureOffset) / 256f, (132 + yTextureOffset) / 256f);
        drawTexturedQuad(graphics, GUI_TEXTURE, x + 8, x + width - 8, y + height - 8, y + height, getZOffset(),
                (114 + xTextureOffset) / 256f, (248 + xTextureOffset) / 256f,
                (182 + yTextureOffset) / 256f, (190 + yTextureOffset) / 256f);
        drawTexturedQuad(graphics, GUI_TEXTURE, x, x + 8, y + 8, y + height - 8, getZOffset(),
                (106 + xTextureOffset) / 256f, (114 + xTextureOffset) / 256f,
                (132 + yTextureOffset) / 256f, (182 + yTextureOffset) / 256f);
        drawTexturedQuad(graphics, GUI_TEXTURE, x + width - 8, x + width, y + 8, y + height - 8, getZOffset(),
                (248 + xTextureOffset) / 256f, (256 + xTextureOffset) / 256f,
                (132 + yTextureOffset) / 256f, (182 + yTextureOffset) / 256f);

        drawTexturedQuad(graphics, GUI_TEXTURE, x + 8, x + width - 8, y + 8, y + height - 8, getZOffset(),
                (114 + xTextureOffset) / 256f, (248 + xTextureOffset) / 256f,
                (132 + yTextureOffset) / 256f, (182 + yTextureOffset) / 256f);
    }

    private int getZOffset() {
        return 0;
    }

    private static void drawTexturedQuad(GraphicsAdapter graphics, ResourceLocation texture, int x1, int x2, int y1, int y2, int z,
                                         float u1, float u2, float v1, float v2) {
        net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x1, y1, z).tex(u1, v1).endVertex();
        buffer.pos(x1, y2, z).tex(u1, v2).endVertex();
        buffer.pos(x2, y2, z).tex(u2, v2).endVertex();
        buffer.pos(x2, y1, z).tex(u2, v1).endVertex();
        Tessellator.getInstance().draw();
    }

    private void applyScissor(int x1, int y1, int x2, int y2) {
        int scale = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x1 * scale, mc.displayHeight - y2 * scale,
                Math.max(0, (x2 - x1) * scale), Math.max(0, (y2 - y1) * scale));
    }

    private final class GraphicsAdapter {
        private void blit(ResourceLocation texture, int x, int y, int u, int v, int width, int height, int texWidth, int texHeight) {
            GlStateManager.color(1, 1, 1, 1);
            mc.getTextureManager().bindTexture(texture);
            Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, width, height, texWidth, texHeight);
        }

        private void fill(int x1, int y1, int x2, int y2, int color) {
            Gui.drawRect(x1, y1, x2, y2, color);
        }

        private void drawString(FontRenderer font, ITextComponent text, int x, int y, int color, boolean shadow) {
            drawString(font, text.getFormattedText(), x, y, color, shadow);
        }

        private void drawString(FontRenderer font, String text, int x, int y, int color, boolean shadow) {
            font.drawString(text, x, y, color, shadow);
        }

        private void renderItem(ItemStack stack, int x, int y) {
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(stack, x, y);
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        }

        private void enableScissor(int x1, int y1, int x2, int y2) {
            BackpackHandledScreen.this.applyScissor(x1, y1, x2, y2);
        }

        private void disableScissor() {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }
}
