package draylar.inmis.client;

import com.mojang.blaze3d.platform.InputConstants;
import draylar.inmis.network.ServerNetworking;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class InmisKeybinds {

    private static final KeyMapping OPEN_BACKPACK = new KeyMapping(
            "key.inmis.open_backpack",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "category.inmis.keybindings");

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_BACKPACK);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_BACKPACK.consumeClick()) {
            ServerNetworking.sendOpenBackpack();
        }
    }
}
