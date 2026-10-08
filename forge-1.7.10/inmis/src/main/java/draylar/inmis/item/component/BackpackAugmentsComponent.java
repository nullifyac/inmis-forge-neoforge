package draylar.inmis.item.component;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.common.IPlantable;
import draylar.inmis.network.LegacyBuffer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class BackpackAugmentsComponent {
    private final FunnellingSettings funnelling;
    private final QuiverlinkSettings quiverlink;
    private final LootboundSettings lootbound;
    private final LightweaverSettings lightweaver;
    private final SeedflowSettings seedflow;
    private final HopperBridgeSettings hopperBridge;
    private final boolean farmhandEnabled;
    private final boolean imbuedHideEnabled;
    private final boolean immortalEnabled;
    private final boolean reforgeEnabled;

    public BackpackAugmentsComponent(FunnellingSettings funnelling, QuiverlinkSettings quiverlink, LootboundSettings lootbound, LightweaverSettings lightweaver, SeedflowSettings seedflow, HopperBridgeSettings hopperBridge, boolean farmhandEnabled, boolean imbuedHideEnabled, boolean immortalEnabled, boolean reforgeEnabled) {
        funnelling = funnelling == null ? FunnellingSettings.DEFAULT : funnelling;
        quiverlink = quiverlink == null ? QuiverlinkSettings.DEFAULT : quiverlink;
        lootbound = lootbound == null ? LootboundSettings.DEFAULT : lootbound;
        lightweaver = lightweaver == null ? LightweaverSettings.DEFAULT : lightweaver;
        seedflow = seedflow == null ? SeedflowSettings.DEFAULT : seedflow;
        hopperBridge = hopperBridge == null ? HopperBridgeSettings.DEFAULT : hopperBridge;
        this.funnelling = funnelling;
        this.quiverlink = quiverlink;
        this.lootbound = lootbound;
        this.lightweaver = lightweaver;
        this.seedflow = seedflow;
        this.hopperBridge = hopperBridge;
        this.farmhandEnabled = farmhandEnabled;
        this.imbuedHideEnabled = imbuedHideEnabled;
        this.immortalEnabled = immortalEnabled;
        this.reforgeEnabled = reforgeEnabled;
    }

    public FunnellingSettings funnelling() {
        return funnelling;
    }

    public QuiverlinkSettings quiverlink() {
        return quiverlink;
    }

    public LootboundSettings lootbound() {
        return lootbound;
    }

    public LightweaverSettings lightweaver() {
        return lightweaver;
    }

    public SeedflowSettings seedflow() {
        return seedflow;
    }

    public HopperBridgeSettings hopperBridge() {
        return hopperBridge;
    }

    public boolean farmhandEnabled() {
        return farmhandEnabled;
    }

    public boolean imbuedHideEnabled() {
        return imbuedHideEnabled;
    }

    public boolean immortalEnabled() {
        return immortalEnabled;
    }

    public boolean reforgeEnabled() {
        return reforgeEnabled;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BackpackAugmentsComponent)) {
            return false;
        }
        BackpackAugmentsComponent that = (BackpackAugmentsComponent) other;
        return java.util.Objects.equals(funnelling, that.funnelling)
                && java.util.Objects.equals(quiverlink, that.quiverlink)
                && java.util.Objects.equals(lootbound, that.lootbound)
                && java.util.Objects.equals(lightweaver, that.lightweaver)
                && java.util.Objects.equals(seedflow, that.seedflow)
                && java.util.Objects.equals(hopperBridge, that.hopperBridge)
                && farmhandEnabled == that.farmhandEnabled
                && imbuedHideEnabled == that.imbuedHideEnabled
                && immortalEnabled == that.immortalEnabled
                && reforgeEnabled == that.reforgeEnabled;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(funnelling, quiverlink, lootbound, lightweaver, seedflow, hopperBridge, farmhandEnabled, imbuedHideEnabled, immortalEnabled, reforgeEnabled);
    }

    @Override
    public String toString() {
        return "BackpackAugmentsComponent[funnelling=" + funnelling + ", quiverlink=" + quiverlink + ", lootbound=" + lootbound + ", lightweaver=" + lightweaver + ", seedflow=" + seedflow + ", hopperBridge=" + hopperBridge + ", farmhandEnabled=" + farmhandEnabled + ", imbuedHideEnabled=" + imbuedHideEnabled + ", immortalEnabled=" + immortalEnabled + ", reforgeEnabled=" + reforgeEnabled + "]";
    }

    public static final int FUNNELLING_MAX_FILTERS = 32;
    public static final int SEEDFLOW_MAX_FILTERS = 32;
    public static final int HOPPER_BRIDGE_MAX_FILTERS = 64;

    private static final String KEY_FUNNELLING = "funnelling";
    private static final String KEY_QUIVERLINK = "quiverlink";
    private static final String KEY_LOOTBOUND = "lootbound";
    private static final String KEY_LIGHTWEAVER = "lightweaver";
    private static final String KEY_SEEDFLOW = "seedflow";
    private static final String KEY_HOPPER_BRIDGE = "hopper_bridge";
    private static final String KEY_FARMHAND_ENABLED = "farmhand_enabled";
    private static final String KEY_IMBUED_HIDE_ENABLED = "imbued_hide_enabled";
    private static final String KEY_IMMORTAL_ENABLED = "immortal_enabled";
    private static final String KEY_REFORGE_ENABLED = "reforge_enabled";

    public static final BackpackAugmentsComponent DEFAULT = new BackpackAugmentsComponent(
            FunnellingSettings.DEFAULT,
            QuiverlinkSettings.DEFAULT,
            LootboundSettings.DEFAULT,
            LightweaverSettings.DEFAULT,
            SeedflowSettings.DEFAULT,
            HopperBridgeSettings.DEFAULT,
            false,
            true,
            true,
            false
    );



    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag(KEY_FUNNELLING, funnelling.toTag());
        tag.setTag(KEY_QUIVERLINK, quiverlink.toTag());
        tag.setTag(KEY_LOOTBOUND, lootbound.toTag());
        tag.setTag(KEY_LIGHTWEAVER, lightweaver.toTag());
        tag.setTag(KEY_SEEDFLOW, seedflow.toTag());
        tag.setTag(KEY_HOPPER_BRIDGE, hopperBridge.toTag());
        tag.setBoolean(KEY_FARMHAND_ENABLED, farmhandEnabled);
        tag.setBoolean(KEY_IMBUED_HIDE_ENABLED, imbuedHideEnabled);
        tag.setBoolean(KEY_IMMORTAL_ENABLED, immortalEnabled);
        tag.setBoolean(KEY_REFORGE_ENABLED, reforgeEnabled);
        return tag;
    }

    public static BackpackAugmentsComponent fromTag(NBTTagCompound tag) {
        if (tag == null || tag.hasNoTags()) {
            return DEFAULT;
        }
        FunnellingSettings funnelling = tag.hasKey(KEY_FUNNELLING, 10)
                ? FunnellingSettings.fromTag(tag.getCompoundTag(KEY_FUNNELLING))
                : FunnellingSettings.DEFAULT;
        QuiverlinkSettings quiverlink = tag.hasKey(KEY_QUIVERLINK, 10)
                ? QuiverlinkSettings.fromTag(tag.getCompoundTag(KEY_QUIVERLINK))
                : QuiverlinkSettings.DEFAULT;
        LootboundSettings lootbound = tag.hasKey(KEY_LOOTBOUND, 10)
                ? LootboundSettings.fromTag(tag.getCompoundTag(KEY_LOOTBOUND))
                : LootboundSettings.DEFAULT;
        LightweaverSettings lightweaver = tag.hasKey(KEY_LIGHTWEAVER, 10)
                ? LightweaverSettings.fromTag(tag.getCompoundTag(KEY_LIGHTWEAVER))
                : LightweaverSettings.DEFAULT;
        SeedflowSettings seedflow = tag.hasKey(KEY_SEEDFLOW, 10)
                ? SeedflowSettings.fromTag(tag.getCompoundTag(KEY_SEEDFLOW))
                : SeedflowSettings.DEFAULT;
        HopperBridgeSettings hopperBridge = tag.hasKey(KEY_HOPPER_BRIDGE, 10)
                ? HopperBridgeSettings.fromTag(tag.getCompoundTag(KEY_HOPPER_BRIDGE))
                : HopperBridgeSettings.DEFAULT;
        boolean farmhandEnabled = tag.hasKey(KEY_FARMHAND_ENABLED, 1)
                ? tag.getBoolean(KEY_FARMHAND_ENABLED)
                : false;
        boolean imbuedHideEnabled = tag.hasKey(KEY_IMBUED_HIDE_ENABLED, 1)
                ? tag.getBoolean(KEY_IMBUED_HIDE_ENABLED)
                : true;
        boolean immortalEnabled = tag.hasKey(KEY_IMMORTAL_ENABLED, 1)
                ? tag.getBoolean(KEY_IMMORTAL_ENABLED)
                : true;
        boolean reforgeEnabled = tag.hasKey(KEY_REFORGE_ENABLED, 1)
                ? tag.getBoolean(KEY_REFORGE_ENABLED)
                : false;
        return new BackpackAugmentsComponent(
                funnelling,
                quiverlink,
                lootbound,
                lightweaver,
                seedflow,
                hopperBridge,
                farmhandEnabled,
                imbuedHideEnabled,
                immortalEnabled,
                reforgeEnabled
        );
    }

    public void write(LegacyBuffer buf) {
        funnelling.write(buf);
        quiverlink.write(buf);
        lootbound.write(buf);
        lightweaver.write(buf);
        seedflow.write(buf);
        hopperBridge.write(buf);
        buf.writeBoolean(farmhandEnabled);
        buf.writeBoolean(imbuedHideEnabled);
        buf.writeBoolean(immortalEnabled);
        buf.writeBoolean(reforgeEnabled);
    }

    public static BackpackAugmentsComponent read(LegacyBuffer buf) {
        return new BackpackAugmentsComponent(
                FunnellingSettings.read(buf),
                QuiverlinkSettings.read(buf),
                LootboundSettings.read(buf),
                LightweaverSettings.read(buf),
                SeedflowSettings.read(buf),
                HopperBridgeSettings.read(buf),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean()
        );
    }

    public BackpackAugmentsComponent withFunnelling(FunnellingSettings settings) {
        return new BackpackAugmentsComponent(settings, quiverlink, lootbound, lightweaver, seedflow, hopperBridge,
                farmhandEnabled, imbuedHideEnabled, immortalEnabled, reforgeEnabled);
    }

    public BackpackAugmentsComponent withQuiverlink(QuiverlinkSettings settings) {
        return new BackpackAugmentsComponent(funnelling, settings, lootbound, lightweaver, seedflow, hopperBridge,
                farmhandEnabled, imbuedHideEnabled, immortalEnabled, reforgeEnabled);
    }

    public BackpackAugmentsComponent withLootbound(LootboundSettings settings) {
        return new BackpackAugmentsComponent(funnelling, quiverlink, settings, lightweaver, seedflow, hopperBridge,
                farmhandEnabled, imbuedHideEnabled, immortalEnabled, reforgeEnabled);
    }

    public BackpackAugmentsComponent withLightweaver(LightweaverSettings settings) {
        return new BackpackAugmentsComponent(funnelling, quiverlink, lootbound, settings, seedflow, hopperBridge,
                farmhandEnabled, imbuedHideEnabled, immortalEnabled, reforgeEnabled);
    }

    public BackpackAugmentsComponent withSeedflow(SeedflowSettings settings) {
        return new BackpackAugmentsComponent(funnelling, quiverlink, lootbound, lightweaver, settings, hopperBridge,
                farmhandEnabled, imbuedHideEnabled, immortalEnabled, reforgeEnabled);
    }

    public BackpackAugmentsComponent withHopperBridge(HopperBridgeSettings settings) {
        return new BackpackAugmentsComponent(funnelling, quiverlink, lootbound, lightweaver, seedflow, settings,
                farmhandEnabled, imbuedHideEnabled, immortalEnabled, reforgeEnabled);
    }

    public BackpackAugmentsComponent withFarmhandEnabled(boolean enabled) {
        return new BackpackAugmentsComponent(funnelling, quiverlink, lootbound, lightweaver, seedflow, hopperBridge,
                enabled, imbuedHideEnabled, immortalEnabled, reforgeEnabled);
    }

    public BackpackAugmentsComponent withImbuedHideEnabled(boolean enabled) {
        return new BackpackAugmentsComponent(funnelling, quiverlink, lootbound, lightweaver, seedflow, hopperBridge,
                farmhandEnabled, enabled, immortalEnabled, reforgeEnabled);
    }

    public BackpackAugmentsComponent withImmortalEnabled(boolean enabled) {
        return new BackpackAugmentsComponent(funnelling, quiverlink, lootbound, lightweaver, seedflow, hopperBridge,
                farmhandEnabled, imbuedHideEnabled, enabled, reforgeEnabled);
    }

    public BackpackAugmentsComponent withReforgeEnabled(boolean enabled) {
        return new BackpackAugmentsComponent(funnelling, quiverlink, lootbound, lightweaver, seedflow, hopperBridge,
                farmhandEnabled, imbuedHideEnabled, immortalEnabled, enabled);
    }

    private static List<ResourceLocation> sanitizeFilters(List<ResourceLocation> filters, int max) {
        if (filters == null || filters.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        LinkedHashSet<ResourceLocation> unique = new LinkedHashSet<>();
        ResourceLocation air = new ResourceLocation("minecraft", "air");
        for (ResourceLocation id : filters) {
            if (id == null || unique.size() >= max) {
                break;
            }
            if (Item.itemRegistry.containsKey(id.toString()) && !id.equals(air)) {
                unique.add(id);
            }
        }
        return java.util.Collections.unmodifiableList(new ArrayList<>(unique));
    }

    private static List<ResourceLocation> sanitizeSeedflowFilters(List<ResourceLocation> filters) {
        List<ResourceLocation> ids = sanitizeFilters(filters, SEEDFLOW_MAX_FILTERS);
        if (ids.isEmpty()) {
            return ids;
        }
        List<ResourceLocation> valid = new ArrayList<>();
        for (ResourceLocation id : ids) {
            Item item = (Item) Item.itemRegistry.getObject(id.toString());
            if (item != null && isSeedflowPlantableItem(item)) {
                valid.add(id);
            }
        }
        return java.util.Collections.unmodifiableList(new ArrayList<>(valid));
    }

    private static boolean isSeedflowPlantableItem(Item item) {
        return item instanceof IPlantable;
    }

    private static List<ResourceLocation> readFilters(NBTTagCompound tag, String key, int max) {
        if (tag == null || !tag.hasKey(key, 9)) {
            return java.util.Collections.emptyList();
        }
        NBTTagList list = tag.getTagList(key, 8);
        List<ResourceLocation> filters = new ArrayList<>();
        for (int i = 0; i < list.tagCount() && filters.size() < max; i++) {
            String value = list.getStringTagAt(i);
            if (value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
                filters.add(new ResourceLocation(value));
            }
        }
        return sanitizeFilters(filters, max);
    }

    private static void writeFilters(NBTTagCompound tag, String key, List<ResourceLocation> filters) {
        NBTTagList list = new NBTTagList();
        for (ResourceLocation id : sanitizeFilters(filters, Integer.MAX_VALUE)) {
            list.appendTag(new NBTTagString(id.toString()));
        }
        tag.setTag(key, list);
    }

    public static final class FunnellingSettings {
        private final boolean enabled;
        private final Mode mode;
        private final List<ResourceLocation> filters;

        public FunnellingSettings(boolean enabled, Mode mode, List<ResourceLocation> filters) {
            mode = mode == null ? Mode.ALLOW : mode;
            filters = sanitizeFilters(filters, FUNNELLING_MAX_FILTERS);
            this.enabled = enabled;
            this.mode = mode;
            this.filters = filters;
        }

        public boolean enabled() {
            return enabled;
        }

        public Mode mode() {
            return mode;
        }

        public List<ResourceLocation> filters() {
            return filters;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FunnellingSettings)) {
                return false;
            }
            FunnellingSettings that = (FunnellingSettings) other;
            return enabled == that.enabled
                    && java.util.Objects.equals(mode, that.mode)
                    && java.util.Objects.equals(filters, that.filters);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(enabled, mode, filters);
        }

        @Override
        public String toString() {
            return "FunnellingSettings[enabled=" + enabled + ", mode=" + mode + ", filters=" + filters + "]";
        }

        public static final FunnellingSettings DEFAULT = new FunnellingSettings(false, Mode.ALLOW, java.util.Collections.emptyList());



        public NBTTagCompound toTag() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setBoolean("enabled", enabled);
            tag.setString("mode", mode.getSerializedName());
            writeFilters(tag, "filters", filters);
            return tag;
        }

        public static FunnellingSettings fromTag(NBTTagCompound tag) {
            if (tag == null || tag.hasNoTags()) {
                return DEFAULT;
            }
            boolean enabled = tag.hasKey("enabled", 1) ? tag.getBoolean("enabled") : false;
            Mode mode = Mode.fromTag(tag.getString("mode"));
            List<ResourceLocation> filters = readFilters(tag, "filters", FUNNELLING_MAX_FILTERS);
            return new FunnellingSettings(enabled, mode, filters);
        }

        public void write(LegacyBuffer buf) {
            buf.writeBoolean(enabled);
            buf.writeEnum(mode);
            buf.writeVarInt(filters.size());
            for (ResourceLocation id : filters) {
                buf.writeResourceLocation(id);
            }
        }

        public static FunnellingSettings read(LegacyBuffer buf) {
            boolean enabled = buf.readBoolean();
            Mode mode = buf.readEnum(Mode.class);
            int count = buf.readVarInt();
            if (count < 0 || count > FUNNELLING_MAX_FILTERS) throw new IllegalArgumentException("Invalid funnelling filter count");
            List<ResourceLocation> filters = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                filters.add(buf.readResourceLocation());
            }
            return new FunnellingSettings(enabled, mode, filters);
        }

        public FunnellingSettings withEnabled(boolean enabled) {
            return new FunnellingSettings(enabled, mode, filters);
        }

        public FunnellingSettings withMode(Mode mode) {
            return new FunnellingSettings(enabled, mode, filters);
        }

        public FunnellingSettings withFilters(List<ResourceLocation> filters) {
            return new FunnellingSettings(enabled, mode, filters);
        }

        public enum Mode {
            ALLOW,
            DISALLOW;

            public String getSerializedName() {
                return name().toLowerCase(Locale.ROOT);
            }

            public static Mode fromTag(String name) {
                if (name == null || name.isEmpty()) {
                    return ALLOW;
                }
                try {
                    return Mode.valueOf(name.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    return ALLOW;
                }
            }
        }
    }

    public static final class QuiverlinkSettings {
        private final boolean enabled;
        private final Priority priority;

        public QuiverlinkSettings(boolean enabled, Priority priority) {
            priority = priority == null ? Priority.BACKPACK : priority;
            this.enabled = enabled;
            this.priority = priority;
        }

        public boolean enabled() {
            return enabled;
        }

        public Priority priority() {
            return priority;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof QuiverlinkSettings)) {
                return false;
            }
            QuiverlinkSettings that = (QuiverlinkSettings) other;
            return enabled == that.enabled
                    && java.util.Objects.equals(priority, that.priority);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(enabled, priority);
        }

        @Override
        public String toString() {
            return "QuiverlinkSettings[enabled=" + enabled + ", priority=" + priority + "]";
        }

        public static final QuiverlinkSettings DEFAULT = new QuiverlinkSettings(true, Priority.BACKPACK);



        public NBTTagCompound toTag() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setBoolean("enabled", enabled);
            tag.setString("priority", priority.getSerializedName());
            return tag;
        }

        public static QuiverlinkSettings fromTag(NBTTagCompound tag) {
            if (tag == null || tag.hasNoTags()) {
                return DEFAULT;
            }
            boolean enabled = tag.hasKey("enabled", 1) ? tag.getBoolean("enabled") : true;
            Priority priority = Priority.fromTag(tag.getString("priority"));
            return new QuiverlinkSettings(enabled, priority);
        }

        public void write(LegacyBuffer buf) {
            buf.writeBoolean(enabled);
            buf.writeEnum(priority);
        }

        public static QuiverlinkSettings read(LegacyBuffer buf) {
            return new QuiverlinkSettings(buf.readBoolean(), buf.readEnum(Priority.class));
        }

        public QuiverlinkSettings withEnabled(boolean enabled) {
            return new QuiverlinkSettings(enabled, priority);
        }

        public QuiverlinkSettings withPriority(Priority priority) {
            return new QuiverlinkSettings(enabled, priority);
        }

        public enum Priority {
            BACKPACK,
            INVENTORY;

            public String getSerializedName() {
                return name().toLowerCase(Locale.ROOT);
            }

            public static Priority fromTag(String name) {
                if (name == null || name.isEmpty()) {
                    return BACKPACK;
                }
                try {
                    return Priority.valueOf(name.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    return BACKPACK;
                }
            }
        }
    }

    public static final class LootboundSettings {
        private final boolean enabled;
        private final boolean blocks;
        private final boolean mobs;

        public LootboundSettings(boolean enabled, boolean blocks, boolean mobs) {
            this.enabled = enabled;
            this.blocks = blocks;
            this.mobs = mobs;
        }

        public boolean enabled() {
            return enabled;
        }

        public boolean blocks() {
            return blocks;
        }

        public boolean mobs() {
            return mobs;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof LootboundSettings)) {
                return false;
            }
            LootboundSettings that = (LootboundSettings) other;
            return enabled == that.enabled
                    && blocks == that.blocks
                    && mobs == that.mobs;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(enabled, blocks, mobs);
        }

        @Override
        public String toString() {
            return "LootboundSettings[enabled=" + enabled + ", blocks=" + blocks + ", mobs=" + mobs + "]";
        }

        public static final LootboundSettings DEFAULT = new LootboundSettings(false, true, true);

        public NBTTagCompound toTag() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setBoolean("enabled", enabled);
            tag.setBoolean("blocks", blocks);
            tag.setBoolean("mobs", mobs);
            return tag;
        }

        public static LootboundSettings fromTag(NBTTagCompound tag) {
            if (tag == null || tag.hasNoTags()) {
                return DEFAULT;
            }
            boolean enabled = tag.hasKey("enabled", 1) ? tag.getBoolean("enabled") : false;
            boolean blocks = tag.hasKey("blocks", 1) ? tag.getBoolean("blocks") : true;
            boolean mobs = tag.hasKey("mobs", 1) ? tag.getBoolean("mobs") : true;
            return new LootboundSettings(enabled, blocks, mobs);
        }

        public void write(LegacyBuffer buf) {
            buf.writeBoolean(enabled);
            buf.writeBoolean(blocks);
            buf.writeBoolean(mobs);
        }

        public static LootboundSettings read(LegacyBuffer buf) {
            return new LootboundSettings(buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        }

        public LootboundSettings withEnabled(boolean enabled) {
            return new LootboundSettings(enabled, blocks, mobs);
        }

        public LootboundSettings withBlocks(boolean blocks) {
            return new LootboundSettings(enabled, blocks, mobs);
        }

        public LootboundSettings withMobs(boolean mobs) {
            return new LootboundSettings(enabled, blocks, mobs);
        }
    }

    public static final class LightweaverSettings {
        private final boolean enabled;
        private final int minimumLight;
        private final boolean placeSound;

        public LightweaverSettings(boolean enabled, int minimumLight, boolean placeSound) {
            this.enabled = enabled;
            this.minimumLight = minimumLight;
            this.placeSound = placeSound;
        }

        public boolean enabled() {
            return enabled;
        }

        public int minimumLight() {
            return minimumLight;
        }

        public boolean placeSound() {
            return placeSound;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof LightweaverSettings)) {
                return false;
            }
            LightweaverSettings that = (LightweaverSettings) other;
            return enabled == that.enabled
                    && minimumLight == that.minimumLight
                    && placeSound == that.placeSound;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(enabled, minimumLight, placeSound);
        }

        @Override
        public String toString() {
            return "LightweaverSettings[enabled=" + enabled + ", minimumLight=" + minimumLight + ", placeSound=" + placeSound + "]";
        }

        public static final LightweaverSettings DEFAULT = new LightweaverSettings(false, 7, true);

        public NBTTagCompound toTag() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setBoolean("enabled", enabled);
            tag.setInteger("minimum_light", minimumLight);
            tag.setBoolean("place_sound", placeSound);
            return tag;
        }

        public static LightweaverSettings fromTag(NBTTagCompound tag) {
            if (tag == null || tag.hasNoTags()) {
                return DEFAULT;
            }
            boolean enabled = tag.hasKey("enabled", 1) ? tag.getBoolean("enabled") : false;
            int minimumLight = tag.hasKey("minimum_light", 3) ? tag.getInteger("minimum_light") : 7;
            boolean placeSound = tag.hasKey("place_sound", 1) ? tag.getBoolean("place_sound") : true;
            return new LightweaverSettings(enabled, minimumLight, placeSound);
        }

        public void write(LegacyBuffer buf) {
            buf.writeBoolean(enabled);
            buf.writeVarInt(minimumLight);
            buf.writeBoolean(placeSound);
        }

        public static LightweaverSettings read(LegacyBuffer buf) {
            return new LightweaverSettings(buf.readBoolean(), buf.readVarInt(), buf.readBoolean());
        }

        public LightweaverSettings withEnabled(boolean enabled) {
            return new LightweaverSettings(enabled, minimumLight, placeSound);
        }

        public LightweaverSettings withMinimumLight(int minimumLight) {
            return new LightweaverSettings(enabled, minimumLight, placeSound);
        }

        public LightweaverSettings withPlaceSound(boolean placeSound) {
            return new LightweaverSettings(enabled, minimumLight, placeSound);
        }
    }

    public static final class SeedflowSettings {
        private final boolean enabled;
        private final boolean randomizeSeeds;
        private final boolean useFilters;
        private final List<ResourceLocation> filters;

        public SeedflowSettings(boolean enabled, boolean randomizeSeeds, boolean useFilters, List<ResourceLocation> filters) {
            filters = sanitizeSeedflowFilters(filters);
            this.enabled = enabled;
            this.randomizeSeeds = randomizeSeeds;
            this.useFilters = useFilters;
            this.filters = filters;
        }

        public boolean enabled() {
            return enabled;
        }

        public boolean randomizeSeeds() {
            return randomizeSeeds;
        }

        public boolean useFilters() {
            return useFilters;
        }

        public List<ResourceLocation> filters() {
            return filters;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SeedflowSettings)) {
                return false;
            }
            SeedflowSettings that = (SeedflowSettings) other;
            return enabled == that.enabled
                    && randomizeSeeds == that.randomizeSeeds
                    && useFilters == that.useFilters
                    && java.util.Objects.equals(filters, that.filters);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(enabled, randomizeSeeds, useFilters, filters);
        }

        @Override
        public String toString() {
            return "SeedflowSettings[enabled=" + enabled + ", randomizeSeeds=" + randomizeSeeds + ", useFilters=" + useFilters + ", filters=" + filters + "]";
        }

        public static final SeedflowSettings DEFAULT = new SeedflowSettings(false, false, false, java.util.Collections.emptyList());



        public NBTTagCompound toTag() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setBoolean("enabled", enabled);
            tag.setBoolean("randomize_seeds", randomizeSeeds);
            tag.setBoolean("use_filters", useFilters);
            writeFilters(tag, "filters", filters);
            return tag;
        }

        public static SeedflowSettings fromTag(NBTTagCompound tag) {
            if (tag == null || tag.hasNoTags()) {
                return DEFAULT;
            }
            boolean enabled = tag.hasKey("enabled", 1) ? tag.getBoolean("enabled") : false;
            boolean randomizeSeeds = tag.hasKey("randomize_seeds", 1) ? tag.getBoolean("randomize_seeds") : false;
            boolean useFilters = tag.hasKey("use_filters", 1) ? tag.getBoolean("use_filters") : false;
            List<ResourceLocation> filters = readFilters(tag, "filters", SEEDFLOW_MAX_FILTERS);
            return new SeedflowSettings(enabled, randomizeSeeds, useFilters, filters);
        }

        public void write(LegacyBuffer buf) {
            buf.writeBoolean(enabled);
            buf.writeBoolean(randomizeSeeds);
            buf.writeBoolean(useFilters);
            buf.writeVarInt(filters.size());
            for (ResourceLocation id : filters) {
                buf.writeResourceLocation(id);
            }
        }

        public static SeedflowSettings read(LegacyBuffer buf) {
            boolean enabled = buf.readBoolean();
            boolean randomizeSeeds = buf.readBoolean();
            boolean useFilters = buf.readBoolean();
            int count = buf.readVarInt();
            if (count < 0 || count > SEEDFLOW_MAX_FILTERS) throw new IllegalArgumentException("Invalid seedflow filter count");
            List<ResourceLocation> filters = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                filters.add(buf.readResourceLocation());
            }
            return new SeedflowSettings(enabled, randomizeSeeds, useFilters, filters);
        }

        public SeedflowSettings withEnabled(boolean enabled) {
            return new SeedflowSettings(enabled, randomizeSeeds, useFilters, filters);
        }

        public SeedflowSettings withRandomizeSeeds(boolean randomizeSeeds) {
            return new SeedflowSettings(enabled, randomizeSeeds, useFilters, filters);
        }

        public SeedflowSettings withUseFilters(boolean useFilters) {
            return new SeedflowSettings(enabled, randomizeSeeds, useFilters, filters);
        }

        public SeedflowSettings withFilters(List<ResourceLocation> filters) {
            return new SeedflowSettings(enabled, randomizeSeeds, useFilters, filters);
        }
    }

    public static final class HopperBridgeSettings {
        private final boolean enabled;
        private final boolean insert;
        private final boolean extract;
        private final FilterMode filterMode;
        private final List<ResourceLocation> filters;

        public HopperBridgeSettings(boolean enabled, boolean insert, boolean extract, FilterMode filterMode, List<ResourceLocation> filters) {
            filterMode = filterMode == null ? FilterMode.OFF : filterMode;
            filters = sanitizeFilters(filters, HOPPER_BRIDGE_MAX_FILTERS);
            this.enabled = enabled;
            this.insert = insert;
            this.extract = extract;
            this.filterMode = filterMode;
            this.filters = filters;
        }

        public boolean enabled() {
            return enabled;
        }

        public boolean insert() {
            return insert;
        }

        public boolean extract() {
            return extract;
        }

        public FilterMode filterMode() {
            return filterMode;
        }

        public List<ResourceLocation> filters() {
            return filters;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof HopperBridgeSettings)) {
                return false;
            }
            HopperBridgeSettings that = (HopperBridgeSettings) other;
            return enabled == that.enabled
                    && insert == that.insert
                    && extract == that.extract
                    && java.util.Objects.equals(filterMode, that.filterMode)
                    && java.util.Objects.equals(filters, that.filters);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(enabled, insert, extract, filterMode, filters);
        }

        @Override
        public String toString() {
            return "HopperBridgeSettings[enabled=" + enabled + ", insert=" + insert + ", extract=" + extract + ", filterMode=" + filterMode + ", filters=" + filters + "]";
        }

        public static final HopperBridgeSettings DEFAULT = new HopperBridgeSettings(true, true, true, FilterMode.OFF, java.util.Collections.emptyList());



        public NBTTagCompound toTag() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setBoolean("enabled", enabled);
            tag.setBoolean("insert", insert);
            tag.setBoolean("extract", extract);
            tag.setString("filter_mode", filterMode.getSerializedName());
            writeFilters(tag, "filters", filters);
            return tag;
        }

        public static HopperBridgeSettings fromTag(NBTTagCompound tag) {
            if (tag == null || tag.hasNoTags()) {
                return DEFAULT;
            }
            boolean enabled = tag.hasKey("enabled", 1) ? tag.getBoolean("enabled") : true;
            boolean insert = tag.hasKey("insert", 1) ? tag.getBoolean("insert") : true;
            boolean extract = tag.hasKey("extract", 1) ? tag.getBoolean("extract") : true;
            FilterMode mode = FilterMode.fromTag(tag.getString("filter_mode"));
            List<ResourceLocation> filters = readFilters(tag, "filters", HOPPER_BRIDGE_MAX_FILTERS);
            return new HopperBridgeSettings(enabled, insert, extract, mode, filters);
        }

        public void write(LegacyBuffer buf) {
            buf.writeBoolean(enabled);
            buf.writeBoolean(insert);
            buf.writeBoolean(extract);
            buf.writeEnum(filterMode);
            buf.writeVarInt(filters.size());
            for (ResourceLocation id : filters) {
                buf.writeResourceLocation(id);
            }
        }

        public static HopperBridgeSettings read(LegacyBuffer buf) {
            boolean enabled = buf.readBoolean();
            boolean insert = buf.readBoolean();
            boolean extract = buf.readBoolean();
            FilterMode mode = buf.readEnum(FilterMode.class);
            int count = buf.readVarInt();
            if (count < 0 || count > HOPPER_BRIDGE_MAX_FILTERS) throw new IllegalArgumentException("Invalid hopper filter count");
            List<ResourceLocation> filters = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                filters.add(buf.readResourceLocation());
            }
            return new HopperBridgeSettings(enabled, insert, extract, mode, filters);
        }

        public HopperBridgeSettings withEnabled(boolean enabled) {
            return new HopperBridgeSettings(enabled, insert, extract, filterMode, filters);
        }

        public HopperBridgeSettings withInsert(boolean insert) {
            return new HopperBridgeSettings(enabled, insert, extract, filterMode, filters);
        }

        public HopperBridgeSettings withExtract(boolean extract) {
            return new HopperBridgeSettings(enabled, insert, extract, filterMode, filters);
        }

        public HopperBridgeSettings withFilterMode(FilterMode filterMode) {
            return new HopperBridgeSettings(enabled, insert, extract, filterMode, filters);
        }

        public HopperBridgeSettings withFilters(List<ResourceLocation> filters) {
            return new HopperBridgeSettings(enabled, insert, extract, filterMode, filters);
        }

        public enum FilterMode {
            OFF(false, false),
            BOTH(true, true),
            INSERT(true, false),
            EXTRACT(false, true);

            private final boolean insert;
            private final boolean extract;

            FilterMode(boolean insert, boolean extract) {
                this.insert = insert;
                this.extract = extract;
            }

            public String getSerializedName() {
                return name().toLowerCase(Locale.ROOT);
            }

            public boolean checkInsert() {
                return insert;
            }

            public boolean checkExtract() {
                return extract;
            }

            public static FilterMode fromTag(String name) {
                if (name == null || name.isEmpty()) {
                    return OFF;
                }
                try {
                    return FilterMode.valueOf(name.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    return OFF;
                }
            }
        }
    }
}
