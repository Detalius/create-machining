package com.jessehandke.createmachining.content.cnc;

import com.jessehandke.createmachining.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The CNC Lathe as an item. Placing it builds the whole 3x2x2 machine at once: the block you
 * aim at becomes the front-left (headstock) corner, and the machine extends to your right,
 * away from you, and up. The front (door) faces you.
 */
public class CncLatheItem extends BlockItem {

    public CncLatheItem(Block controller, Properties properties) {
        super(controller, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        Direction front = ctx.getHorizontalDirection().getOpposite(); // face the player
        BlockPos origin = ctx.getClickedPos();

        if (!ctx.canPlace() || !canBuild(level, origin, front)) {
            if (player != null && level.isClientSide)
                player.displayClientMessage(Component.translatable("create_machining.cnc_lathe.no_room"), true);
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            build(level, origin, front);
            level.playSound(null, origin, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 1, 0.8f);
        }

        if (player == null || !player.getAbilities().instabuild)
            ctx.getItemInHand().shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Are all 12 spots free (air, grass, water, ...)? */
    public static boolean canBuild(Level level, BlockPos origin, Direction front) {
        for (CncLathePart part : CncLathePart.values()) {
            BlockPos pos = CncLatheLayout.posOf(part, origin, front);
            if (!level.isInWorldBounds(pos) || !level.getBlockState(pos).canBeReplaced())
                return false;
        }
        return true;
    }

    /** Place the whole machine. The controller first, so the other blocks have something to point at. */
    public static void build(Level level, BlockPos origin, Direction front) {
        BlockState controller = ModBlocks.CNC_LATHE.get().defaultBlockState()
                .setValue(CncLatheBlock.HORIZONTAL_FACING, front);
        level.setBlock(CncLatheLayout.posOf(CncLathePart.CONTROLLER, origin, front), controller, 3);
        for (CncLathePart part : CncLathePart.values()) {
            if (part == CncLathePart.CONTROLLER)
                continue;
            BlockState structural = ModBlocks.CNC_LATHE_STRUCTURAL.get().defaultBlockState()
                    .setValue(CncLatheStructuralBlock.FACING, front)
                    .setValue(CncLatheStructuralBlock.PART, part);
            level.setBlock(CncLatheLayout.posOf(part, origin, front), structural, 3);
        }
    }
}
