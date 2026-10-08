package draylar.inmis.config;

import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InmisConfig {

    public List<BackpackInfo> backpacks = Arrays.asList(
            BackpackInfo.of("baby", 3, 1, false, SoundEvents.ARMOR_EQUIP_LEATHER),
            BackpackInfo.of("frayed", 9, 1, false, SoundEvents.ARMOR_EQUIP_LEATHER, true),
            BackpackInfo.of("plated", 9, 2, false, SoundEvents.ARMOR_EQUIP_IRON),
            BackpackInfo.of("gilded", 9, 3, false, SoundEvents.ARMOR_EQUIP_GOLD),
            BackpackInfo.of("bejeweled", 9, 5, false, SoundEvents.ARMOR_EQUIP_DIAMOND),
            BackpackInfo.of("blazing", 9, 6, true, SoundEvents.ARMOR_EQUIP_LEATHER),
            BackpackInfo.of("withered", 11, 6, false, SoundEvents.ARMOR_EQUIP_LEATHER),
            BackpackInfo.of("endless", 15, 6, false, SoundEvents.ARMOR_EQUIP_LEATHER)
    );

    public boolean unstackablesOnly = false;

    public boolean disableShulkers = true;

    public List<String> blacklist = new ArrayList<>();

    public boolean playSound = true;

    public boolean requireArmorTrinketToOpen = false;

    public boolean allowBackpacksInChestplate = true;

    public boolean enableTrinketCompatibility = true;

    public boolean requireEmptyForUnequip = false;

    public boolean spillArmorBackpacksOnDeath = false;

    public boolean spillMainBackpacksOnDeath = false;

    public boolean importBackpackedItems = false;

    public String autoBackpackedTier = "bejeweled";

    public int autoBackpackedColumns = 9;

    public int autoBackpackedRows = 5;

    public boolean autoBackpackedAllowSmaller = true;

    public boolean trinketRendering = true;

    public String guiTitleColor = "0x404040";
}
