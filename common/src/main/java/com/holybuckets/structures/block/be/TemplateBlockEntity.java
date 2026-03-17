package com.holybuckets.structures.block.be;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TemplateBlockEntity extends ChestBlockEntity implements LidBlockEntity {
    public TemplateBlockEntity(BlockPos pos, BlockState state) {
        super( ModBlockEntities.templateChest.get(), pos, state);
    }
}
