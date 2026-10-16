package com.jessehandke.createmachining.content.cnc;

import org.jetbrains.annotations.Nullable;

import com.jessehandke.createmachining.registry.ModBlocks;
import com.jessehandke.createmachining.registry.ModItems;
import com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Every CNC Lathe position except the controller. It has no logic of its own: it knows which
 * part of the machine it is (PART) and which way the machine faces (FACING), which is enough to
 * find the controller and pass everything to it. Same idea as Create's Large Water Wheel.
 *
 * IProxyHoveringInformation tells Create's goggles "show the controller's info when looking here".
 */
public class CncLatheStructuralBlock extends Block implements IProxyHoveringInformation {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /** Which part of the machine this is. The controller isn't allowed: it's a different block. */
    public static final EnumProperty<CncLathePart> PART =
            EnumProperty.create("part", CncLathePart.class, p -> p != CncLathePart.CONTROLLER);

    public CncLatheStructuralBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(PART, CncLathePart.CHUCK));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    public static BlockPos controllerPos(BlockPos pos, BlockState state) {
        return CncLatheLayout.controllerFrom(pos, state.getValue(PART), state.getValue(FACING));
    }

    /** The controller's block entity, or null if the machine is broken/incomplete. */
    @Nullable
    public static CncLatheBlockEntity controller(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(controllerPos(pos, state)) instanceof CncLatheBlockEntity lathe ? lathe : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        CncLatheBlockEntity lathe = controller(level, pos, state);
        if (lathe == null)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return lathe.use(state.getValue(PART), stack, player, hand);
    }

    /** Creative players break the machine without getting the item back, like any other block. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player.isCreative()) {
            BlockPos controller = controllerPos(pos, state);
            if (level.getBlockState(controller).is(ModBlocks.CNC_LATHE.get()))
                level.destroyBlock(controller, false);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Breaking any piece breaks the controller, which drops the machine and takes the rest down. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
        if (state.is(newState.getBlock()))
            return;
        BlockPos controller = controllerPos(pos, state);
        if (level.getBlockState(controller).is(ModBlocks.CNC_LATHE.get()))
            level.destroyBlock(controller, true);
    }

    /** Middle-click (pick block) gives the whole machine. */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(ModItems.CNC_LATHE.get());
    }

    @Override
    public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
        return controllerPos(pos, state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
