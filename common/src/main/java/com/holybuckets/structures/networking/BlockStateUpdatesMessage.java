package com.holybuckets.structures.networking;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * Description: MessageUpdateBlockStates
 * Packet data for block state updates from server to client
 */
public class BlockStateUpdatesMessage {

    public static final String LOCATION = "block_state_updates";
    private static final Integer BLOCKPOS_SIZE = 48;    //16 bytes per number x3 = 48 bytes
    LevelAccessor world;
    Map<BlockState, List<BlockPos>> blockStates;

    BlockStateUpdatesMessage(LevelAccessor level, Map<BlockState, List<BlockPos>> blocks) {
        this.world = level;
        this.blockStates = blocks;
    }

    public static void createAndFire(LevelAccessor world, Map<BlockState, List<BlockPos>> updates) {
        BlockStateUpdatesMessageHandler.createAndFire(world, updates);
    }
}
