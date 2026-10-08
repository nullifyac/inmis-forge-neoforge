package draylar.inmis.smoketest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import draylar.inmis.Inmis;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;

/** Opt-in test mod. Neither this source set nor its resources enter the release jar. */
@Mod("inmis_smoke_tests")
@Mod.EventBusSubscriber(modid = "inmis_smoke_tests")
public final class InmisSmokeTests {
    @SubscribeEvent
    public static void started(FMLServerStartedEvent event) {
        if (!Boolean.getBoolean("inmis.serverChecks")) return;
        MinecraftServer server = event.getServer();
        JsonObject result = new JsonObject();
        JsonArray checks = new JsonArray();
        int passed = 0;
        int failed = 0;
        result.addProperty("javaVersion", System.getProperty("java.version"));
        result.addProperty("profile", System.getProperty("inmis.compatProfile", "none"));
        result.addProperty("curiosLoaded", Inmis.CURIOS_LOADED);
        boolean correctRuntime = System.getProperty("java.specification.version").equals("1.8");
        boolean correctProfile = Inmis.CURIOS_LOADED == System.getProperty("inmis.compatProfile", "none").equals("curios");
        if (!correctRuntime || !correctProfile) failed++;
        for (Class<?> suite : new Class<?>[]{BackpackChecks.class, AdditionalChecks.class,
                LegacyMechanicsChecks.class, EquipmentDeathChecks.class}) {
            if (suite == EquipmentDeathChecks.class && !Inmis.CURIOS_LOADED) continue;
            Method[] methods = suite.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));
            for (Method method : methods) {
                if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())
                        || !Arrays.equals(method.getParameterTypes(), new Class<?>[]{SmokeContext.class})) continue;
                JsonObject check = new JsonObject();
                check.addProperty("name", suite.getSimpleName() + "." + method.getName());
                SmokeContext context = new SmokeContext(server.overworld());
                try {
                    method.invoke(null, context);
                    if (!context.hasSucceeded()) throw new AssertionError("Fixture returned without completion");
                    check.addProperty("passed", true);
                    passed++;
                    Inmis.LOGGER.info("Native assertion passed: {}", method.getName());
                } catch (Throwable error) {
                    Throwable cause = error instanceof InvocationTargetException ? error.getCause() : error;
                    check.addProperty("passed", false);
                    check.addProperty("error", cause.toString());
                    failed++;
                    Inmis.LOGGER.error("Native assertion failed: " + method.getName(), cause);
                } finally {
                    context.killAllEntities();
                }
                checks.add(check);
            }
        }
        result.addProperty("passed", passed);
        result.addProperty("failed", failed);
        result.addProperty("completed", true);
        result.add("checks", checks);
        try {
            Files.write(Paths.get("server-checks.json"), new GsonBuilder().setPrettyPrinting().create()
                    .toJson(result).getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot write native verification result", error);
        } finally {
            Inmis.LOGGER.info("INMIS_NATIVE_CHECKS: passed={}, failed={}", passed, failed);
            server.halt(false);
        }
    }
}
