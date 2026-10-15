package com.jessehandke.createmachining.registry;

import com.jessehandke.createmachining.CreateMachining;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;

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

    // Assemblies. The "incomplete" one is the in-progress item that rides the belt during
    // Create's sequenced assembly (it shows a progress bar), so it's Create's own item class.
    public static final DeferredItem<Item> BEARING_ASSEMBLY = ITEMS.registerSimpleItem("bearing_assembly");
    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_BEARING_ASSEMBLY = ITEMS.registerItem(
            "incomplete_bearing_assembly", SequencedAssemblyItem::new);

    // Chips that come off the part. Run them through crushing wheels to get the metal back.
    public static final DeferredItem<Item> IRON_SWARF = ITEMS.registerSimpleItem("iron_swarf");
    public static final DeferredItem<Item> BRASS_SWARF = ITEMS.registerSimpleItem("brass_swarf");

    private ModItems() {}
}
