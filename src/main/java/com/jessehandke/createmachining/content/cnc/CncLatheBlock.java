package com.jessehandke.createmachining.content.cnc;

import com.jessehandke.createmachining.registry.ModBlockEntities;
import com.jessehandke.createmachining.registry.ModBlocks;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The CNC Lathe's controller: the drive block at the bottom back of the headstock. It's the only
 * block with real logic (the block entity); the other 11 positions are CncLatheStructuralBlocks
 * that point back here.
 *
 * HORIZONTAL_FACING is the direction the FRONT of the machine (the door) faces.
 */
public class CncLatheBlock extends HorizontalKineticBlock implements IBE<CncLatheBlockEntity> {

    public CncLatheBlock(Properties properties) {
        super(properties);
    }

    /** The drive shaft runs front-to-back. */
    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    /** Power comes in through the back of the machine. */
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(HORIZONTAL_FACING).getOpposite();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return onBlockEntityUseItemOn(level, pos,
                lathe -> lathe.use(CncLathePart.CONTROLLER, stack, player, hand));
    }

    /** When the controller goes, the whole machine goes. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving); // drops contents (Create's IBE logic)
        if (state.is(newState.getBlock()))
            return; // just a state change (e.g. rotation update), not a removal
        Direction front = state.getValue(HORIZONTAL_FACING);
        BlockPos origin = CncLatheLayout.origin(pos, front);
        for (CncLathePart part : CncLathePart.values()) {
            if (part == CncLathePart.CONTROLLER)
                continue;
            BlockPos partPos = CncLatheLayout.posOf(part, origin, front);
            if (level.getBlockState(partPos).is(ModBlocks.CNC_LATHE_STRUCTURAL.get()))
                level.setBlock(partPos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public Class<CncLatheBlockEntity> getBlockEntityClass() {
        return CncLatheBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CncLatheBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CNC_LATHE.get();
    }
}
