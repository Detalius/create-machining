package com.jessehandke.createmachining.registry;

import com.jessehandke.createmachining.CreateMachining;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateMachining.MOD_ID);

    // The item form of the lathe, i.e. what sits in your inventory before you place it
    public static final DeferredItem<BlockItem> CNC_LATHE = ITEMS.registerSimpleBlockItem(ModBlocks.CNC_LATHE);
    public static final DeferredItem<BlockItem> INSPECTION_STATION = ITEMS.registerSimpleBlockItem(ModBlocks.INSPECTION_STATION);

    // Tooling. Durability = how many parts it can cut (overspeeding burns through it twice as fast).
    public static final DeferredItem<Item> CARBIDE_INSERT = ITEMS.registerSimpleItem("carbide_insert",
            new Item.Properties().durability(64));

    // Turned parts
    public static final DeferredItem<Item> IRON_BUSHING = ITEMS.registerSimpleItem("iron_bushing");
    public static final DeferredItem<Item> BRASS_BUSHING = ITEMS.registerSimpleItem("brass_bushing");

    // Chips that come off the part. Run them through crushing wheels to get the metal back.
    public static final DeferredItem<Item> IRON_SWARF = ITEMS.registerSimpleItem("iron_swarf");
    public static final DeferredItem<Item> BRASS_SWARF = ITEMS.registerSimpleItem("brass_swarf");

    private ModItems() {}
}
