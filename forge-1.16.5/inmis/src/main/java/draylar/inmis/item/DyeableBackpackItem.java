package draylar.inmis.item;

import draylar.inmis.config.BackpackInfo;
import net.minecraft.item.IDyeableArmorItem;
import net.minecraft.item.Item;

public class DyeableBackpackItem extends BackpackItem implements IDyeableArmorItem {

    public DyeableBackpackItem(BackpackInfo backpack, Item.Properties properties) {
        super(backpack, properties);
    }
}
