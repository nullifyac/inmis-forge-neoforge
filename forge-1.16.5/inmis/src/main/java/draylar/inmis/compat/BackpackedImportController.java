package draylar.inmis.compat;

import draylar.inmis.Inmis;

public final class BackpackedImportController {

    private static Boolean overrideValue = null;

    private BackpackedImportController() {
    }

    public static boolean isImportEnabled() {
        if (overrideValue != null) {
            return overrideValue;
        }

        return Inmis.CONFIG != null && Inmis.CONFIG.importBackpackedItems;
    }

    public static void setOverride(Boolean override) {
        overrideValue = override;
    }

    public static boolean isOverridden() {
        return overrideValue != null;
    }

    public static boolean getConfigDefault() {
        return Inmis.CONFIG != null && Inmis.CONFIG.importBackpackedItems;
    }
}
