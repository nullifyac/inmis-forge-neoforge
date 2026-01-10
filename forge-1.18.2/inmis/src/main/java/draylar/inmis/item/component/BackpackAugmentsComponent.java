package draylar.inmis.item.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public record BackpackAugmentsComponent(
        FunnellingSettings funnelling,
        QuiverlinkSettings quiverlink,
        LootboundSettings lootbound,
        LightweaverSettings lightweaver,
        SeedflowSettings seedflow,
        HopperBridgeSettings hopperBridge,
        boolean farmhandEnabled,
        boolean imbuedHideEnabled,
        boolean immortalEnabled,
        boolean reforgeEnabled
) {

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
            true,
            true,
            true,
            true
    );

    public BackpackAugmentsComponent {
        funnelling = funnelling == null ? FunnellingSettings.DEFAULT : funnelling;
        quiverlink = quiverlink == null ? QuiverlinkSettings.DEFAULT : quiverlink;
        lootbound = lootbound == null ? LootboundSettings.DEFAULT : lootbound;
        lightweaver = lightweaver == null ? LightweaverSettings.DEFAULT : lightweaver;
        seedflow = seedflow == null ? SeedflowSettings.DEFAULT : seedflow;
        hopperBridge = hopperBridge == null ? HopperBridgeSettings.DEFAULT : hopperBridge;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put(KEY_FUNNELLING, funnelling.toTag());
        tag.put(KEY_QUIVERLINK, quiverlink.toTag());
        tag.put(KEY_LOOTBOUND, lootbound.toTag());
        tag.put(KEY_LIGHTWEAVER, lightweaver.toTag());
        tag.put(KEY_SEEDFLOW, seedflow.toTag());
        tag.put(KEY_HOPPER_BRIDGE, hopperBridge.toTag());
        tag.putBoolean(KEY_FARMHAND_ENABLED, farmhandEnabled);
        tag.putBoolean(KEY_IMBUED_HIDE_ENABLED, imbuedHideEnabled);
        tag.putBoolean(KEY_IMMORTAL_ENABLED, immortalEnabled);
        tag.putBoolean(KEY_REFORGE_ENABLED, reforgeEnabled);
        return tag;
    }

    public static BackpackAugmentsComponent fromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return DEFAULT;
        }
        FunnellingSettings funnelling = tag.contains(KEY_FUNNELLING, Tag.TAG_COMPOUND)
                ? FunnellingSettings.fromTag(tag.getCompound(KEY_FUNNELLING))
                : FunnellingSettings.DEFAULT;
        QuiverlinkSettings quiverlink = tag.contains(KEY_QUIVERLINK, Tag.TAG_COMPOUND)
                ? QuiverlinkSettings.fromTag(tag.getCompound(KEY_QUIVERLINK))
                : QuiverlinkSettings.DEFAULT;
        LootboundSettings lootbound = tag.contains(KEY_LOOTBOUND, Tag.TAG_COMPOUND)
                ? LootboundSettings.fromTag(tag.getCompound(KEY_LOOTBOUND))
                : LootboundSettings.DEFAULT;
        LightweaverSettings lightweaver = tag.contains(KEY_LIGHTWEAVER, Tag.TAG_COMPOUND)
                ? LightweaverSettings.fromTag(tag.getCompound(KEY_LIGHTWEAVER))
                : LightweaverSettings.DEFAULT;
        SeedflowSettings seedflow = tag.contains(KEY_SEEDFLOW, Tag.TAG_COMPOUND)
                ? SeedflowSettings.fromTag(tag.getCompound(KEY_SEEDFLOW))
                : SeedflowSettings.DEFAULT;
        HopperBridgeSettings hopperBridge = tag.contains(KEY_HOPPER_BRIDGE, Tag.TAG_COMPOUND)
                ? HopperBridgeSettings.fromTag(tag.getCompound(KEY_HOPPER_BRIDGE))
                : HopperBridgeSettings.DEFAULT;
        boolean farmhandEnabled = tag.contains(KEY_FARMHAND_ENABLED, Tag.TAG_BYTE)
                ? tag.getBoolean(KEY_FARMHAND_ENABLED)
                : true;
        boolean imbuedHideEnabled = tag.contains(KEY_IMBUED_HIDE_ENABLED, Tag.TAG_BYTE)
                ? tag.getBoolean(KEY_IMBUED_HIDE_ENABLED)
                : true;
        boolean immortalEnabled = tag.contains(KEY_IMMORTAL_ENABLED, Tag.TAG_BYTE)
                ? tag.getBoolean(KEY_IMMORTAL_ENABLED)
                : true;
        boolean reforgeEnabled = tag.contains(KEY_REFORGE_ENABLED, Tag.TAG_BYTE)
                ? tag.getBoolean(KEY_REFORGE_ENABLED)
                : true;
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

    public void write(FriendlyByteBuf buf) {
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

    public static BackpackAugmentsComponent read(FriendlyByteBuf buf) {
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
            return List.of();
        }
        LinkedHashSet<ResourceLocation> unique = new LinkedHashSet<>();
        ResourceLocation air = new ResourceLocation("minecraft", "air");
        for (ResourceLocation id : filters) {
            if (id == null || unique.size() >= max) {
                break;
            }
            if (ForgeRegistries.ITEMS.containsKey(id) && !id.equals(air)) {
                unique.add(id);
            }
        }
        return List.copyOf(unique);
    }

    private static List<ResourceLocation> readFilters(CompoundTag tag, String key, int max) {
        if (tag == null || !tag.contains(key, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        List<ResourceLocation> filters = new ArrayList<>();
        for (int i = 0; i < list.size() && filters.size() < max; i++) {
            Tag entry = list.get(i);
            if (entry instanceof StringTag stringTag) {
                ResourceLocation id = ResourceLocation.tryParse(stringTag.getAsString());
                if (id != null) {
                    filters.add(id);
                }
            }
        }
        return sanitizeFilters(filters, max);
    }

    private static void writeFilters(CompoundTag tag, String key, List<ResourceLocation> filters) {
        ListTag list = new ListTag();
        for (ResourceLocation id : sanitizeFilters(filters, Integer.MAX_VALUE)) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put(key, list);
    }

    public record FunnellingSettings(boolean enabled, Mode mode, List<ResourceLocation> filters) {

        public static final FunnellingSettings DEFAULT = new FunnellingSettings(true, Mode.ALLOW, List.of());

        public FunnellingSettings {
            mode = mode == null ? Mode.ALLOW : mode;
            filters = sanitizeFilters(filters, FUNNELLING_MAX_FILTERS);
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("enabled", enabled);
            tag.putString("mode", mode.getSerializedName());
            writeFilters(tag, "filters", filters);
            return tag;
        }

        public static FunnellingSettings fromTag(CompoundTag tag) {
            if (tag == null || tag.isEmpty()) {
                return DEFAULT;
            }
            boolean enabled = tag.contains("enabled", Tag.TAG_BYTE) ? tag.getBoolean("enabled") : true;
            Mode mode = Mode.fromTag(tag.getString("mode"));
            List<ResourceLocation> filters = readFilters(tag, "filters", FUNNELLING_MAX_FILTERS);
            return new FunnellingSettings(enabled, mode, filters);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(enabled);
            buf.writeEnum(mode);
            buf.writeVarInt(filters.size());
            for (ResourceLocation id : filters) {
                buf.writeResourceLocation(id);
            }
        }

        public static FunnellingSettings read(FriendlyByteBuf buf) {
            boolean enabled = buf.readBoolean();
            Mode mode = buf.readEnum(Mode.class);
            int count = buf.readVarInt();
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

        public enum Mode implements StringRepresentable {
            ALLOW,
            DISALLOW;

            @Override
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

    public record QuiverlinkSettings(boolean enabled, Priority priority) {

        public static final QuiverlinkSettings DEFAULT = new QuiverlinkSettings(true, Priority.BACKPACK);

        public QuiverlinkSettings {
            priority = priority == null ? Priority.BACKPACK : priority;
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("enabled", enabled);
            tag.putString("priority", priority.getSerializedName());
            return tag;
        }

        public static QuiverlinkSettings fromTag(CompoundTag tag) {
            if (tag == null || tag.isEmpty()) {
                return DEFAULT;
            }
            boolean enabled = tag.contains("enabled", Tag.TAG_BYTE) ? tag.getBoolean("enabled") : true;
            Priority priority = Priority.fromTag(tag.getString("priority"));
            return new QuiverlinkSettings(enabled, priority);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(enabled);
            buf.writeEnum(priority);
        }

        public static QuiverlinkSettings read(FriendlyByteBuf buf) {
            return new QuiverlinkSettings(buf.readBoolean(), buf.readEnum(Priority.class));
        }

        public QuiverlinkSettings withEnabled(boolean enabled) {
            return new QuiverlinkSettings(enabled, priority);
        }

        public QuiverlinkSettings withPriority(Priority priority) {
            return new QuiverlinkSettings(enabled, priority);
        }

        public enum Priority implements StringRepresentable {
            BACKPACK,
            INVENTORY;

            @Override
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

    public record LootboundSettings(boolean enabled, boolean blocks, boolean mobs) {

        public static final LootboundSettings DEFAULT = new LootboundSettings(true, true, true);

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("enabled", enabled);
            tag.putBoolean("blocks", blocks);
            tag.putBoolean("mobs", mobs);
            return tag;
        }

        public static LootboundSettings fromTag(CompoundTag tag) {
            if (tag == null || tag.isEmpty()) {
                return DEFAULT;
            }
            boolean enabled = tag.contains("enabled", Tag.TAG_BYTE) ? tag.getBoolean("enabled") : true;
            boolean blocks = tag.contains("blocks", Tag.TAG_BYTE) ? tag.getBoolean("blocks") : true;
            boolean mobs = tag.contains("mobs", Tag.TAG_BYTE) ? tag.getBoolean("mobs") : true;
            return new LootboundSettings(enabled, blocks, mobs);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(enabled);
            buf.writeBoolean(blocks);
            buf.writeBoolean(mobs);
        }

        public static LootboundSettings read(FriendlyByteBuf buf) {
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

    public record LightweaverSettings(boolean enabled, int minimumLight, boolean placeSound) {

        public static final LightweaverSettings DEFAULT = new LightweaverSettings(true, 7, true);

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("enabled", enabled);
            tag.putInt("minimum_light", minimumLight);
            tag.putBoolean("place_sound", placeSound);
            return tag;
        }

        public static LightweaverSettings fromTag(CompoundTag tag) {
            if (tag == null || tag.isEmpty()) {
                return DEFAULT;
            }
            boolean enabled = tag.contains("enabled", Tag.TAG_BYTE) ? tag.getBoolean("enabled") : true;
            int minimumLight = tag.contains("minimum_light", Tag.TAG_INT) ? tag.getInt("minimum_light") : 7;
            boolean placeSound = tag.contains("place_sound", Tag.TAG_BYTE) ? tag.getBoolean("place_sound") : true;
            return new LightweaverSettings(enabled, minimumLight, placeSound);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(enabled);
            buf.writeVarInt(minimumLight);
            buf.writeBoolean(placeSound);
        }

        public static LightweaverSettings read(FriendlyByteBuf buf) {
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

    public record SeedflowSettings(boolean enabled, boolean randomizeSeeds, boolean useFilters, List<ResourceLocation> filters) {

        public static final SeedflowSettings DEFAULT = new SeedflowSettings(true, false, false, List.of());

        public SeedflowSettings {
            filters = sanitizeFilters(filters, SEEDFLOW_MAX_FILTERS);
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("enabled", enabled);
            tag.putBoolean("randomize_seeds", randomizeSeeds);
            tag.putBoolean("use_filters", useFilters);
            writeFilters(tag, "filters", filters);
            return tag;
        }

        public static SeedflowSettings fromTag(CompoundTag tag) {
            if (tag == null || tag.isEmpty()) {
                return DEFAULT;
            }
            boolean enabled = tag.contains("enabled", Tag.TAG_BYTE) ? tag.getBoolean("enabled") : true;
            boolean randomizeSeeds = tag.contains("randomize_seeds", Tag.TAG_BYTE) ? tag.getBoolean("randomize_seeds") : false;
            boolean useFilters = tag.contains("use_filters", Tag.TAG_BYTE) ? tag.getBoolean("use_filters") : false;
            List<ResourceLocation> filters = readFilters(tag, "filters", SEEDFLOW_MAX_FILTERS);
            return new SeedflowSettings(enabled, randomizeSeeds, useFilters, filters);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(enabled);
            buf.writeBoolean(randomizeSeeds);
            buf.writeBoolean(useFilters);
            buf.writeVarInt(filters.size());
            for (ResourceLocation id : filters) {
                buf.writeResourceLocation(id);
            }
        }

        public static SeedflowSettings read(FriendlyByteBuf buf) {
            boolean enabled = buf.readBoolean();
            boolean randomizeSeeds = buf.readBoolean();
            boolean useFilters = buf.readBoolean();
            int count = buf.readVarInt();
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

    public record HopperBridgeSettings(boolean enabled, boolean insert, boolean extract, FilterMode filterMode, List<ResourceLocation> filters) {

        public static final HopperBridgeSettings DEFAULT = new HopperBridgeSettings(true, true, true, FilterMode.OFF, List.of());

        public HopperBridgeSettings {
            filterMode = filterMode == null ? FilterMode.OFF : filterMode;
            filters = sanitizeFilters(filters, HOPPER_BRIDGE_MAX_FILTERS);
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("enabled", enabled);
            tag.putBoolean("insert", insert);
            tag.putBoolean("extract", extract);
            tag.putString("filter_mode", filterMode.getSerializedName());
            writeFilters(tag, "filters", filters);
            return tag;
        }

        public static HopperBridgeSettings fromTag(CompoundTag tag) {
            if (tag == null || tag.isEmpty()) {
                return DEFAULT;
            }
            boolean enabled = tag.contains("enabled", Tag.TAG_BYTE) ? tag.getBoolean("enabled") : true;
            boolean insert = tag.contains("insert", Tag.TAG_BYTE) ? tag.getBoolean("insert") : true;
            boolean extract = tag.contains("extract", Tag.TAG_BYTE) ? tag.getBoolean("extract") : true;
            FilterMode mode = FilterMode.fromTag(tag.getString("filter_mode"));
            List<ResourceLocation> filters = readFilters(tag, "filters", HOPPER_BRIDGE_MAX_FILTERS);
            return new HopperBridgeSettings(enabled, insert, extract, mode, filters);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(enabled);
            buf.writeBoolean(insert);
            buf.writeBoolean(extract);
            buf.writeEnum(filterMode);
            buf.writeVarInt(filters.size());
            for (ResourceLocation id : filters) {
                buf.writeResourceLocation(id);
            }
        }

        public static HopperBridgeSettings read(FriendlyByteBuf buf) {
            boolean enabled = buf.readBoolean();
            boolean insert = buf.readBoolean();
            boolean extract = buf.readBoolean();
            FilterMode mode = buf.readEnum(FilterMode.class);
            int count = buf.readVarInt();
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

        public enum FilterMode implements StringRepresentable {
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

            @Override
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
