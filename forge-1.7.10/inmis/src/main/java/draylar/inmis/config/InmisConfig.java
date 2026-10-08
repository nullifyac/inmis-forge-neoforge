package draylar.inmis.config;



import java.util.Arrays;
import java.util.List;

public class InmisConfig {
    public List<BackpackInfo> backpacks = Arrays.asList(
            BackpackInfo.of("baby", 3, 1, false, "random.chestopen"),
            BackpackInfo.of("frayed", 9, 1, false, "random.chestopen", true),
            BackpackInfo.of("plated", 9, 2, false, "random.chestopen"),
            BackpackInfo.of("gilded", 9, 3, false, "random.chestopen"),
            BackpackInfo.of("bejeweled", 9, 5, false, "random.chestopen"),
            BackpackInfo.of("blazing", 9, 6, true, "random.chestopen"),
            BackpackInfo.of("withered", 11, 6, false, "random.chestopen"),
            BackpackInfo.of("endless", 15, 6, false, "random.chestopen")
    );

    public boolean unstackablesOnly = false;

    public boolean disableShulkers = true;

    public boolean playSound = true;

    public boolean requireArmorTrinketToOpen = false;

    public boolean allowBackpacksInChestplate = true;

    public boolean enableTrinketCompatibility = true;

    public boolean requireEmptyForUnequip = false;

    public boolean spillArmorBackpacksOnDeath = false;

    public boolean spillMainBackpacksOnDeath = false;

    public boolean importBackpackedItems = false;

    public String autoBackpackedTier = "blazing";

    public int autoBackpackedColumns = 9;

    public int autoBackpackedRows = 6;

    public boolean autoBackpackedAllowSmaller = true;

    public boolean trinketRendering = true;

    public String guiTitleColor = "0x404040";
}
