package draylar.inmis.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

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

    public static final Codec<BackpackAugmentsComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            FunnellingSettings.CODEC.optionalFieldOf("funnelling", FunnellingSettings.DEFAULT).forGetter(BackpackAugmentsComponent::funnelling),
            QuiverlinkSettings.CODEC.optionalFieldOf("quiverlink", QuiverlinkSettings.DEFAULT).forGetter(BackpackAugmentsComponent::quiverlink),
            LootboundSettings.CODEC.optionalFieldOf("lootbound", LootboundSettings.DEFAULT).forGetter(BackpackAugmentsComponent::lootbound),
            LightweaverSettings.CODEC.optionalFieldOf("lightweaver", LightweaverSettings.DEFAULT).forGetter(BackpackAugmentsComponent::lightweaver),
            SeedflowSettings.CODEC.optionalFieldOf("seedflow", SeedflowSettings.DEFAULT).forGetter(BackpackAugmentsComponent::seedflow),
            HopperBridgeSettings.CODEC.optionalFieldOf("hopper_bridge", HopperBridgeSettings.DEFAULT).forGetter(BackpackAugmentsComponent::hopperBridge),
            Codec.BOOL.optionalFieldOf("farmhand_enabled", true).forGetter(BackpackAugmentsComponent::farmhandEnabled),
            Codec.BOOL.optionalFieldOf("imbued_hide_enabled", true).forGetter(BackpackAugmentsComponent::imbuedHideEnabled),
            Codec.BOOL.optionalFieldOf("immortal_enabled", true).forGetter(BackpackAugmentsComponent::immortalEnabled),
            Codec.BOOL.optionalFieldOf("reforge_enabled", true).forGetter(BackpackAugmentsComponent::reforgeEnabled)
    ).apply(builder, BackpackAugmentsComponent::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackAugmentsComponent> STREAM_CODEC =
            StreamCodec.of(BackpackAugmentsComponent::encode, BackpackAugmentsComponent::decode);

    public BackpackAugmentsComponent {
        funnelling = funnelling == null ? FunnellingSettings.DEFAULT : funnelling;
        quiverlink = quiverlink == null ? QuiverlinkSettings.DEFAULT : quiverlink;
        lootbound = lootbound == null ? LootboundSettings.DEFAULT : lootbound;
        lightweaver = lightweaver == null ? LightweaverSettings.DEFAULT : lightweaver;
        seedflow = seedflow == null ? SeedflowSettings.DEFAULT : seedflow;
        hopperBridge = hopperBridge == null ? HopperBridgeSettings.DEFAULT : hopperBridge;
    }

    private static void encode(RegistryFriendlyByteBuf buf, BackpackAugmentsComponent value) {
        FunnellingSettings.STREAM_CODEC.encode(buf, value.funnelling());
        QuiverlinkSettings.STREAM_CODEC.encode(buf, value.quiverlink());
        LootboundSettings.STREAM_CODEC.encode(buf, value.lootbound());
        LightweaverSettings.STREAM_CODEC.encode(buf, value.lightweaver());
        SeedflowSettings.STREAM_CODEC.encode(buf, value.seedflow());
        HopperBridgeSettings.STREAM_CODEC.encode(buf, value.hopperBridge());
        ByteBufCodecs.BOOL.encode(buf, value.farmhandEnabled());
        ByteBufCodecs.BOOL.encode(buf, value.imbuedHideEnabled());
        ByteBufCodecs.BOOL.encode(buf, value.immortalEnabled());
        ByteBufCodecs.BOOL.encode(buf, value.reforgeEnabled());
    }

    private static BackpackAugmentsComponent decode(RegistryFriendlyByteBuf buf) {
        return new BackpackAugmentsComponent(
                FunnellingSettings.STREAM_CODEC.decode(buf),
                QuiverlinkSettings.STREAM_CODEC.decode(buf),
                LootboundSettings.STREAM_CODEC.decode(buf),
                LightweaverSettings.STREAM_CODEC.decode(buf),
                SeedflowSettings.STREAM_CODEC.decode(buf),
                HopperBridgeSettings.STREAM_CODEC.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.BOOL.decode(buf)
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
        for (ResourceLocation id : filters) {
            if (id == null || unique.size() >= max) {
                break;
            }
            if (BuiltInRegistries.ITEM.containsKey(id) && !id.equals(ResourceLocation.withDefaultNamespace("air"))) {
                unique.add(id);
            }
        }
        return List.copyOf(unique);
    }

    public record FunnellingSettings(boolean enabled, Mode mode, List<ResourceLocation> filters) {

        public static final FunnellingSettings DEFAULT = new FunnellingSettings(true, Mode.ALLOW, List.of());

        public static final Codec<FunnellingSettings> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(FunnellingSettings::enabled),
                Mode.CODEC.optionalFieldOf("mode", Mode.ALLOW).forGetter(FunnellingSettings::mode),
                ResourceLocation.CODEC.sizeLimitedListOf(FUNNELLING_MAX_FILTERS)
                        .optionalFieldOf("filters", List.of()).forGetter(FunnellingSettings::filters)
        ).apply(builder, FunnellingSettings::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, FunnellingSettings> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, FunnellingSettings::enabled,
                Mode.STREAM_CODEC, FunnellingSettings::mode,
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(FUNNELLING_MAX_FILTERS)), FunnellingSettings::filters,
                FunnellingSettings::new
        );

        public FunnellingSettings {
            mode = mode == null ? Mode.ALLOW : mode;
            filters = sanitizeFilters(filters, FUNNELLING_MAX_FILTERS);
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

            public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
            public static final StreamCodec<RegistryFriendlyByteBuf, Mode> STREAM_CODEC =
                    StreamCodec.of(RegistryFriendlyByteBuf::writeEnum, buf -> buf.readEnum(Mode.class));

            @Override
            public String getSerializedName() {
                return name().toLowerCase(Locale.ROOT);
            }
        }
    }

    public record QuiverlinkSettings(boolean enabled, Priority priority) {

        public static final QuiverlinkSettings DEFAULT = new QuiverlinkSettings(true, Priority.BACKPACK);

        public static final Codec<QuiverlinkSettings> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(QuiverlinkSettings::enabled),
                Priority.CODEC.optionalFieldOf("priority", Priority.BACKPACK).forGetter(QuiverlinkSettings::priority)
        ).apply(builder, QuiverlinkSettings::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, QuiverlinkSettings> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, QuiverlinkSettings::enabled,
                Priority.STREAM_CODEC, QuiverlinkSettings::priority,
                QuiverlinkSettings::new
        );

        public QuiverlinkSettings {
            priority = priority == null ? Priority.BACKPACK : priority;
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

            public static final Codec<Priority> CODEC = StringRepresentable.fromEnum(Priority::values);
            public static final StreamCodec<RegistryFriendlyByteBuf, Priority> STREAM_CODEC =
                    StreamCodec.of(RegistryFriendlyByteBuf::writeEnum, buf -> buf.readEnum(Priority.class));

            @Override
            public String getSerializedName() {
                return name().toLowerCase(Locale.ROOT);
            }
        }
    }

    public record LootboundSettings(boolean enabled, boolean blocks, boolean mobs) {

        public static final LootboundSettings DEFAULT = new LootboundSettings(true, true, true);

        public static final Codec<LootboundSettings> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(LootboundSettings::enabled),
                Codec.BOOL.optionalFieldOf("blocks", true).forGetter(LootboundSettings::blocks),
                Codec.BOOL.optionalFieldOf("mobs", true).forGetter(LootboundSettings::mobs)
        ).apply(builder, LootboundSettings::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, LootboundSettings> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, LootboundSettings::enabled,
                ByteBufCodecs.BOOL, LootboundSettings::blocks,
                ByteBufCodecs.BOOL, LootboundSettings::mobs,
                LootboundSettings::new
        );

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

        public static final Codec<LightweaverSettings> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(LightweaverSettings::enabled),
                Codec.INT.optionalFieldOf("minimum_light", 7).forGetter(LightweaverSettings::minimumLight),
                Codec.BOOL.optionalFieldOf("place_sound", true).forGetter(LightweaverSettings::placeSound)
        ).apply(builder, LightweaverSettings::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, LightweaverSettings> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, LightweaverSettings::enabled,
                ByteBufCodecs.INT, LightweaverSettings::minimumLight,
                ByteBufCodecs.BOOL, LightweaverSettings::placeSound,
                LightweaverSettings::new
        );

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

        public static final Codec<SeedflowSettings> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(SeedflowSettings::enabled),
                Codec.BOOL.optionalFieldOf("randomize_seeds", false).forGetter(SeedflowSettings::randomizeSeeds),
                Codec.BOOL.optionalFieldOf("use_filters", false).forGetter(SeedflowSettings::useFilters),
                ResourceLocation.CODEC.sizeLimitedListOf(SEEDFLOW_MAX_FILTERS)
                        .optionalFieldOf("filters", List.of()).forGetter(SeedflowSettings::filters)
        ).apply(builder, SeedflowSettings::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, SeedflowSettings> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SeedflowSettings::enabled,
                ByteBufCodecs.BOOL, SeedflowSettings::randomizeSeeds,
                ByteBufCodecs.BOOL, SeedflowSettings::useFilters,
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(SEEDFLOW_MAX_FILTERS)), SeedflowSettings::filters,
                SeedflowSettings::new
        );

        public SeedflowSettings {
            filters = sanitizeFilters(filters, SEEDFLOW_MAX_FILTERS);
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

        public static final Codec<HopperBridgeSettings> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(HopperBridgeSettings::enabled),
                Codec.BOOL.optionalFieldOf("insert", true).forGetter(HopperBridgeSettings::insert),
                Codec.BOOL.optionalFieldOf("extract", true).forGetter(HopperBridgeSettings::extract),
                FilterMode.CODEC.optionalFieldOf("filter_mode", FilterMode.OFF).forGetter(HopperBridgeSettings::filterMode),
                ResourceLocation.CODEC.sizeLimitedListOf(HOPPER_BRIDGE_MAX_FILTERS)
                        .optionalFieldOf("filters", List.of()).forGetter(HopperBridgeSettings::filters)
        ).apply(builder, HopperBridgeSettings::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, HopperBridgeSettings> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, HopperBridgeSettings::enabled,
                ByteBufCodecs.BOOL, HopperBridgeSettings::insert,
                ByteBufCodecs.BOOL, HopperBridgeSettings::extract,
                FilterMode.STREAM_CODEC, HopperBridgeSettings::filterMode,
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(HOPPER_BRIDGE_MAX_FILTERS)), HopperBridgeSettings::filters,
                HopperBridgeSettings::new
        );

        public HopperBridgeSettings {
            filterMode = filterMode == null ? FilterMode.OFF : filterMode;
            filters = sanitizeFilters(filters, HOPPER_BRIDGE_MAX_FILTERS);
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

            public static final Codec<FilterMode> CODEC = StringRepresentable.fromEnum(FilterMode::values);
            public static final StreamCodec<RegistryFriendlyByteBuf, FilterMode> STREAM_CODEC =
                    StreamCodec.of(RegistryFriendlyByteBuf::writeEnum, buf -> buf.readEnum(FilterMode.class));

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
        }
    }
}
