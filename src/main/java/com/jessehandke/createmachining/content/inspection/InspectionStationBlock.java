package com.jessehandke.createmachining.content.inspection;

import com.jessehandke.createmachining.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A granite surface plate on a stand. No power needed: inspection is about time, not torque.
 */
public class InspectionStationBlock extends Block implements IBE<InspectionStationBlockEntity> {
    // Hitbox: full width, 14 px tall to match the model (Block.box works in pixels, 16 = one block)
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);

    public InspectionStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        return onBlockEntityUseItemOn(level, pos, station -> {
            if (level.isClientSide)
                return stack.isEmpty() || InspectionStationBlockEntity.isInspectable(stack)
                        ? ItemInteractionResult.SUCCESS
                        : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

            if (!stack.isEmpty()) {
                ItemStack remainder = station.insertPart(stack);
                if (remainder.getCount() == stack.getCount())
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                player.setItemInHand(hand, remainder);
                return ItemInteractionResult.SUCCESS;
            }
            station.unloadInto(player);
            return ItemInteractionResult.SUCCESS;
        });
    }

    /** Block broken or replaced: let the block entity drop its contents. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<InspectionStationBlockEntity> getBlockEntityClass() {
        return InspectionStationBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends InspectionStationBlockEntity> getBlockEntityType() {
        return ModBlockEntities.INSPECTION_STATION.get();
    }
}
