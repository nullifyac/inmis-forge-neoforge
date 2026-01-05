package draylar.inmis.client;

import com.mojang.blaze3d.platform.InputConstants;
import draylar.inmis.network.ServerNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

public class InmisKeybinds {

    private static final KeyMapping OPEN_BACKPACK = new KeyMapping(
            "key.inmis.open_backpack",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "category.inmis.keybindings");

    public static void register() {
        ClientRegistry.registerKeyBinding(OPEN_BACKPACK);
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        while (OPEN_BACKPACK.consumeClick()) {
            ServerNetworking.sendOpenBackpack();
        }
    }
}
