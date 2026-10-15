package com.jessehandke.createmachining.registry;

import com.jessehandke.createmachining.CreateMachining;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Tags are named groups of items defined in JSON (data/create_machining/tags/item/...),
 * so modpack makers can add their own items without touching code.
 */
public final class ModTags {
    /** Lathe outputs that get a quality score. Swarf isn't in here. */
    public static final TagKey<Item> MACHINED_PARTS =
            TagKey.create(Registries.ITEM, CreateMachining.asResource("machined_parts"));

    private ModTags() {}
}
