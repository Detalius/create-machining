package com.jessehandke.createmachining.content.lathe;

import com.jessehandke.createmachining.registry.ModBlockEntities;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The lathe as a block type. There's ONE CncLatheBlock object for the whole game, shared by every
 * lathe placed in the world, so it holds no per-machine data. Per-machine state (inventory,
 * cycle timer) lives in CncLatheBlockEntity.
 *
 * HorizontalKineticBlock is Create's base for machines that face one of 4 directions and take
 * rotational power. IBE<...> is a Create helper that links this block to its block entity.
 */
public class CncLatheBlock extends HorizontalKineticBlock implements IBE<CncLatheBlockEntity> {

    public CncLatheBlock(Properties properties) {
        super(properties);
    }

    /** The spindle turns around the axis the machine faces along. */
    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    /** Power comes in through the back of the headstock only. */
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(HORIZONTAL_FACING).getOpposite();
    }

    /**
     * Right-click behaviour:
     *  - holding stock or a carbide insert: load it
     *  - empty hand: unload finished parts (or the stock, or the insert, in that order)
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        // onBlockEntityUseItemOn finds this block's CncLatheBlockEntity and runs our lambda on it.
        // "lathe -> ..." is a lambda: an inline unnamed function, similar to a C++ [](auto lathe){...}
        return onBlockEntityUseItemOn(level, pos, lathe -> {
            // The client only predicts the outcome (so your arm swings); the server does the real work.
            if (level.isClientSide)
                return stack.isEmpty() || lathe.accepts(stack)
                        ? ItemInteractionResult.SUCCESS
                        : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

            if (!stack.isEmpty()) {
                ItemStack remainder = lathe.insertItem(stack);
                if (remainder.getCount() == stack.getCount())
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // not stock or tooling
                player.setItemInHand(hand, remainder);
                return ItemInteractionResult.SUCCESS;
            }
            lathe.unloadInto(player);
            return ItemInteractionResult.SUCCESS;
        });
    }

    @Override
    public Class<CncLatheBlockEntity> getBlockEntityClass() {
        return CncLatheBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CncLatheBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CNC_LATHE.get();
    }

    /** Mobs shouldn't try to walk through it. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
