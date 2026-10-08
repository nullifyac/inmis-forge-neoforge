package draylar.inmis.augment;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;

/** Defers only our automatic torch placement sounds until Forge accepts placement. */
public final class PlacementSounds {
    private static final ThreadLocal<List<Sound>> PENDING = new ThreadLocal<>();

    private PlacementSounds() { }

    public static boolean placeTorch(EntityPlayer player, ItemStack stack, int x, int y, int z, boolean audible) {
        List<Sound> parent = PENDING.get();
        List<Sound> sounds = new ArrayList<>();
        PENDING.set(sounds);
        boolean placed;
        try {
            placed = ForgeHooks.onPlaceItemIntoWorld(stack, player, player.worldObj, x, y - 1, z, 1, .5F, 1F, .5F);
        } finally {
            if (parent == null) PENDING.remove();
            else PENDING.set(parent);
        }
        if (placed && audible) {
            for (Sound sound : sounds) {
                if (parent == null) sound.play();
                else parent.add(sound);
            }
        }
        return placed;
    }

    /** Native ItemBlock bridge; normal player placement delegates immediately. */
    public static void playSound(World world, double x, double y, double z, String name, float volume, float pitch) {
        List<Sound> sounds = PENDING.get();
        if (sounds == null) world.playSoundEffect(x, y, z, name, volume, pitch);
        else sounds.add(new Sound(world, x, y, z, name, volume, pitch));
    }

    private static final class Sound {
        private final World world;
        private final double x, y, z;
        private final String name;
        private final float volume, pitch;

        private Sound(World world, double x, double y, double z, String name, float volume, float pitch) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name;
            this.volume = volume;
            this.pitch = pitch;
        }

        private void play() { world.playSoundEffect(x, y, z, name, volume, pitch); }
    }
}
