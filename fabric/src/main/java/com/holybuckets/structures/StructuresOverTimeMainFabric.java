package com.holybuckets.structures;

import net.blay09.mods.balm.api.Balm;
import net.fabricmc.api.ModInitializer;

//YOU NEED TO UPDATE NAME OF MAIN CLASS IN fabric.mod.json
//Use mod_id of other mods to add them in depends section, ensures they are loaded first
public class StructuresOverTimeMainFabric implements ModInitializer {
    
    @Override
    public void onInitialize() {
        
        // This method is invoked by the Fabric mod loader when it is ready
        // to load your mod. You can access Fabric and Common code in this
        // project.

        // Use Fabric to bootstrap the Common mod.
        Balm.initialize(Constants.MOD_ID, CommonClass::init);
    }
}
