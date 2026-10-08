package draylar.inmis.gametest;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.function.Consumer;
import java.lang.reflect.InvocationTargetException;

/** Loaded only by the dedicated regression-test and runtime-smoke run configurations. */
@Mod(InmisGameTestsMod.MOD_ID)
public final class InmisGameTestsMod {

    public static final String MOD_ID = "inmis_game_tests";

    public InmisGameTestsMod(IEventBus eventBus) {
        DeferredRegister<Consumer<GameTestHelper>> functions = DeferredRegister.create(Registries.TEST_FUNCTION, MOD_ID);
        Class<?>[] suites = {InmisGameplayGameTests.class, InmisDeathGameTests.class,
                InmisCompatibilityGameTests.class, InmisEquipmentDeathGameTests.class,
                InmisEquipmentAndDyeGameTests.class};
        for (Class<?> suite : suites) {
            for (var method : suite.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(GameTest.class)) continue;
                functions.register(method.getName().toLowerCase(java.util.Locale.ROOT), () -> helper -> {
                    try {
                        method.invoke(null, helper);
                    } catch (InvocationTargetException exception) {
                        if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
                        throw new RuntimeException(exception.getCause());
                    } catch (ReflectiveOperationException exception) {
                        throw new RuntimeException(exception);
                    }
                });
            }
        }
        functions.register(eventBus);
        eventBus.addListener((RegisterGameTestsEvent event) -> {
            var environment = event.registerEnvironment(Identifier.fromNamespaceAndPath(MOD_ID, "regression"));
            for (Class<?> suite : suites) {
                for (var method : suite.getDeclaredMethods()) {
                    GameTest test = method.getAnnotation(GameTest.class);
                    if (test == null) continue;
                    Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, method.getName().toLowerCase(java.util.Locale.ROOT));
                    event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
                            new TestData<>(environment, Identifier.fromNamespaceAndPath(test.templateNamespace(), test.template()),
                                    test.timeoutTicks(), test.setupTicks(), test.required())));
                }
            }
        });
    }
}
