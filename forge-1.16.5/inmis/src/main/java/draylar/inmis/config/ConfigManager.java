package draylar.inmis.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ConfigManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "inmis.json";

    private ConfigManager() {
    }

    public static InmisConfig load() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
        if (Files.exists(configPath)) {
            try (BufferedReader reader = Files.newBufferedReader(configPath)) {
                InmisConfig config = GSON.fromJson(reader, InmisConfig.class);
                if (config != null) {
                    validate(config);
                    return config;
                }
                throw new IllegalArgumentException("Config must contain a JSON object.");
            } catch (IOException | JsonSyntaxException e) {
                throw new IllegalStateException("Cannot read " + configPath
                        + ". Correct the existing file; it has been preserved.", e);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid " + configPath
                        + ": " + e.getMessage() + " The existing file has been preserved.", e);
            }
        }

        InmisConfig defaultConfig = new InmisConfig();
        save(configPath, defaultConfig);
        return defaultConfig;
    }

    static void validate(InmisConfig config) {
        if (config.backpacks == null) {
            throw new IllegalArgumentException("backpacks must be a list.");
        }
        Set<String> names = new HashSet<>();
        for (BackpackInfo backpack : config.backpacks) {
            if (backpack == null || backpack.getName() == null
                    || backpack.getName().isEmpty()) {
                throw new IllegalArgumentException("Each backpack must have a name.");
            }
            String name = backpack.getName().toLowerCase(Locale.ROOT);
            if (!name.matches("[a-z0-9/._-]+") || !names.add(name)) {
                throw new IllegalArgumentException("Invalid or duplicate backpack name: " + name);
            }
            long slots = (long) backpack.getRowWidth() * backpack.getNumberOfRows();
            if (backpack.getRowWidth() <= 0 || backpack.getNumberOfRows() <= 0
                    || slots > Short.MAX_VALUE - 36) {
                throw new IllegalArgumentException("Backpack " + name
                        + " needs positive dimensions and at most " + (Short.MAX_VALUE - 36)
                        + " slots (the menu protocol uses signed short slot indexes).");
            }
        }
    }

    private static void save(Path path, InmisConfig config) {
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(path)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to save inmis config.", e);
        }
    }
}
