package com.holybuckets.structures.networking;

import com.holybuckets.structures.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * Description: MessageUpdateBlockStates
 * Packet data for block state updates from server to client
 */
public class BlockStateUpdatesMessage implements CustomPacketPayload {

    public static final String LOCATION = "block_state_updates";

    public static final CustomPacketPayload.Type<BlockStateUpdatesMessage> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, LOCATION));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlockStateUpdatesMessage> STREAM_CODEC =
        CustomPacketPayload.codec(Codecs::encodeBlockStateUpdates, Codecs::decodeBlockStateUpdates);

    private static final Integer BLOCKPOS_SIZE = 48;    //16 bytes per number x3 = 48 bytes
    LevelAccessor world;
    Map<BlockState, List<BlockPos>> blockStates;

    BlockStateUpdatesMessage(LevelAccessor level, Map<BlockState, List<BlockPos>> blocks) {
        this.world = level;
        this.blockStates = blocks;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void createAndFire(LevelAccessor world, Map<BlockState, List<BlockPos>> updates) {
        BlockStateUpdatesMessageHandler.createAndFire(world, updates);
    }
}
