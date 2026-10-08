package draylar.inmis.augment;

import draylar.inmis.Inmis;
import draylar.inmis.config.BackpackInfo;

import java.util.ArrayList;
import java.util.List;

public final class BackpackAugments {

    private static final List<BackpackAugmentType> ORDERED = immutableList(
            BackpackAugmentType.FUNNELLING,
            BackpackAugmentType.QUIVERLINK,
            BackpackAugmentType.FARMHAND,
            BackpackAugmentType.LIGHTWEAVER,
            BackpackAugmentType.LOOTBOUND,
            BackpackAugmentType.IMBUED_HIDE,
            BackpackAugmentType.IMMORTAL,
            BackpackAugmentType.REFORGE,
            BackpackAugmentType.SEEDFLOW,
            BackpackAugmentType.HOPPER_BRIDGE
    );
    private static final List<List<BackpackAugmentType>> TIER_UNLOCKS = immutableList(
            java.util.Collections.emptyList(),
            immutableList(BackpackAugmentType.FUNNELLING),
            immutableList(BackpackAugmentType.FARMHAND, BackpackAugmentType.SEEDFLOW),
            immutableList(BackpackAugmentType.QUIVERLINK),
            immutableList(BackpackAugmentType.LIGHTWEAVER, BackpackAugmentType.LOOTBOUND),
            immutableList(BackpackAugmentType.IMBUED_HIDE),
            java.util.Collections.emptyList(),
            immutableList(BackpackAugmentType.HOPPER_BRIDGE)
    );

    private BackpackAugments() {
    }

    public static List<BackpackAugmentType> getUnlocked(BackpackInfo tier) {
        int index = getTierIndex(tier);
        if (index < 0) {
            return java.util.Collections.emptyList();
        }
        boolean[] unlocked = new boolean[ORDERED.size()];
        for (int i = 0; i <= index; i++) {
            for (BackpackAugmentType type : getTierUnlocksByIndex(i)) {
                int orderedIndex = ORDERED.indexOf(type);
                if (orderedIndex >= 0) {
                    unlocked[orderedIndex] = true;
                }
            }
        }
        List<BackpackAugmentType> result = new ArrayList<>();
        for (int i = 0; i < ORDERED.size(); i++) {
            if (unlocked[i]) {
                result.add(ORDERED.get(i));
            }
        }
        return result;
    }

    public static boolean isUnlocked(BackpackInfo tier, BackpackAugmentType type) {
        return getUnlocked(tier).contains(type);
    }

    public static int getUnlockCount(BackpackInfo tier) {
        return getUnlocked(tier).size();
    }

    public static List<BackpackAugmentType> getTierUnlocks(BackpackInfo tier) {
        int index = getTierIndex(tier);
        if (index < 0) {
            return java.util.Collections.emptyList();
        }
        return getTierUnlocksByIndex(index);
    }

    private static List<BackpackAugmentType> getTierUnlocksByIndex(int index) {
        if (index < 0 || index >= TIER_UNLOCKS.size()) {
            return java.util.Collections.emptyList();
        }
        return TIER_UNLOCKS.get(index);
    }

    private static int getTierIndex(BackpackInfo tier) {
        if (tier == null || Inmis.CONFIG == null || Inmis.CONFIG.backpacks == null) {
            return -1;
        }
        for (int i = 0; i < Inmis.CONFIG.backpacks.size(); i++) {
            BackpackInfo info = Inmis.CONFIG.backpacks.get(i);
            if (info != null && info.getName().equals(tier.getName())) {
                return i;
            }
        }
        return -1;
    }

    @SafeVarargs
    private static <T> List<T> immutableList(T... values) {
        return java.util.Collections.unmodifiableList(java.util.Arrays.asList(values));
    }
}
