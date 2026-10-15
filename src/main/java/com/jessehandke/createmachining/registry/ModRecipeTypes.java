package com.jessehandke.createmachining.registry;

import java.util.Optional;
import java.util.function.Supplier;

import com.jessehandke.createmachining.CreateMachining;
import com.jessehandke.createmachining.content.lathe.TurningRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Our recipe types, written the same way Create writes its own (see Create's AllRecipeTypes).
 *
 * A recipe needs two registered things:
 *  - a RecipeType: the category ("this is a turning recipe"), which machines search through
 *  - a RecipeSerializer: the code that reads the recipe's JSON file and sends it to clients
 *
 * IRecipeTypeInfo is a Create interface (like an abstract base class in C++) that bundles both,
 * so Create's processing-recipe code can work with our recipe types.
 */
public enum ModRecipeTypes implements IRecipeTypeInfo {
    // JSON files with "type": "create_machining:turning" are read by this serializer
    TURNING(TurningRecipe.Serializer::new);

    private final ResourceLocation id;
    private final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> serializer;
    private final DeferredHolder<RecipeType<?>, RecipeType<?>> type;

    ModRecipeTypes(Supplier<RecipeSerializer<?>> serializerSupplier) {
        String name = name().toLowerCase(); // TURNING -> "turning"
        this.id = CreateMachining.asResource(name);
        this.serializer = Registers.SERIALIZERS.register(name, serializerSupplier);
        this.type = Registers.TYPES.register(name, () -> RecipeType.simple(id));
    }

    public static void register(IEventBus modEventBus) {
        Registers.SERIALIZERS.register(modEventBus);
        Registers.TYPES.register(modEventBus);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    // The <T ...> parts are Java generics, close to C++ templates. The interface lets the caller
    // pick the exact type it wants back, so we cast. It's safe because we built these ourselves.
    @SuppressWarnings("unchecked")
    @Override
    public <T extends RecipeSerializer<?>> T getSerializer() {
        return (T) serializer.get();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I extends RecipeInput, R extends Recipe<I>> RecipeType<R> getType() {
        return (RecipeType<R>) type.get();
    }

    /** Look for a recipe of this type that matches the given input. Empty if nothing matches. */
    public <I extends RecipeInput, R extends Recipe<I>> Optional<RecipeHolder<R>> find(I input, Level level) {
        return level.getRecipeManager().getRecipeFor(getType(), input, level);
    }

    /**
     * The registers live in a nested class because Java creates enum constants (TURNING) before
     * the enum's own static fields. Putting the registers here guarantees they already exist
     * when TURNING's constructor runs.
     */
    private static class Registers {
        private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
                DeferredRegister.create(Registries.RECIPE_SERIALIZER, CreateMachining.MOD_ID);
        private static final DeferredRegister<RecipeType<?>> TYPES =
                DeferredRegister.create(Registries.RECIPE_TYPE, CreateMachining.MOD_ID);
    }
}
