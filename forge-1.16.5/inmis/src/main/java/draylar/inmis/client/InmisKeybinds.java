package draylar.inmis.client;

import net.minecraft.client.util.InputMappings;
import draylar.inmis.network.ServerNetworking;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

public class InmisKeybinds {

    private static final KeyBinding OPEN_BACKPACK = new KeyBinding(
            "key.inmis.open_backpack",
            InputMappings.Type.KEYSYM,
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
