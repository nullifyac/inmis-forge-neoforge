package draylar.inmis.bootstrap;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
public final class InmisMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !mixinClassName.endsWith("BaublesDeathMixin") || getClass().getClassLoader().getResource("baubles/common/event/EventHandlerEntity.class") != null;
    }
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    public List<String> getMixins() { return null; }
    public void preApply(String targetClassName, ClassNode node, String mixinClassName, IMixinInfo info) {}
    public void postApply(String targetClassName, ClassNode node, String mixinClassName, IMixinInfo info) {}
}
