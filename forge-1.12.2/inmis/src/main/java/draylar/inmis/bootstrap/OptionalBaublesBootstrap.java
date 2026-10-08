package draylar.inmis.bootstrap;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.libraries.Artifact;
import net.minecraftforge.fml.relauncher.libraries.LibraryManager;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarFile;

/** Makes an installed optional target visible before early Mixin selection. */
final class OptionalBaublesBootstrap {
    private static final String TARGET = "baubles/common/event/EventHandlerEntity.class";

    static void prepare() {
        if (Launch.classLoader.getResource(TARGET) != null) return;
        Object home = FMLInjectionData.data()[6];
        if (!(home instanceof File)) return;
        File minecraftHome = (File) home;
        // Use the same ordinary and repository mod candidates as native FML.
        Set<File> candidates = new LinkedHashSet<>(LibraryManager.gatherLegacyCanidates(minecraftHome));
        for (Artifact artifact : LibraryManager.flattenLists(minecraftHome)) {
            File candidate = artifact.getFile();
            if (candidate != null) candidates.add(candidate);
        }
        for (File candidate : candidates) {
            if (!candidate.isFile()) continue;
            try (JarFile jar = new JarFile(candidate)) {
                if (jar.getJarEntry(TARGET) == null) continue;
                // FML must still discover this ordinary mod from its candidate
                // list, rather than count the early URL as a second mod copy.
                if (!CoreModManager.getReparseableCoremods().contains(candidate.getName())) {
                    CoreModManager.getReparseableCoremods().add(candidate.getName());
                }
                Launch.classLoader.addURL(candidate.toURI().toURL());
                FMLLog.log.debug("Inmis exposed the installed Baubles resource for optional Mixin selection");
                return;
            } catch (IOException error) {
                FMLLog.log.debug("Inmis could not inspect an optional mod candidate", error);
            }
        }
    }
}
