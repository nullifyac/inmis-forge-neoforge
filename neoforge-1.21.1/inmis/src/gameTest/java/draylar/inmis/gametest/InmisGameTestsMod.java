package draylar.inmis.gametest;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Loaded only by the dedicated regression-test and runtime-smoke run configurations. */
@Mod(InmisGameTestsMod.MOD_ID)
public final class InmisGameTestsMod {

    public static final String MOD_ID = "inmis_game_tests";

    public InmisGameTestsMod(IEventBus eventBus) {
    }
}
