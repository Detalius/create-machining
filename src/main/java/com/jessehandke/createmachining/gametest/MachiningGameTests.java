package com.jessehandke.createmachining.gametest;

import com.jessehandke.createmachining.CreateMachining;
import com.jessehandke.createmachining.content.inspection.InspectionStationBlockEntity;
import com.jessehandke.createmachining.content.lathe.CncLatheBlock;
import com.jessehandke.createmachining.content.lathe.CncLatheBlockEntity;
import com.jessehandke.createmachining.registry.ModBlocks;
import com.jessehandke.createmachining.registry.ModDataComponents;
import com.jessehandke.createmachining.registry.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

    /** Perfect parts (score 95) must gage between 95% and 100%, and lose their hidden score. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void inspectionGagesPerfectPartsAt95To100(GameTestHelper helper) {
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
                Integer gage = stack.get(ModDataComponents.GAGE);
                helper.assertTrue(gage != null && gage >= 95 && gage <= 100, "gage out of range: " + gage);
                helper.assertTrue(!stack.has(ModDataComponents.PROCESS_SCORE), "hidden score not removed");
                measured += stack.getCount();
            }
            helper.assertValueEqual(measured, 4, "parts measured");
        });
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
