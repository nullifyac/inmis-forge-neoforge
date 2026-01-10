package draylar.inmis.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackAugmentType;
import draylar.inmis.augment.BackpackAugments;
import draylar.inmis.api.Dimension;
import draylar.inmis.api.Rectangle;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import draylar.inmis.network.ServerNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BackpackHandledScreen extends AbstractContainerScreen<BackpackScreenHandler> {

    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("inmis", "textures/gui/backpack_container.png");
    private static final ResourceLocation SLOT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("inmis", "textures/gui/backpack_slot.png");
    private static final ResourceLocation SETTINGS_ICON =
            ResourceLocation.fromNamespaceAndPath("inmis", "textures/gui/sprites/backpack/settings.png");
    private static final ResourceLocation TOGGLE_ON_ICON =
            ResourceLocation.fromNamespaceAndPath("inmis", "textures/gui/sprites/backpack/toggle_on.png");
    private static final ResourceLocation TOGGLE_OFF_ICON =
            ResourceLocation.fromNamespaceAndPath("inmis", "textures/gui/sprites/backpack/toggle_off.png");

    private static final Component UPGRADES_LABEL = Component.translatable("inmis.gui.backpack_upgrades");
    private static final Component UPGRADES_TOOLTIP = Component.translatable("inmis.gui.backpack_upgrades.tooltip");
    private static final Component FILTERS_LABEL = Component.translatable("inmis.gui.filters");
    private static final Component SET_FILTERS_LABEL = Component.translatable("inmis.gui.set_filters");

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

    private final int guiTitleColor = Integer.decode(Inmis.CONFIG.guiTitleColor);
    private boolean showSettings;
    private boolean suppressNextRelease;
    private BackpackAugmentType selectedAugment;
    private int filterPage;
    private int settingsScroll;
    private List<BackpackAugmentType> unlockedAugments = List.of();

    public BackpackHandledScreen(BackpackScreenHandler handler, Inventory player, Component title) {
        super(handler, player, handler.getBackpackStack().getHoverName());

        Dimension dimension = handler.getDimension();
        this.imageWidth = dimension.getWidth();
        this.imageHeight = dimension.getHeight();
        this.titleLabelY = 7;
        this.inventoryLabelX = handler.getPlayerInvSlotPosition(dimension, 0, 0).x;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
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
    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        RenderSystem.clearColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = this.leftPos;
        int y = this.topPos;
        renderBackgroundTexture(graphics, new Rectangle(x, y, imageWidth, imageHeight), delta, 0xFFFFFFFF);
        for (Slot slot : getMenu().slots) {
            graphics.blit(SLOT_TEXTURE, x + slot.x - 1, y + slot.y - 1, 0, 0, 18, 18, 18, 18);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
        renderSettingsIcon(graphics, mouseX, mouseY);
        if (showSettings) {
            renderSettingsPanel(graphics, mouseX, mouseY);
        }
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, title, titleLabelX, titleLabelY, guiTitleColor, false);
        graphics.drawString(this.font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, guiTitleColor, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOverSettingsIcon(mouseX, mouseY) && button == 0) {
            this.showSettings = !this.showSettings;
            this.suppressNextRelease = true;
            return true;
        }
        if (showSettings && handleSettingsClick(mouseX, mouseY, button)) {
            this.suppressNextRelease = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (suppressNextRelease) {
            suppressNextRelease = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!showSettings) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        SettingsLayout layout = getSettingsLayout();
        if (layout.scrollMax > 0 && isWithin(mouseX, mouseY, layout.x, layout.y, layout.width, layout.height)) {
            int step = ROW_HEIGHT;
            int next = this.settingsScroll - (int) Math.signum(scrollY) * step;
            this.settingsScroll = Mth.clamp(next, 0, layout.scrollMax);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void renderSettingsIcon(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = getSettingsIconX();
        int y = getSettingsIconY();
        graphics.blit(SETTINGS_ICON, x, y, 0, 0, SETTINGS_BUTTON_SIZE, SETTINGS_BUTTON_SIZE, SETTINGS_TEXTURE_SIZE, SETTINGS_TEXTURE_SIZE);
        if (isMouseOverSettingsIcon(mouseX, mouseY)) {
            graphics.fill(x, y, x + SETTINGS_BUTTON_SIZE, y + SETTINGS_BUTTON_SIZE, 0x66FFFFFF);
            this.setTooltipForNextRenderPass(UPGRADES_TOOLTIP);
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

    private void renderSettingsPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        if (unlockedAugments.isEmpty()) {
            return;
        }
        SettingsLayout layout = getSettingsLayout();
        graphics.fill(layout.x, layout.y, layout.x + layout.width, layout.y + layout.height, 0xCC2A241E);
        graphics.drawString(this.font, UPGRADES_LABEL, layout.contentX, layout.y + PANEL_PADDING, 0xFFE0CDB7, false);

        graphics.enableScissor(layout.x, layout.contentTop, layout.x + layout.width, layout.contentTop + layout.visibleHeight);
        BackpackAugmentsComponent augments = getAugments();
        int rowY = layout.listStartY;
        for (BackpackAugmentType type : unlockedAugments) {
            boolean selected = type == selectedAugment;
            if (selected) {
                graphics.fill(layout.x + 2, rowY, layout.x + layout.width - 2, rowY + ROW_HEIGHT, 0x553E352C);
            }
            graphics.blit(type.icon(), layout.contentX, rowY + 1, 0, 0, 16, 16, 16, 16);
            graphics.drawString(this.font, type.label(), layout.contentX + 20, rowY + 5, 0xFFE0CDB7, false);
            drawToggleIcon(graphics, layout.toggleX, rowY + (ROW_HEIGHT - TOGGLE_SIZE) / 2, isAugmentEnabled(type, augments));
            if (isWithin(mouseX, mouseY, layout.x + 2, rowY, layout.width - 4, ROW_HEIGHT)) {
                setTooltipForNextRenderPass(buildAugmentTooltip(type));
            }
            rowY += ROW_HEIGHT;
        }

        if (selectedAugment != null && layout.settingsStartY >= 0) {
            renderAugmentSettings(graphics, layout, mouseX, mouseY, augments);
        }
        graphics.disableScissor();
    }

    private void renderAugmentSettings(GuiGraphics graphics, SettingsLayout layout, int mouseX, int mouseY,
                                       BackpackAugmentsComponent augments) {
        int y = layout.settingsStartY;
        Component settingsTitle = Component.translatable("inmis.gui.upgrade_settings", selectedAugment.label());
        graphics.drawString(this.font, settingsTitle, layout.contentX, y, 0xFFE0CDB7, false);
        y += 12;

        switch (selectedAugment) {
            case FUNNELLING -> renderFunnellingSettings(graphics, layout, y, mouseX, mouseY, augments.funnelling());
            case QUIVERLINK -> renderQuiverlinkSettings(graphics, layout, y, mouseX, mouseY, augments.quiverlink());
            case LOOTBOUND -> renderLootboundSettings(graphics, layout, y, mouseX, mouseY, augments.lootbound());
            case LIGHTWEAVER -> renderLightweaverSettings(graphics, layout, y, mouseX, mouseY, augments.lightweaver());
            case SEEDFLOW -> renderSeedflowSettings(graphics, layout, y, mouseX, mouseY, augments.seedflow());
            case HOPPER_BRIDGE -> renderHopperBridgeSettings(graphics, layout, y, mouseX, mouseY, augments.hopperBridge());
            default -> {
            }
        }
    }

    private void renderFunnellingSettings(GuiGraphics graphics, SettingsLayout layout, int startY, int mouseX, int mouseY,
                                          BackpackAugmentsComponent.FunnellingSettings settings) {
        Component modeValue = Component.translatable("augment.backpacked.funnelling.mode." + settings.mode().getSerializedName());
        Component label = Component.translatable("augment.backpacked.funnelling.mode", modeValue);
        graphics.drawString(this.font, label, layout.contentX, startY, 0xFFD8C6B2, false);
        if (isWithin(mouseX, mouseY, layout.contentX, startY, this.font.width(label), 10)) {
            Component tooltip = Component.translatable("augment.backpacked.funnelling.mode." + settings.mode().getSerializedName() + ".tooltip");
            setTooltipForNextRenderPass(tooltip);
        }
        int y = startY + TEXT_LINE_HEIGHT;
        graphics.drawString(this.font, FILTERS_LABEL, layout.contentX, y, 0xFFD8C6B2, false);
        int buttonY = y + TEXT_LINE_HEIGHT;
        int buttonColor = isWithin(mouseX, mouseY, layout.contentX, buttonY, this.font.width(SET_FILTERS_LABEL), 10)
                ? 0xFFE0CDB7
                : 0xFFD8C6B2;
        graphics.drawString(this.font, SET_FILTERS_LABEL, layout.contentX, buttonY, buttonColor, false);
        if (isWithin(mouseX, mouseY, layout.contentX, buttonY, this.font.width(SET_FILTERS_LABEL), 10)) {
            setTooltipForNextRenderPass(Component.translatable("inmis.gui.set_filters.tooltip"));
        }
        int gridY = buttonY + TEXT_LINE_HEIGHT;
        renderFilterGrid(graphics, layout, gridY, settings.filters(), mouseX, mouseY);
    }

    private void renderQuiverlinkSettings(GuiGraphics graphics, SettingsLayout layout, int startY, int mouseX, int mouseY,
                                          BackpackAugmentsComponent.QuiverlinkSettings settings) {
        Component label = Component.translatable("augment.backpacked.quiverlink.priority");
        Component value = Component.translatable("augment.backpacked.quiverlink.priority." + settings.priority().getSerializedName());
        Component text = Component.literal(label.getString() + ": ").append(value);
        graphics.drawString(this.font, text, layout.contentX, startY, 0xFFD8C6B2, false);
        if (isWithin(mouseX, mouseY, layout.contentX, startY, this.font.width(text), 10)) {
            setTooltipForNextRenderPass(Component.translatable("augment.backpacked.quiverlink.priority.tooltip"));
        }
    }

    private void renderLootboundSettings(GuiGraphics graphics, SettingsLayout layout, int startY, int mouseX, int mouseY,
                                         BackpackAugmentsComponent.LootboundSettings settings) {
        int y = startY;
        Component blocks = Component.translatable("augment.backpacked.lootbound.blocks");
        graphics.drawString(this.font, blocks, layout.contentX, y, 0xFFD8C6B2, false);
        drawToggleIcon(graphics, layout.toggleX, y, settings.blocks());
        if (isWithin(mouseX, mouseY, layout.contentX, y, this.font.width(blocks), 10)) {
            setTooltipForNextRenderPass(Component.translatable("augment.backpacked.lootbound.blocks.tooltip"));
        }
        y += 12;
        Component mobs = Component.translatable("augment.backpacked.lootbound.mobs");
        graphics.drawString(this.font, mobs, layout.contentX, y, 0xFFD8C6B2, false);
        drawToggleIcon(graphics, layout.toggleX, y, settings.mobs());
        if (isWithin(mouseX, mouseY, layout.contentX, y, this.font.width(mobs), 10)) {
            setTooltipForNextRenderPass(Component.translatable("augment.backpacked.lootbound.mobs.tooltip"));
        }
    }

    private void renderLightweaverSettings(GuiGraphics graphics, SettingsLayout layout, int startY, int mouseX, int mouseY,
                                           BackpackAugmentsComponent.LightweaverSettings settings) {
        Component label = Component.translatable("augment.backpacked.lightweaver.light_level");
        Component text = Component.literal(label.getString() + ": " + settings.minimumLight());
        graphics.drawString(this.font, text, layout.contentX, startY, 0xFFD8C6B2, false);
        int minusX = layout.toggleX - 14;
        graphics.drawString(this.font, "-", minusX, startY, 0xFFD8C6B2, false);
        graphics.drawString(this.font, "+", layout.toggleX, startY, 0xFFD8C6B2, false);
        if (isWithin(mouseX, mouseY, layout.contentX, startY, this.font.width(text), 10)) {
            setTooltipForNextRenderPass(Component.translatable("augment.backpacked.lightweaver.light_level.tooltip"));
        }
        int y = startY + 12;
        Component sound = Component.translatable("augment.backpacked.lightweaver.place_sound");
        graphics.drawString(this.font, sound, layout.contentX, y, 0xFFD8C6B2, false);
        drawToggleIcon(graphics, layout.toggleX, y, settings.placeSound());
        if (isWithin(mouseX, mouseY, layout.contentX, y, this.font.width(sound), 10)) {
            setTooltipForNextRenderPass(Component.translatable("augment.backpacked.lightweaver.place_sound.tooltip"));
        }
    }

    private void renderSeedflowSettings(GuiGraphics graphics, SettingsLayout layout, int startY, int mouseX, int mouseY,
                                        BackpackAugmentsComponent.SeedflowSettings settings) {
        int y = startY;
        Component randomize = Component.translatable("augment.backpacked.seedflow.randomize_seeds");
        graphics.drawString(this.font, randomize, layout.contentX, y, 0xFFD8C6B2, false);
        drawToggleIcon(graphics, layout.toggleX, y, settings.randomizeSeeds());
        if (isWithin(mouseX, mouseY, layout.contentX, y, this.font.width(randomize), 10)) {
            setTooltipForNextRenderPass(Component.translatable("augment.backpacked.seedflow.randomize_seeds.tooltip"));
        }
        y += 12;
        Component useFilters = Component.translatable("augment.backpacked.seedflow.use_filters");
        graphics.drawString(this.font, useFilters, layout.contentX, y, 0xFFD8C6B2, false);
        drawToggleIcon(graphics, layout.toggleX, y, settings.useFilters());
        if (isWithin(mouseX, mouseY, layout.contentX, y, this.font.width(useFilters), 10)) {
            setTooltipForNextRenderPass(Component.translatable("augment.backpacked.seedflow.use_filters.tooltip"));
        }
        y += 12;
        graphics.drawString(this.font, FILTERS_LABEL, layout.contentX, y, 0xFFD8C6B2, false);
        renderFilterGrid(graphics, layout, y + 10, settings.filters(), mouseX, mouseY);
    }

    private void renderHopperBridgeSettings(GuiGraphics graphics, SettingsLayout layout, int startY, int mouseX, int mouseY,
                                            BackpackAugmentsComponent.HopperBridgeSettings settings) {
        int y = startY;
        Component insert = Component.translatable("augment.backpacked.hopper_bridge.insert");
        graphics.drawString(this.font, insert, layout.contentX, y, 0xFFD8C6B2, false);
        drawToggleIcon(graphics, layout.toggleX, y, settings.insert());
        if (isWithin(mouseX, mouseY, layout.contentX, y, this.font.width(insert), 10)) {
            setTooltipForNextRenderPass(Component.translatable("augment.backpacked.hopper_bridge.insert.tooltip"));
        }
        y += 12;
        Component extract = Component.translatable("augment.backpacked.hopper_bridge.extract");
        graphics.drawString(this.font, extract, layout.contentX, y, 0xFFD8C6B2, false);
        drawToggleIcon(graphics, layout.toggleX, y, settings.extract());
        if (isWithin(mouseX, mouseY, layout.contentX, y, this.font.width(extract), 10)) {
            setTooltipForNextRenderPass(Component.translatable("augment.backpacked.hopper_bridge.extract.tooltip"));
        }
        y += 12;
        Component modeLabel = Component.translatable("augment.backpacked.hopper_bridge.filter_mode");
        Component modeValue = Component.translatable("augment.backpacked.hopper_bridge.filter_mode." + settings.filterMode().getSerializedName());
        Component text = Component.literal(modeLabel.getString() + ": ").append(modeValue);
        graphics.drawString(this.font, text, layout.contentX, y, 0xFFD8C6B2, false);
        if (isWithin(mouseX, mouseY, layout.contentX, y, this.font.width(text), 10)) {
            setTooltipForNextRenderPass(Component.translatable("augment.backpacked.hopper_bridge.filter_mode.tooltip"));
        }
        y += 12;
        graphics.drawString(this.font, FILTERS_LABEL, layout.contentX, y, 0xFFD8C6B2, false);
        renderFilterGrid(graphics, layout, y + 10, settings.filters(), mouseX, mouseY);
    }

    private void renderFilterGrid(GuiGraphics graphics, SettingsLayout layout, int startY, List<ResourceLocation> filters,
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
            graphics.drawString(this.font, "<", layout.toggleX - 12, arrowY, 0xFFD8C6B2, false);
            graphics.drawString(this.font, ">", layout.toggleX, arrowY, 0xFFD8C6B2, false);
        }
    }

    private boolean handleAugmentSettingsClick(SettingsLayout layout, double mouseX, double mouseY, int button) {
        BackpackAugmentsComponent augments = getAugments();
        int y = layout.settingsStartY + 12;
        switch (selectedAugment) {
            case FUNNELLING -> {
                Component modeLabel = Component.translatable("augment.backpacked.funnelling.mode",
                        Component.translatable("augment.backpacked.funnelling.mode." + augments.funnelling().mode().getSerializedName()));
                if (isWithin(mouseX, mouseY, layout.contentX, y, this.font.width(modeLabel), 10)) {
                    BackpackAugmentsComponent.FunnellingSettings.Mode next =
                            augments.funnelling().mode() == BackpackAugmentsComponent.FunnellingSettings.Mode.ALLOW
                                    ? BackpackAugmentsComponent.FunnellingSettings.Mode.DISALLOW
                                    : BackpackAugmentsComponent.FunnellingSettings.Mode.ALLOW;
                    applyAugments(augments.withFunnelling(augments.funnelling().withMode(next)));
                    return true;
                }
                int setFiltersY = y + (TEXT_LINE_HEIGHT * 2);
                if (isWithin(mouseX, mouseY, layout.contentX, setFiltersY, this.font.width(SET_FILTERS_LABEL), 10)) {
                    applyAugments(augments.withFunnelling(augments.funnelling().withFilters(buildFiltersFromBackpack())));
                    filterPage = 0;
                    return true;
                }
                int gridY = y + (TEXT_LINE_HEIGHT * 3);
                return handleFilterClick(layout, gridY, augments.funnelling().filters(), button, selectedAugment, mouseX, mouseY);
            }
            case QUIVERLINK -> {
                Component label = Component.translatable("augment.backpacked.quiverlink.priority");
                Component value = Component.translatable("augment.backpacked.quiverlink.priority." + augments.quiverlink().priority().getSerializedName());
                Component text = Component.literal(label.getString() + ": ").append(value);
                if (isWithin(mouseX, mouseY, layout.contentX, y, this.font.width(text), 10)) {
                    BackpackAugmentsComponent.QuiverlinkSettings.Priority next =
                            augments.quiverlink().priority() == BackpackAugmentsComponent.QuiverlinkSettings.Priority.BACKPACK
                                    ? BackpackAugmentsComponent.QuiverlinkSettings.Priority.INVENTORY
                                    : BackpackAugmentsComponent.QuiverlinkSettings.Priority.BACKPACK;
                    applyAugments(augments.withQuiverlink(augments.quiverlink().withPriority(next)));
                    return true;
                }
            }
            case LOOTBOUND -> {
                Component blocks = Component.translatable("augment.backpacked.lootbound.blocks");
                if (isWithin(mouseX, mouseY, layout.toggleX, y, TOGGLE_SIZE, TOGGLE_SIZE)) {
                    applyAugments(augments.withLootbound(augments.lootbound().withBlocks(!augments.lootbound().blocks())));
                    return true;
                }
                if (isWithin(mouseX, mouseY, layout.contentX, y, this.font.width(blocks), 10)) {
                    return true;
                }
                y += 12;
                if (isWithin(mouseX, mouseY, layout.toggleX, y, TOGGLE_SIZE, TOGGLE_SIZE)) {
                    applyAugments(augments.withLootbound(augments.lootbound().withMobs(!augments.lootbound().mobs())));
                    return true;
                }
            }
            case LIGHTWEAVER -> {
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
            }
            case SEEDFLOW -> {
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
            case HOPPER_BRIDGE -> {
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
                Component modeLabel = Component.translatable("augment.backpacked.hopper_bridge.filter_mode");
                Component modeValue = Component.translatable("augment.backpacked.hopper_bridge.filter_mode." + augments.hopperBridge().filterMode().getSerializedName());
                Component text = Component.literal(modeLabel.getString() + ": ").append(modeValue);
                if (isWithin(mouseX, mouseY, layout.contentX, y, this.font.width(text), 10)) {
                    BackpackAugmentsComponent.HopperBridgeSettings.FilterMode next = cycleFilterMode(augments.hopperBridge().filterMode());
                    applyAugments(augments.withHopperBridge(augments.hopperBridge().withFilterMode(next)));
                    return true;
                }
                return handleFilterClick(layout, y + 22, augments.hopperBridge().filters(), button, selectedAugment, mouseX, mouseY);
            }
            default -> {
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
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(source.getItem());
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
            case FUNNELLING -> applyAugments(augments.withFunnelling(augments.funnelling().withFilters(filters)));
            case SEEDFLOW -> applyAugments(augments.withSeedflow(augments.seedflow().withFilters(filters)));
            case HOPPER_BRIDGE -> applyAugments(augments.withHopperBridge(augments.hopperBridge().withFilters(filters)));
            default -> {
            }
        }
    }

    private ItemStack getFilterSourceStack() {
        ItemStack carried = getMenu().getCarried();
        if (!carried.isEmpty()) {
            return carried;
        }
        Slot slot = this.getSlotUnderMouse();
        if (slot != null && slot.hasItem()) {
            return slot.getItem();
        }
        return ItemStack.EMPTY;
    }

    private ItemStack stackFromFilter(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        return item != null ? new ItemStack(item) : ItemStack.EMPTY;
    }

    private List<ResourceLocation> buildFiltersFromBackpack() {
        List<ItemStack> contents = Inmis.getBackpackContents(getMenu().getBackpackStack());
        java.util.LinkedHashSet<ResourceLocation> ids = new java.util.LinkedHashSet<>();
        for (ItemStack stack : contents) {
            if (stack.isEmpty() || !stack.isStackable()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null) {
                ids.add(id);
            }
        }
        return new java.util.ArrayList<>(ids);
    }

    private void toggleAugment(BackpackAugmentType type) {
        BackpackAugmentsComponent augments = getAugments();
        BackpackAugmentsComponent updated = switch (type) {
            case FUNNELLING -> augments.withFunnelling(augments.funnelling().withEnabled(!augments.funnelling().enabled()));
            case QUIVERLINK -> augments.withQuiverlink(augments.quiverlink().withEnabled(!augments.quiverlink().enabled()));
            case LOOTBOUND -> augments.withLootbound(augments.lootbound().withEnabled(!augments.lootbound().enabled()));
            case LIGHTWEAVER -> augments.withLightweaver(augments.lightweaver().withEnabled(!augments.lightweaver().enabled()));
            case SEEDFLOW -> augments.withSeedflow(augments.seedflow().withEnabled(!augments.seedflow().enabled()));
            case HOPPER_BRIDGE -> augments.withHopperBridge(augments.hopperBridge().withEnabled(!augments.hopperBridge().enabled()));
            case FARMHAND -> augments.withFarmhandEnabled(!augments.farmhandEnabled());
            case IMBUED_HIDE -> augments.withImbuedHideEnabled(!augments.imbuedHideEnabled());
            case IMMORTAL -> augments.withImmortalEnabled(!augments.immortalEnabled());
            case REFORGE -> augments.withReforgeEnabled(!augments.reforgeEnabled());
        };
        applyAugments(updated);
    }

    private boolean isAugmentEnabled(BackpackAugmentType type, BackpackAugmentsComponent augments) {
        return switch (type) {
            case FUNNELLING -> augments.funnelling().enabled();
            case QUIVERLINK -> augments.quiverlink().enabled();
            case LOOTBOUND -> augments.lootbound().enabled();
            case LIGHTWEAVER -> augments.lightweaver().enabled();
            case SEEDFLOW -> augments.seedflow().enabled();
            case HOPPER_BRIDGE -> augments.hopperBridge().enabled();
            case FARMHAND -> augments.farmhandEnabled();
            case IMBUED_HIDE -> augments.imbuedHideEnabled();
            case IMMORTAL -> augments.immortalEnabled();
            case REFORGE -> augments.reforgeEnabled();
        };
    }

    private BackpackAugmentsComponent getAugments() {
        ItemStack stack = getMenu().getBackpackStack();
        BackpackAugmentsComponent augments = stack.get(Inmis.BACKPACK_AUGMENTS.get());
        if (augments == null) {
            augments = BackpackAugmentsComponent.DEFAULT;
            stack.set(Inmis.BACKPACK_AUGMENTS.get(), augments);
        }
        return augments;
    }

    private void applyAugments(BackpackAugmentsComponent augments) {
        ItemStack stack = getMenu().getBackpackStack();
        stack.set(Inmis.BACKPACK_AUGMENTS.get(), augments);
        ServerNetworking.sendUpdateBackpackAugments(augments);
    }

    private BackpackAugmentsComponent.HopperBridgeSettings.FilterMode cycleFilterMode(
            BackpackAugmentsComponent.HopperBridgeSettings.FilterMode mode) {
        return switch (mode) {
            case OFF -> BackpackAugmentsComponent.HopperBridgeSettings.FilterMode.BOTH;
            case BOTH -> BackpackAugmentsComponent.HopperBridgeSettings.FilterMode.INSERT;
            case INSERT -> BackpackAugmentsComponent.HopperBridgeSettings.FilterMode.EXTRACT;
            case EXTRACT -> BackpackAugmentsComponent.HopperBridgeSettings.FilterMode.OFF;
        };
    }

    private int getSettingsIconX() {
        return leftPos + imageWidth - SETTINGS_BUTTON_SIZE - 6;
    }

    private int getSettingsIconY() {
        return topPos + 6;
    }

    private boolean isMouseOverSettingsIcon(double mouseX, double mouseY) {
        return isWithin(mouseX, mouseY, getSettingsIconX(), getSettingsIconY(), SETTINGS_BUTTON_SIZE, SETTINGS_BUTTON_SIZE);
    }

    private boolean isWithin(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void drawToggleIcon(GuiGraphics graphics, int x, int y, boolean enabled) {
        ResourceLocation icon = enabled ? TOGGLE_ON_ICON : TOGGLE_OFF_ICON;
        graphics.blit(icon, x, y, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, 10, 10);
    }

    private List<FormattedCharSequence> buildAugmentTooltip(BackpackAugmentType type) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(type.label().getVisualOrderText());
        lines.addAll(this.font.split(type.description(), 180));
        return lines;
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

        int availableWidth = Math.max(0, leftPos - PANEL_SAFE_MARGIN_X - 6);
        int panelWidth = Math.min(PANEL_MAX_WIDTH, availableWidth);
        if (availableWidth >= PANEL_MIN_WIDTH) {
            panelWidth = Math.max(panelWidth, PANEL_MIN_WIDTH);
        }

        int x = leftPos - panelWidth - 6;
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
        settingsScroll = Mth.clamp(settingsScroll, 0, scrollMax);
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
        return switch (type) {
            case FUNNELLING -> SETTINGS_HEADER_HEIGHT + (TEXT_LINE_HEIGHT * 3) + FILTER_GRID_HEIGHT;
            case QUIVERLINK -> SETTINGS_HEADER_HEIGHT + TEXT_LINE_HEIGHT;
            case LOOTBOUND -> SETTINGS_HEADER_HEIGHT + (TEXT_LINE_HEIGHT * 2);
            case LIGHTWEAVER -> SETTINGS_HEADER_HEIGHT + (TEXT_LINE_HEIGHT * 2);
            case SEEDFLOW -> SETTINGS_HEADER_HEIGHT + (TEXT_LINE_HEIGHT * 2) + FILTER_LABEL_HEIGHT + FILTER_GRID_HEIGHT;
            case HOPPER_BRIDGE -> SETTINGS_HEADER_HEIGHT + (TEXT_LINE_HEIGHT * 3) + FILTER_LABEL_HEIGHT + FILTER_GRID_HEIGHT;
            default -> 0;
        };
    }

    private int getMaxSettingsSectionHeight() {
        int maxHeight = 0;
        for (BackpackAugmentType type : unlockedAugments) {
            maxHeight = Math.max(maxHeight, getSettingsSectionHeight(type));
        }
        return maxHeight;
    }

    private record SettingsLayout(int x, int y, int width, int height, int contentX, int contentTop,
                                  int visibleHeight, int listStartY, int settingsStartY, int toggleX,
                                  int scrollOffset, int scrollMax) {
    }

    public void renderBackgroundTexture(GuiGraphics graphics, Rectangle bounds, float delta, int color) {
        float alpha = ((color >> 24) & 0xFF) / 255f;
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;
        RenderSystem.clearColor(red, green, blue, alpha);
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

    private static void drawTexturedQuad(GuiGraphics graphics, ResourceLocation texture, int x1, int x2, int y1, int y2, int z,
                                         float u1, float u2, float v1, float v2) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        var matrix4f = graphics.pose().last().pose();
        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferBuilder.addVertex(matrix4f, x1, y1, z).setUv(u1, v1);
        bufferBuilder.addVertex(matrix4f, x1, y2, z).setUv(u1, v2);
        bufferBuilder.addVertex(matrix4f, x2, y2, z).setUv(u2, v2);
        bufferBuilder.addVertex(matrix4f, x2, y1, z).setUv(u2, v1);
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
    }
}
