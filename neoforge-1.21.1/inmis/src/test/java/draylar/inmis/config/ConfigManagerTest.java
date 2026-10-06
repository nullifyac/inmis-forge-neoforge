package draylar.inmis.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigManagerTest {

    @Test
    void defaultConfigAndModItemBlacklistAreValid() {
        InmisConfig config = new InmisConfig();
        config.blacklist = List.of("supplementaries:sack");

        assertDoesNotThrow(() -> ConfigManager.validate(config));
    }

    @Test
    void zeroOrNegativeDimensionsAreRejected() {
        for (int[] dimensions : List.of(new int[]{0, 3}, new int[]{9, 0},
                new int[]{-9, 3}, new int[]{9, -3}, new int[]{-9, -3})) {
            InmisConfig config = withBackpacks(tier("test", dimensions[0], dimensions[1]));
            assertThrows(IllegalArgumentException.class, () -> ConfigManager.validate(config));
        }
    }

    @Test
    void capacitiesCannotOverflowTheMenuSlotProtocol() {
        assertDoesNotThrow(() -> ConfigManager.validate(withBackpacks(tier("test", 1, Short.MAX_VALUE - 36))));
        assertThrows(IllegalArgumentException.class,
                () -> ConfigManager.validate(withBackpacks(tier("test", 1, Short.MAX_VALUE - 35))));
        assertThrows(IllegalArgumentException.class,
                () -> ConfigManager.validate(withBackpacks(tier("test", Integer.MAX_VALUE, Integer.MAX_VALUE))));
    }

    @Test
    void duplicateRegistryNamesAreRejectedRegardlessOfCase() {
        InmisConfig config = withBackpacks(tier("same", 9, 1), tier("SAME", 9, 2));

        assertThrows(IllegalArgumentException.class, () -> ConfigManager.validate(config));
    }

    @Test
    void malformedNamesAndNullBackpackEntriesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigManager.validate(withBackpacks(tier("not valid", 9, 1))));
        assertThrows(IllegalArgumentException.class,
                () -> ConfigManager.validate(withBackpacks(tier("", 9, 1))));
        assertThrows(IllegalArgumentException.class,
                () -> ConfigManager.validate(withBackpacks(tier(null, 9, 1))));
        assertThrows(IllegalArgumentException.class,
                () -> ConfigManager.validate(withBackpacks((BackpackInfo) null)));
        InmisConfig missingList = new InmisConfig();
        missingList.backpacks = null;
        assertThrows(IllegalArgumentException.class, () -> ConfigManager.validate(missingList));
    }

    private static InmisConfig withBackpacks(BackpackInfo... tiers) {
        InmisConfig config = new InmisConfig();
        config.backpacks = Arrays.asList(tiers);
        return config;
    }

    private static BackpackInfo tier(String name, int width, int rows) {
        return new BackpackInfo(name, width, rows, false, "minecraft:item.armor.equip_leather");
    }
}
