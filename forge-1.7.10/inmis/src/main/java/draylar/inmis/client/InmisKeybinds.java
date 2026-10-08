package draylar.inmis.client;

import draylar.inmis.network.ServerNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public final class InmisKeybinds {
    private static final KeyBinding OPEN_BACKPACK = new KeyBinding(
            "key.inmis.open_backpack", Keyboard.KEY_B, "category.inmis.keybindings");

    public static void register() {
        ClientRegistry.registerKeyBinding(OPEN_BACKPACK);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (OPEN_BACKPACK.isPressed()) {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft.thePlayer != null && minecraft.currentScreen == null) ServerNetworking.sendOpenBackpack();
        }
    }
}
