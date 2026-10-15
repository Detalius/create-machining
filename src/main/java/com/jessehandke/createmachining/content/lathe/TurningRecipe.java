package com.jessehandke.createmachining.content.lathe;

import com.jessehandke.createmachining.registry.ModRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;

import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;

/**
 * One turning operation: one piece of stock in, up to 4 outputs (part plus chance-based swarf).
 *
 * "extends" is inheritance, like ": public Base" in C++. Create's StandardProcessingRecipe already
 * handles reading ingredients, results, chances and processing_time from JSON, so we only say
 * what's specific to turning.
 */
public class TurningRecipe extends StandardProcessingRecipe<RecipeInput> {

    public TurningRecipe(ProcessingRecipeParams params) {
        super(ModRecipeTypes.TURNING, params);
    }

    /** Does the stock currently in the lathe fit this recipe? */
    @Override
    public boolean matches(RecipeInput input, Level level) {
        if (input.isEmpty())
            return false;
        return ingredients.get(0).test(input.getItem(0));
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
}
