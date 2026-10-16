package com.jessehandke.createmachining.gametest;

import com.jessehandke.createmachining.CreateMachining;
import com.jessehandke.createmachining.content.cnc.CncLatheBlockEntity;
import com.jessehandke.createmachining.content.cnc.CncLatheItem;
import com.jessehandke.createmachining.content.cnc.CncLatheLayout;
import com.jessehandke.createmachining.content.cnc.CncLathePart;
import com.jessehandke.createmachining.registry.ModBlocks;
import com.jessehandke.createmachining.registry.ModDataComponents;
import com.jessehandke.createmachining.registry.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * In-game tests for the CNC Lathe multiblock.
 *
 * Test layout (relative to the 7x4x7 test area), machine front facing north:
 *   the machine runs from x=4 (headstock) down to x=2 (tailstock), z=2 (front) to z=3 (back),
 *   y=1..2; the controller is at (4,1,3) and the Creative Motor sits behind it at (4,1,4).
 */
@GameTestHolder(CreateMachining.MOD_ID)
@PrefixGameTestTemplate(false)
public class CncLatheGameTests {
    private static final String AREA = "empty_7x4x7";
    private static final Direction FRONT = Direction.NORTH;
    private static final BlockPos ORIGIN = new BlockPos(4, 1, 2);

    private static BlockPos rel(CncLathePart part) {
        return CncLatheLayout.posOf(part, ORIGIN, FRONT);
    }

    private static CncLatheBlockEntity build(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(ORIGIN);
        helper.assertTrue(CncLatheItem.canBuild(helper.getLevel(), origin, FRONT), "no room for the machine");
        CncLatheItem.build(helper.getLevel(), origin, FRONT);
        return helper.getBlockEntity(rel(CncLathePart.CONTROLLER));
    }

    private static void power(GameTestHelper helper, int rpm) {
        BlockPos motor = rel(CncLathePart.CONTROLLER).relative(FRONT.getOpposite());
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, FRONT));
        CreativeMotorBlockEntity be = helper.getBlockEntity(motor);
        be.generatedSpeed.setValue(rpm);
    }

    /** All 12 blocks appear, and breaking any one of them takes down the whole machine. */
    @GameTest(template = AREA)
    public static void placesAndBreaksAsOneMachine(GameTestHelper helper) {
        build(helper);
        helper.assertBlockPresent(ModBlocks.CNC_LATHE.get(), rel(CncLathePart.CONTROLLER));
        for (CncLathePart part : CncLathePart.values())
            if (part != CncLathePart.CONTROLLER)
                helper.assertBlockPresent(ModBlocks.CNC_LATHE_STRUCTURAL.get(), rel(part));

        helper.destroyBlock(rel(CncLathePart.TAIL_TOP)); // break the far corner
        for (CncLathePart part : CncLathePart.values())
            helper.assertBlockPresent(Blocks.AIR, rel(part));
        helper.assertItemEntityPresent(ModItems.CNC_LATHE.get());
        helper.succeed();
    }

    /** A blocked spot means no placement. */
    @GameTest(template = AREA)
    public static void refusesWithoutRoom(GameTestHelper helper) {
        helper.setBlock(rel(CncLathePart.TURRET), Blocks.STONE);
        helper.assertFalse(CncLatheItem.canBuild(helper.getLevel(), helper.absolutePos(ORIGIN), FRONT),
                "should not fit around a stone block");
        helper.succeed();
    }

    /**
     * A full powered cycle: door shuts, it cuts, door opens, a Perfect-setup part is in the
     * parts catcher (reachable through the port), and the chips went to the tray, not the parts.
     */
    @GameTest(template = AREA, timeoutTicks = 600)
    public static void runsAFullCycle(GameTestHelper helper) {
        CncLatheBlockEntity lathe = build(helper);
        power(helper, 64);
        lathe.toolInv.setStackInSlot(0, new ItemStack(ModItems.CARBIDE_INSERT.get()));
        lathe.stockInv.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));

        helper.succeedWhen(() -> {
            helper.assertValueEqual(lathe.phase, CncLatheBlockEntity.Phase.IDLE, "phase");
            IItemHandler parts = port(helper, CncLathePart.PARTS_CATCHER);
            helper.assertTrue(parts != null, "parts catcher has no port");
            ItemStack part = parts.extractItem(0, 64, true); // simulate: just look
            helper.assertTrue(part.is(ModItems.IRON_BUSHING.get()), "no bushing in the parts catcher");
            helper.assertValueEqual(part.getCount(), 2, "bushings");
            helper.assertValueEqual(part.get(ModDataComponents.PROCESS_SCORE), 95, "process score");
            for (int i = 0; i < lathe.partsInv.getSlots(); i++)
                helper.assertFalse(lathe.partsInv.getStackInSlot(i).is(ModItems.IRON_SWARF.get()), "swarf mixed into parts");
        });
    }

    /** Ports only allow what belongs there. */
    @GameTest(template = AREA)
    public static void portsFilterItems(GameTestHelper helper) {
        build(helper);
        IItemHandler chuck = port(helper, CncLathePart.CHUCK);
        helper.assertTrue(chuck.insertItem(0, new ItemStack(Items.DIRT), true).getCount() == 1, "chuck took dirt");
        helper.assertTrue(chuck.insertItem(0, new ItemStack(Items.IRON_INGOT), true).isEmpty(), "chuck refused stock");
        IItemHandler turret = port(helper, CncLathePart.TURRET);
        helper.assertTrue(turret.insertItem(0, new ItemStack(ModItems.CARBIDE_INSERT.get()), true).isEmpty(), "turret refused insert");
        helper.assertTrue(port(helper, CncLathePart.CHIP_OUTLET) == null, "chip outlet open without a conveyor");
        helper.assertTrue(port(helper, CncLathePart.TAIL_TOP) == null, "a plain panel shouldn't be a port");
        helper.succeed();
    }

    /** Right-clicking the chip tray with a belt installs the conveyor, which pushes chips into a chest. */
    @GameTest(template = AREA, timeoutTicks = 200)
    public static void conveyorPushesChipsOut(GameTestHelper helper) {
        CncLatheBlockEntity lathe = build(helper);
        BlockPos chest = rel(CncLathePart.CHIP_OUTLET).relative(CncLatheLayout.lengthDir(FRONT));
        helper.setBlock(chest, Blocks.CHEST);
        lathe.chipInv.setStackInSlot(0, new ItemStack(ModItems.IRON_SWARF.get(), 3));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.BELT_CONNECTOR.get()));
        lathe.use(CncLathePart.CHIP_TRAY, player.getMainHandItem(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(lathe.conveyorInstalled, "conveyor not installed");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "belt not consumed");
        helper.assertTrue(port(helper, CncLathePart.CHIP_OUTLET) != null, "chip outlet still closed");

        helper.succeedWhen(() -> {
            ChestBlockEntity box = helper.getBlockEntity(chest);
            helper.assertValueEqual(box.countItem(ModItems.IRON_SWARF.get()), 3, "swarf in chest");
        });
    }

    /** The door can't be opened mid-cycle. */
    @GameTest(template = AREA, timeoutTicks = 200)
    public static void doorIsInterlocked(GameTestHelper helper) {
        CncLatheBlockEntity lathe = build(helper);
        power(helper, 16); // slow, so the cycle lasts a while
        lathe.toolInv.setStackInSlot(0, new ItemStack(ModItems.CARBIDE_INSERT.get()));
        lathe.stockInv.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.succeedWhen(() -> {
            helper.assertValueEqual(lathe.phase, CncLatheBlockEntity.Phase.CUTTING, "phase");
            lathe.use(CncLathePart.CHUCK, ItemStack.EMPTY, player, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(lathe.stockInv.getStackInSlot(0).getCount(), 4, "stock pulled mid-cycle");
        });
    }

    /** The 5x7 mechanical crafting recipe loads and makes a CNC Lathe. */
    @GameTest(template = AREA)
    public static void recipeLoads(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager()
                .byKey(CreateMachining.asResource("mechanical_crafting/cnc_lathe"))
                .orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("CNC Lathe recipe missing"));
        helper.assertTrue(recipe.value().getResultItem(helper.getLevel().registryAccess()).is(ModItems.CNC_LATHE.get()),
                "recipe makes the wrong thing");
        helper.assertValueEqual(recipe.value().getIngredients().size(), 35, "ingredient slots");
        helper.succeed();
    }

    private static IItemHandler port(GameTestHelper helper, CncLathePart part) {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(rel(part)), null);
    }
}
