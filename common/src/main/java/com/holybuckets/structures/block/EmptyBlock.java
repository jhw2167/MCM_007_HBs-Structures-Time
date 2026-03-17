package com.holybuckets.structures.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A completely empty block class. Used for debugging and in certain algorithms
 */
public class EmptyBlock extends Block {

    protected EmptyBlock(Properties $$0) {
        super($$0);
    }

    public RenderShape getRenderShape(BlockState $$0) {
        return RenderShape.MODEL;
    }

    public VoxelShape getShape(BlockState $$0, BlockGetter $$1, BlockPos $$2, CollisionContext $$3) {
        return Shapes.empty();
    }

}
