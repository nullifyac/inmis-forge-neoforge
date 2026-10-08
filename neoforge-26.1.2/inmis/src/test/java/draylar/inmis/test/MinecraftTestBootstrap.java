package draylar.inmis.test;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;

import java.util.List;
import java.util.stream.Stream;

/** Completes the resource-reload component initialization absent from the headless mod launcher. */
public final class MinecraftTestBootstrap {
    private static HolderLookup.Provider registries;

    private MinecraftTestBootstrap() {
    }

    public static synchronized void bindItemComponents() {
        if (registries != null) {
            return;
        }
        // Datagen's VanillaRegistries lookup supplies construction-only tag placeholders.
        // Load real vanilla registry values and tags, as WorldLoader does, before binding defaults.
        try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                List.of(ServerPacksSource.createVanillaPackSource()))) {
            RegistryAccess.Frozen staticRegistries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            List<Registry.PendingTags<?>> pendingTags = TagLoader.loadTagsForExistingRegistries(resources, staticRegistries);
            var context = TagLoader.buildUpdatedLookups(staticRegistries, pendingTags);
            RegistryAccess.Frozen dynamicRegistries = RegistryDataLoader.load(resources, context,
                    RegistryDataLoader.WORLDGEN_REGISTRIES, Runnable::run, pendingTags).join();
            pendingTags.forEach(Registry.PendingTags::apply);
            HolderLookup.Provider lookup = HolderLookup.Provider.create(
                    Stream.concat(staticRegistries.listRegistries(), dynamicRegistries.listRegistries()));
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup)
                    .forEach(DataComponentInitializers.PendingComponents::apply);
            registries = lookup;
        }
    }
}
