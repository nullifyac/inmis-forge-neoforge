package draylar.inmis.smoketest;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.relauncher.Side;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import draylar.inmis.Inmis;
import draylar.inmis.compat.BaublesCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

@Mod(modid="inmis_smoke_tests",name="Inmis Native Checks",version="1",acceptedMinecraftVersions="[1.7.10]",dependencies="after:inmis")
public final class InmisSmokeTests {
    @Mod.EventHandler public void init(FMLInitializationEvent event) throws Exception {
        FMLCommonHandler.instance().bus().register(new InmisSmokeServer());
        if(FMLCommonHandler.instance().getSide()==Side.CLIENT&&Boolean.getBoolean("inmis.smokeClient"))
            Class.forName("draylar.inmis.smoketest.InmisClientSmoke").getMethod("register").invoke(null);
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent event) throws Exception {
        if(!Boolean.getBoolean("inmis.serverChecks"))return;
        MinecraftServer server=FMLCommonHandler.instance().getMinecraftServerInstance();WorldServer world=server.worldServers[0];
        world.getChunkFromChunkCoords(0,0);JsonObject result=new JsonObject();JsonArray checks=new JsonArray();int passed=0,failed=0;
        result.addProperty("javaVersion",System.getProperty("java.version"));result.addProperty("profile",System.getProperty("inmis.compatProfile","none"));result.addProperty("baublesLoaded",BaublesCompat.isLoaded());
        if(!System.getProperty("java.specification.version").equals("1.8")||BaublesCompat.isLoaded()!=System.getProperty("inmis.compatProfile","none").equals("baubles"))failed++;
        for(Class<?> suite:new Class<?>[]{BackpackChecks.class,LegacyMechanicsChecks.class}){
            Method[] methods=suite.getDeclaredMethods();java.util.Arrays.sort(methods,java.util.Comparator.comparing(Method::getName));
            for(Method method:methods){if(!Modifier.isPublic(method.getModifiers())||!Modifier.isStatic(method.getModifiers())||!java.util.Arrays.equals(method.getParameterTypes(),new Class<?>[]{WorldServer.class}))continue;
                JsonObject check=new JsonObject();check.addProperty("name",method.getName());
                try{method.invoke(null,world);check.addProperty("passed",true);passed++;Inmis.LOGGER.info("Native assertion passed: {}",method.getName());}
                catch(Throwable error){Throwable cause=error instanceof java.lang.reflect.InvocationTargetException?error.getCause():error;check.addProperty("passed",false);check.addProperty("error",cause.toString());failed++;Inmis.LOGGER.error("Native assertion failed: "+method.getName(),cause);}
                checks.add(check);
            }
        }
        if(BaublesCompat.isLoaded()){
            Method[] methods=Class.forName("draylar.inmis.smoketest.BaublesChecks").getDeclaredMethods();java.util.Arrays.sort(methods,java.util.Comparator.comparing(Method::getName));
            for(Method method:methods){if(!Modifier.isPublic(method.getModifiers())||!Modifier.isStatic(method.getModifiers())||!java.util.Arrays.equals(method.getParameterTypes(),new Class<?>[]{WorldServer.class}))continue;
                JsonObject check=new JsonObject();check.addProperty("name",method.getName());
                try{method.invoke(null,world);check.addProperty("passed",true);passed++;Inmis.LOGGER.info("Native assertion passed: {}",method.getName());}
                catch(Throwable error){Throwable cause=error instanceof java.lang.reflect.InvocationTargetException?error.getCause():error;check.addProperty("passed",false);check.addProperty("error",cause.toString());failed++;Inmis.LOGGER.error("Native assertion failed: "+method.getName(),cause);}checks.add(check);
            }
        }
        result.addProperty("passed",passed);result.addProperty("failed",failed);result.addProperty("completed",true);result.add("checks",checks);
        Files.write(Paths.get("server-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));
        Inmis.LOGGER.info("INMIS_NATIVE_CHECKS: passed={}, failed={}",passed,failed);server.initiateShutdown();
    }
}
