package com.holybuckets.structures.networking;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.model.ManagedChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * Description: MessageUpdateBlockStates
 * Packet data for block state updates from server to client
 */
public class BlockStateUpdatesMessageHandler {

    /**
     * Create and fire the packet. There is an upper limit of 32KB per pack sent enforced by Minecraft.
     * In order to avoid this, we will send multiple packets in increments of 512 blocks.
     * @param world
     * @param updates
     * @return
     */
    static void createAndFire(LevelAccessor world, Map<BlockState, List<BlockPos>> updates)
    {
        //1. Create new instances of updates
        List< Map<BlockState, List<BlockPos>> > messages = new ArrayList<>();

        //2. Create map of blockState to ListIterator for each list
        Queue<Pair<BlockState, Iterator<BlockPos>>> iterators = new LinkedList<>();
        for (Map.Entry<BlockState, List<BlockPos>> entry : updates.entrySet()) {
            if(entry.getValue().size() == 0) continue;
            iterators.add(Pair.of(entry.getKey(), entry.getValue().listIterator()));
        }

        //3. for up to MAX_SIZE = 512 blocks, add to blockStates using iterators
        final int MAX_SIZE = 512;
        Pair<BlockState, Iterator<BlockPos>> current = iterators.poll();

        while(current != null)
        {
            Map<BlockState, List<BlockPos>> blockStateUpdates = new HashMap<>();
            for(int i = 0; i < MAX_SIZE; i++)
            {
                if( !current.getRight().hasNext() )
                    current = iterators.poll();

                if(current == null)
                    break;

                blockStateUpdates.putIfAbsent(current.getLeft(), new ArrayList<>());
                List<BlockPos> positions = blockStateUpdates.get(current.getLeft());
                positions.add(current.getRight().next());
            }

            if(blockStateUpdates.size() > 0)
                messages.add(blockStateUpdates);
        }


        BlockPos pos = updates.values().stream().findFirst().get().get(0);
        final int CHUNKS_RANGE = 40;
        List<ServerPlayer> playersInUpdateRange = HBUtil.PlayerUtil.getAllPlayersInBlockRange(pos, 16*CHUNKS_RANGE);
        for(Map<BlockState, List<BlockPos>> message : messages) {
            BlockStateUpdatesMessage packet = new BlockStateUpdatesMessage(world, message);
            playersInUpdateRange.stream().forEach(player -> HBUtil.NetworkUtil.serverSendToPlayer(player, packet));
        }

    }


    private static HashSet<Integer> collisionChecker = new HashSet<>();
    static void handle(Player player, BlockStateUpdatesMessage message) {
        if(player.level() != message.world) return;
        ManagedChunk.updateChunkBlockStates(message.world, message.blockStates);
    }

    /*
    private static void threadUpdateChunkBlocks( LevelAccessor world, Map<BlockState, List<BlockPos>> blocks) {
        boolean madeUpdates = false;
        BlockPos coords = blocks.values().stream().findFirst().get().get(0);
        int startTime = (int) System.currentTimeMillis();
        try {
            while (!madeUpdates) {
                if (GeneralConfig.getInstance().getServer() == null) return;
                madeUpdates = ManagedChunk.updateChunkBlockStates(world, blocks);
                sleep(10);
            }
            int endTime = (int) System.currentTimeMillis();
            //LoggerBase.logInfo(null, "011000", "Updated chunk blocks on client " +  coords + " TIME: " + (endTime - startTime) + "ms");
            LoggerBase.logInfo(null, "000003"," CLIENT Total Executed: " + POOL.getCompletedTaskCount());
        } catch (Exception e) {
            e.printStackTrace();
            LoggerBase.logError(null, "011001", "Error updating chunk blocks on client: " + coords );
        }


    }

     */

}
