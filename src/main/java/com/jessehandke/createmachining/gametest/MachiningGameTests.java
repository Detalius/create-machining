package com.jessehandke.createmachining.gametest;

import com.jessehandke.createmachining.CreateMachining;
import com.jessehandke.createmachining.content.inspection.InspectionStationBlockEntity;
import com.jessehandke.createmachining.content.lathe.CncLatheBlock;
import com.jessehandke.createmachining.content.lathe.CncLatheBlockEntity;
import com.jessehandke.createmachining.content.quality.QualityGrade;
import com.jessehandke.createmachining.registry.ModBlocks;
import com.jessehandke.createmachining.registry.ModDataComponents;
import com.jessehandke.createmachining.registry.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game automated tests. Each method builds a tiny setup in a real world, runs it, and checks
 * the result. Run them all with: ./gradlew runGameTestServer (it exits with an error if any fail).
 *
 * Positions are relative to the test area (an empty 5x3x5 box), y=1 is the floor level.
 */
@GameTestHolder(CreateMachining.MOD_ID)
@PrefixGameTestTemplate(false)
public class MachiningGameTests {
    private static final String EMPTY = "empty_5x3x5";
    private static final BlockPos LATHE = new BlockPos(2, 1, 2);
    private static final BlockPos MOTOR = new BlockPos(2, 1, 3); // directly behind the lathe

    /** Lathe facing north (front toward -z), Creative Motor behind it driving the headstock. */
    private static CncLatheBlockEntity buildLathe(GameTestHelper helper, int rpm) {
        helper.setBlock(LATHE, ModBlocks.CNC_LATHE.get().defaultBlockState()
                .setValue(CncLatheBlock.HORIZONTAL_FACING, Direction.NORTH));
        helper.setBlock(MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(CreativeMotorBlock.FACING, Direction.NORTH)); // shaft points at the lathe
        CreativeMotorBlockEntity motor = helper.getBlockEntity(MOTOR);
        motor.generatedSpeed.setValue(rpm);
        return helper.getBlockEntity(LATHE);
    }

    /** First part at the ideal speed with a fresh insert: process score should be the max, 95. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void inBandFreshToolScores95(GameTestHelper helper) {
        CncLatheBlockEntity lathe = buildLathe(helper, 64); // iron band is 32-96
        lathe.toolInv.setStackInSlot(0, new ItemStack(ModItems.CARBIDE_INSERT.get()));
        lathe.inputInv.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));

        // succeedWhen re-checks every tick until the checks pass (or the test times out)
        helper.succeedWhen(() -> {
            ItemStack part = findPart(lathe);
            helper.assertTrue(!part.isEmpty(), "no bushing yet");
            helper.assertValueEqual(part.get(ModDataComponents.PROCESS_SCORE), 95, "process score");
            helper.assertValueEqual(lathe.toolInv.getStackInSlot(0).getDamageValue(), 1, "insert wear");
        });
    }

    /** 256 RPM on iron (band 32-96) is way over: quality hits the floor and the insert wears double. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void overspeedTanksQualityAndBurnsTool(GameTestHelper helper) {
        CncLatheBlockEntity lathe = buildLathe(helper, 256);
        lathe.toolInv.setStackInSlot(0, new ItemStack(ModItems.CARBIDE_INSERT.get()));
        lathe.inputInv.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));

        helper.succeedWhen(() -> {
            ItemStack part = findPart(lathe);
            helper.assertTrue(!part.isEmpty(), "no bushing yet");
            helper.assertValueEqual(part.get(ModDataComponents.PROCESS_SCORE), 19, "process score"); // 95 * 0.2
            helper.assertValueEqual(lathe.toolInv.getStackInSlot(0).getDamageValue(), 2, "insert wear");
        });
    }

    /** No insert, no cutting, no matter how long it spins. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void noToolNoParts(GameTestHelper helper) {
        CncLatheBlockEntity lathe = buildLathe(helper, 64);
        lathe.inputInv.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(findPart(lathe).isEmpty(), "lathe cut a part without a tool");
            helper.assertValueEqual(lathe.inputInv.getStackInSlot(0).getCount(), 1, "stock left");
            helper.succeed();
        });
    }

    /** Parts from several cycles pool into ONE uninspected stack instead of a stack per score. */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void latheOutputPoolsIntoOneLot(GameTestHelper helper) {
        CncLatheBlockEntity lathe = buildLathe(helper, 64);
        lathe.toolInv.setStackInSlot(0, new ItemStack(ModItems.CARBIDE_INSERT.get()));
        lathe.inputInv.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));

        helper.succeedWhen(() -> {
            helper.assertTrue(lathe.inputInv.getStackInSlot(0).isEmpty(), "stock not used up yet");
            int bushingStacks = 0;
            for (int slot = 0; slot < lathe.outputInv.getSlots(); slot++)
                if (lathe.outputInv.getStackInSlot(slot).is(ModItems.IRON_BUSHING.get()))
                    bushingStacks++;
            helper.assertValueEqual(bushingStacks, 1, "bushing stacks");
            helper.assertValueEqual(findPart(lathe).getCount(), 6, "bushings in the lot");
        });
    }

    /** Perfect parts (score 95) must always come out graded Perfect, with the hidden score gone. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void inspectionGradesPerfectParts(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.INSPECTION_STATION.get().defaultBlockState());
        InspectionStationBlockEntity station = helper.getBlockEntity(pos);

        ItemStack parts = new ItemStack(ModItems.IRON_BUSHING.get(), 4);
        parts.set(ModDataComponents.PROCESS_SCORE, 95);
        station.inputInv.setStackInSlot(0, parts);

        helper.succeedWhen(() -> {
            helper.assertTrue(station.inputInv.getStackInSlot(0).isEmpty(), "still inspecting");
            int measured = 0;
            for (int slot = 0; slot < station.outputInv.getSlots(); slot++) {
                ItemStack stack = station.outputInv.getStackInSlot(slot);
                if (stack.isEmpty())
                    continue;
                helper.assertValueEqual(stack.get(ModDataComponents.GRADE), QualityGrade.PERFECT, "grade");
                helper.assertTrue(!stack.has(ModDataComponents.PROCESS_SCORE), "hidden score not removed");
                measured += stack.getCount();
            }
            helper.assertValueEqual(measured, 4, "parts measured");
        });
    }

    /**
     * The graded recipes load from JSON and their part checks work: the precision route takes
     * only Perfect bushings, the standard bearing route takes Perfect or In-Tolerance, and the
     * lathe accepts a Needs Rework bushing for a skim cut.
     */
    @GameTest(template = EMPTY)
    public static void gradedRecipesCheckGrades(GameTestHelper helper) {
        Ingredient precisionPart = deployedPart(helper, "precision_mechanism");
        helper.assertTrue(precisionPart.test(graded(ModItems.IRON_BUSHING.get(), QualityGrade.PERFECT)),
                "precision recipe should take a Perfect bushing");
        helper.assertFalse(precisionPart.test(graded(ModItems.IRON_BUSHING.get(), QualityGrade.IN_TOLERANCE)),
                "precision recipe must reject an In-Tolerance bushing");
        helper.assertFalse(precisionPart.test(new ItemStack(ModItems.IRON_BUSHING.get())),
                "precision recipe must reject an uninspected bushing");

        Ingredient bearingPart = deployedPart(helper, "bearing_assembly");
        helper.assertTrue(bearingPart.test(graded(ModItems.BRASS_BUSHING.get(), QualityGrade.IN_TOLERANCE)),
                "bearing recipe should take In-Tolerance");
        helper.assertFalse(bearingPart.test(graded(ModItems.BRASS_BUSHING.get(), QualityGrade.OUT_OF_TOLERANCE)),
                "standard bearing recipe must reject OOT (that's the use-as-is recipe's job)");
        helper.assertTrue(deployedPart(helper, "bearing_assembly_use_as_is")
                        .test(graded(ModItems.BRASS_BUSHING.get(), QualityGrade.OUT_OF_TOLERANCE)),
                "use-as-is recipe should take OOT");

        CncLatheBlockEntity lathe = buildLathe(helper, 64);
        helper.assertTrue(lathe.isValidStock(graded(ModItems.IRON_BUSHING.get(), QualityGrade.NEEDS_REWORK)),
                "lathe should accept a rework part");
        helper.assertFalse(lathe.isValidStock(graded(ModItems.IRON_BUSHING.get(), QualityGrade.PERFECT)),
                "lathe must not re-cut a good part");
        helper.succeed();
    }

    private static ItemStack graded(net.minecraft.world.item.Item item, QualityGrade grade) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.GRADE, grade);
        return stack;
    }

    /** The part held by the deployer in step 1 of one of our sequenced assembly recipes. */
    private static Ingredient deployedPart(GameTestHelper helper, String recipeName) {
        RecipeHolder<?> holder = helper.getLevel().getRecipeManager()
                .byKey(CreateMachining.asResource("sequenced_assembly/" + recipeName))
                .orElseThrow(() -> new GameTestAssertException("missing recipe " + recipeName));
        SequencedAssemblyRecipe recipe = (SequencedAssemblyRecipe) holder.value();
        return recipe.getSequence().get(0).getRecipe().getIngredients().get(1);
    }

    /** The first stack in the lathe's output that's a bushing, or empty. */
    private static ItemStack findPart(CncLatheBlockEntity lathe) {
        for (int slot = 0; slot < lathe.outputInv.getSlots(); slot++) {
            ItemStack stack = lathe.outputInv.getStackInSlot(slot);
            if (stack.is(ModItems.IRON_BUSHING.get()))
                return stack;
        }
        return ItemStack.EMPTY;
    }
}
