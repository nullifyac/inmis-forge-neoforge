package draylar.inmis.augment;

import draylar.inmis.Inmis;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.ResourceLocation;

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
    private final ITextComponent label;
    private final ITextComponent description;

    BackpackAugmentType(String id) {
        this.id = id;
        this.icon = new ResourceLocation(Inmis.MOD_ID, "textures/gui/sprites/augment/" + id + ".png");
        this.label = new TranslationTextComponent("augment.backpacked." + id);
        this.description = new TranslationTextComponent("augment.backpacked." + id + ".desc");
    }

    public String id() {
        return id;
    }

    public ResourceLocation icon() {
        return icon;
    }

    public ITextComponent label() {
        return label;
    }

    public ITextComponent description() {
        return description;
    }
}
