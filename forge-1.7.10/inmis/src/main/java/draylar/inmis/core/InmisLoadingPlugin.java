package draylar.inmis.core;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.util.Map;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("InmisBackpackHooks")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"draylar.inmis.core"})
public final class InmisLoadingPlugin implements IFMLLoadingPlugin {
    static boolean obfuscated;
    public String[] getASMTransformerClass(){return new String[]{"draylar.inmis.core.BackpackDeathTransformer"};}
    public String getModContainerClass(){return null;}
    public String getSetupClass(){return null;}
    public void injectData(Map<String,Object> data){obfuscated=Boolean.TRUE.equals(data.get("runtimeDeobfuscationEnabled"));}
    public String getAccessTransformerClass(){return null;}
}
