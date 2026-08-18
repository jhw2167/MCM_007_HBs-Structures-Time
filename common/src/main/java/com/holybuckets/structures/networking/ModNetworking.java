package com.holybuckets.structures.networking;

import net.blay09.mods.balm.api.network.BalmNetworking;

public class ModNetworking {

    public static void init(BalmNetworking networking) {
        Handlers.init();

        networking.registerClientboundPacket(
            BlockStateUpdatesMessage.TYPE,
            BlockStateUpdatesMessage.class,
            BlockStateUpdatesMessage.STREAM_CODEC,
            Handlers::handleBlockStateUpdates
        );
    }
}
