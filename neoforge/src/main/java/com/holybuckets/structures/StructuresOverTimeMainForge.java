package com.holybuckets.structures;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.neoforge.NeoForgeLoadContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class StructuresOverTimeMainForge {

    public StructuresOverTimeMainForge(IEventBus modEventBus) {
        super();
        final var context = new NeoForgeLoadContext(modEventBus);
        Balm.initialize(Constants.MOD_ID, context, CommonClass::init);
    }

}
