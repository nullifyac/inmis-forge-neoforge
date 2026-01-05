package draylar.inmis.item;

import draylar.inmis.config.BackpackInfo;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;

public class DyeableBackpackItem extends BackpackItem implements DyeableLeatherItem {

    public DyeableBackpackItem(BackpackInfo backpack, Item.Properties properties) {
        super(backpack, properties);
    }
}
