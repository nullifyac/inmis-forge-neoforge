package draylar.inmis.smoketest;

import draylar.inmis.Inmis;
import draylar.inmis.augment.BackpackInventory;
import draylar.inmis.item.BackpackItem;
import draylar.inmis.item.DyeableBackpackItem;
import draylar.inmis.item.component.BackpackAugmentsComponent;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.init.Items;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

public final class CraftingChecks {
    private CraftingChecks() {}

    public static void everyNativeRecipeLoadsAndUpgradesPreserveContents(SmokeContext context) {
        String[] recipes = {"baby_backpack", "frayed_backpack", "plated_backpack", "gilded_backpack",
                "bejeweled_backpack", "blazing_backpack", "withered_backpack", "endless_backpack", "ender_pouch"};
        for (String name : recipes) {
            IRecipe recipe = CraftingManager.REGISTRY.getObject(Inmis.id(name));
            check(recipe != null, "Missing actual loaded recipe " + name);
            InventoryCrafting grid = grid();
            for (int i=0;i<recipe.getIngredients().size();i++) {
                Ingredient ingredient = recipe.getIngredients().get(i);
                if (ingredient == Ingredient.EMPTY) continue;
                ItemStack[] choices = ingredient.getMatchingStacks();
                check(choices.length>0, "Native recipe ingredient did not resolve: " + name + " slot " + i);
                grid.setInventorySlotContents(i, choices[0].copy());
            }
            ItemStack input = grid.getStackInSlot(4);
            if (input.getItem() instanceof BackpackItem) {
                input.setStackDisplayName("Named native backpack");
                Inmis.tag(input).setString("inmis_test_addon", "preserved");
                new BackpackInventory(input, ((BackpackItem)input.getItem()).getTier())
                        .setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 7));
                Inmis.setBackpackAugments(input, BackpackAugmentsComponent.DEFAULT.withFarmhandEnabled(true));
            }
            check(recipe.matches(grid, context.getLevel()), "Actual loaded recipe failed valid native inputs: " + name);
            ItemStack output = CraftingManager.findMatchingResult(grid, context.getLevel());
            check(!output.isEmpty() && output.getItem()==recipe.getRecipeOutput().getItem(), "Crafting manager returned wrong result: " + name);
            if (input.getItem() instanceof BackpackItem) {
                check(output.getDisplayName().equals(input.getDisplayName()) && Inmis.tag(output).getString("inmis_test_addon").equals("preserved"),
                        "Upgrade lost saved name or addon metadata: " + name);
                check(Inmis.getBackpackContents(output).stream().mapToInt(ItemStack::getCount).sum()==7,
                        "Upgrade lost actual stored contents: " + name);
                check(Inmis.tag(output).getCompoundTag("Augments").equals(Inmis.tag(input).getCompoundTag("Augments")),
                        "Upgrade lost actual augment settings: " + name);
                check(Inmis.getBackpackContents(input).stream().mapToInt(ItemStack::getCount).sum()==7,
                        "Crafting result mutated the source backpack: " + name);
            }
        }
        context.succeed();
    }

    public static void emptyUpgradeAndDyePreserveAugmentsAndStoredNbt(SmokeContext context) {
        IRecipe upgrade = CraftingManager.REGISTRY.getObject(Inmis.id("plated_backpack"));
        InventoryCrafting grid=grid();
        ItemStack frayed=bag("frayed");
        Inmis.setBackpackAugments(frayed, BackpackAugmentsComponent.DEFAULT.withFarmhandEnabled(true));
        for (int i=0;i<9;i++) grid.setInventorySlotContents(i, i==4 ? frayed : new ItemStack(Items.IRON_INGOT));
        ItemStack result=CraftingManager.findMatchingResult(grid,context.getLevel());
        check(upgrade!=null&&!result.isEmpty(),"Empty native upgrade did not craft");
        check(Inmis.tag(result).getCompoundTag("Augments").equals(Inmis.tag(frayed).getCompoundTag("Augments")),"Empty upgrade reset stored augment settings");
        ItemStack dyed=bag("frayed");
        new BackpackInventory(dyed,((BackpackItem)dyed.getItem()).getTier()).setInventorySlotContents(0,new ItemStack(Items.DIAMOND,7));
        Inmis.setBackpackAugments(dyed,BackpackAugmentsComponent.DEFAULT.withFarmhandEnabled(true));
        InventoryCrafting dye=grid(); dye.setInventorySlotContents(0,dyed); dye.setInventorySlotContents(1,new ItemStack(Items.DYE,1,1));
        ItemStack recolored=CraftingManager.findMatchingResult(dye,context.getLevel());
        check(recolored.getItem()==dyed.getItem()&&((DyeableBackpackItem)recolored.getItem()).getColor(recolored)!=((DyeableBackpackItem)dyed.getItem()).getColor(dyed),"Native dye crafting did not apply dye color");
        check(Inmis.tag(recolored).getTagList("Inventory",10).equals(Inmis.tag(dyed).getTagList("Inventory",10))&&Inmis.tag(recolored).getCompoundTag("Augments").equals(Inmis.tag(dyed).getCompoundTag("Augments")),"Native dye crafting changed contents/settings");
        context.succeed();
    }

    public static void nativeRecipeUnlockPredicatesRejectUnrelatedItems(SmokeContext context) throws Exception {
        String[] recipes = {"baby_backpack", "frayed_backpack", "plated_backpack", "gilded_backpack", "bejeweled_backpack", "blazing_backpack", "withered_backpack", "endless_backpack", "ender_pouch"};
        int checked=0;
        for(String recipe:recipes) {
            String resource="assets/inmis/advancements/recipes/"+recipe+".json";
            try(java.io.InputStream stream=CraftingChecks.class.getClassLoader().getResourceAsStream(resource)) {
                check(stream!=null,"Native recipe advancement missing "+recipe);
                com.google.gson.JsonObject json=new com.google.gson.JsonParser().parse(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for(java.util.Map.Entry<String,com.google.gson.JsonElement> criterion:json.getAsJsonObject("criteria").entrySet()) {
                    com.google.gson.JsonObject conditions=criterion.getValue().getAsJsonObject().getAsJsonObject("conditions");
                    if(!conditions.has("items"))continue;
                    for(com.google.gson.JsonElement predicate:conditions.getAsJsonArray("items")) {
                        String itemId=predicate.getAsJsonObject().get("item").getAsString();
                        net.minecraft.item.Item item=net.minecraft.item.Item.REGISTRY.getObject(new net.minecraft.util.ResourceLocation(itemId));
                        check(item!=null&&item!=Items.AIR,"Native advancement references nonexistent item "+itemId);
                        net.minecraft.advancements.critereon.ItemPredicate parsed=net.minecraft.advancements.critereon.ItemPredicate.deserialize(predicate);
                        check(parsed.test(new ItemStack(item))&&!parsed.test(new ItemStack(Items.STICK)),"Native recipe unlock predicate matched unrelated items: "+recipe+"/"+criterion.getKey());
                        checked++;
                    }
                }
            }
        }
        check(checked>=9,"Native recipe advancement predicates were not exercised");
        context.succeed();
    }

    private static InventoryCrafting grid() { return new InventoryCrafting(new Container(){public boolean canInteractWith(EntityPlayer player){return true;}},3,3); }
    private static ItemStack bag(String tier) { return new ItemStack(Inmis.BACKPACKS.stream().map(e->e.get()).filter(b->b.getTier().getName().equals(tier)).findFirst().orElseThrow(()->new AssertionError("Missing tier "+tier))); }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
