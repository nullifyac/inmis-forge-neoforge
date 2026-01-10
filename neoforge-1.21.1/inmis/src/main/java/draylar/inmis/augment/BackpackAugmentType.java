package draylar.inmis.augment;

import draylar.inmis.Inmis;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public enum BackpackAugmentType {
    FUNNELLING("funnelling"),
    QUIVERLINK("quiverlink"),
    FARMHAND("farmhand"),
    LIGHTWEAVER("lightweaver"),
    LOOTBOUND("lootbound"),
    IMBUED_HIDE("imbued_hide"),
    IMMORTAL("immortal"),
    REFORGE("reforge"),
    SEEDFLOW("seedflow"),
    HOPPER_BRIDGE("hopper_bridge");

    private final String id;
    private final ResourceLocation icon;
    private final Component label;
    private final Component description;

    BackpackAugmentType(String id) {
        this.id = id;
        this.icon = ResourceLocation.fromNamespaceAndPath(Inmis.MOD_ID, "textures/gui/sprites/augment/" + id + ".png");
        this.label = Component.translatable("augment.backpacked." + id);
        this.description = Component.translatable("augment.backpacked." + id + ".desc");
    }

    public String id() {
        return id;
    }

    public ResourceLocation icon() {
        return icon;
    }

    public Component label() {
        return label;
    }

    public Component description() {
        return description;
    }
}
