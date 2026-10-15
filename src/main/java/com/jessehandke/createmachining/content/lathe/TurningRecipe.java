package com.jessehandke.createmachining.content.lathe;

import java.util.List;

import com.jessehandke.createmachining.registry.ModRecipeTypes;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * One turning operation: one piece of stock in, up to 4 outputs (part plus chance-based swarf),
 * and an ideal spindle speed band.
 *
 * "extends" is inheritance, like ": public Base" in C++. Create's ProcessingRecipe already
 * handles reading ingredients, results, chances and processing_time from JSON, so we only add
 * what's specific to turning.
 */
public class TurningRecipe extends ProcessingRecipe<RecipeInput, TurningRecipeParams> {

    public TurningRecipe(TurningRecipeParams params) {
        super(ModRecipeTypes.TURNING, params);
    }

    /** Does the stock currently in the lathe fit this recipe? */
    @Override
    public boolean matches(RecipeInput input, Level level) {
        if (input.isEmpty())
            return false;
        return ingredients.get(0).test(input.getItem(0));
    }

    public int getMinRpm() {
        return params.minRpm;
    }

    public int getMaxRpm() {
        return params.maxRpm;
    }

    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    @Override
    protected int getMaxOutputCount() {
        return 4;
    }

    /** Lets recipe JSONs set "processing_time" (cycle time). */
    @Override
    protected boolean canSpecifyDuration() {
        return true;
    }

    /** Catch typos like a band that's upside down when the recipe loads, not mid-game. */
    @Override
    public List<String> validate() {
        List<String> errors = super.validate();
        if (params.minRpm < 1 || params.maxRpm < params.minRpm)
            errors.add("Turning recipe needs 1 <= min_rpm <= max_rpm, got "
                    + params.minRpm + ".." + params.maxRpm);
        return errors;
    }

    /** Reads turning recipes from JSON (and syncs them to clients). */
    public static class Serializer implements RecipeSerializer<TurningRecipe> {
        private final MapCodec<TurningRecipe> codec =
                ProcessingRecipe.codec(TurningRecipe::new, TurningRecipeParams.CODEC);
        private final StreamCodec<RegistryFriendlyByteBuf, TurningRecipe> streamCodec =
                ProcessingRecipe.streamCodec(TurningRecipe::new, TurningRecipeParams.STREAM_CODEC);

        @Override
        public MapCodec<TurningRecipe> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, TurningRecipe> streamCodec() {
            return streamCodec;
        }
    }
}
