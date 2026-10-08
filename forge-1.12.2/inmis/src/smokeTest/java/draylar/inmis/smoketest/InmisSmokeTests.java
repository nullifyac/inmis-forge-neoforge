package draylar.inmis.smoketest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import draylar.inmis.Inmis;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;

/** Opt-in test mod. Neither this source set nor its resources enter the release jar. */
@Mod(modid="inmis_smoke_tests", name="Inmis Native Checks", version="1", acceptedMinecraftVersions="[1.12.2]")
@Mod.EventBusSubscriber(modid = "inmis_smoke_tests")
public final class InmisSmokeTests {
    @Mod.EventHandler
    public void started(FMLServerStartedEvent event) {
        if (!Boolean.getBoolean("inmis.serverChecks")) return;
        MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        JsonObject result = new JsonObject();
        JsonArray checks = new JsonArray();
        int passed = 0;
        int failed = 0;
        result.addProperty("javaVersion", System.getProperty("java.version"));
        result.addProperty("profile", System.getProperty("inmis.compatProfile", "none"));
        result.addProperty("baublesLoaded", Inmis.BAUBLES_LOADED);
        result.addProperty("nativeSideCount", net.minecraftforge.fml.relauncher.Side.values().length);
        result.addProperty("sideApiSource", net.minecraftforge.fml.relauncher.Side.class.getProtectionDomain().getCodeSource().getLocation().toString());
        boolean correctRuntime = System.getProperty("java.specification.version").equals("1.8");
        boolean correctProfile = Inmis.BAUBLES_LOADED == System.getProperty("inmis.compatProfile", "none").equals("baubles");
        if (!correctRuntime || !correctProfile) failed++;
        for (Class<?> suite : new Class<?>[]{BackpackChecks.class, AdditionalChecks.class,
                LegacyMechanicsChecks.class, CraftingChecks.class, AugmentGameplayChecks.class, SecurityChecks.class, EquipmentDeathChecks.class}) {
            if (suite == EquipmentDeathChecks.class && !Inmis.BAUBLES_LOADED) continue;
            Method[] methods = suite.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));
            for (Method method : methods) {
                if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())
                        || !Arrays.equals(method.getParameterTypes(), new Class<?>[]{SmokeContext.class})) continue;
                JsonObject check = new JsonObject();
                check.addProperty("name", suite.getSimpleName() + "." + method.getName());
                SmokeContext context = new SmokeContext(server.getWorld(0));
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
            server.initiateShutdown();
        }
    }
}
