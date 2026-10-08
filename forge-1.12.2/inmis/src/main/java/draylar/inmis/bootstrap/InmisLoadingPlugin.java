package draylar.inmis.bootstrap;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;
import java.util.Map;
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.Name("InmisMixinBootstrap")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions("draylar.inmis.bootstrap")
public final class InmisLoadingPlugin implements IFMLLoadingPlugin {
    public InmisLoadingPlugin() {
        OptionalBaublesBootstrap.prepare();
        MixinBootstrap.init();
        Mixins.addConfiguration("inmis.mixins.json");
    }
    public String[] getASMTransformerClass() { return new String[0]; }
    public String getModContainerClass() { return null; }
    public String getSetupClass() { return null; }
    public void injectData(Map<String,Object> data) {}
    public String getAccessTransformerClass() { return null; }
}
