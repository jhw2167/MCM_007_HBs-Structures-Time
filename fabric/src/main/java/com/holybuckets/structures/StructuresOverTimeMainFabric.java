package com.holybuckets.structures;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.EmptyLoadContext;
import net.fabricmc.api.ModInitializer;

public class StructuresOverTimeMainFabric implements ModInitializer {
    
    @Override
    public void onInitialize() {

        Balm.initialize(Constants.MOD_ID, EmptyLoadContext.INSTANCE, CommonClass::init);
    }
}
