package com.holybuckets.structures;


import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.structures.config.HBStructuresModConfig;
import com.holybuckets.structures.config.StructuresTimeConfig;
import com.holybuckets.structures.core.StructureConceptManager;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;

/**
 * Main instance of the mod, initialize this class statically via commonClass
 * This class will init all major Manager instances and events for the mod
 */
public class StructuresOverTimeMain {
    private static boolean DEV_MODE = false;;
    private static StructuresTimeConfig CONFIG;
    public static StructuresOverTimeMain INSTANCE;

    public StructuresOverTimeMain()
    {
        super();
        INSTANCE = this;
        init();
        // LoggerProject.logInit( "001000", this.getClass().getName() ); // Uncomment if you have a logging system in place
    }

    private void init()
    {

        /*
        Proxy for external APIs which are platform dependent
        this.portalApi = (PortalApi) Balm.platformProxy()
            .withFabric("com.holybuckets.challengetemple.externalapi.FabricPortalApi")
            .withForge("com.holybuckets.challengetemple.externalapi.ForgePortalApi")
            .build();
            */

        //Events
        EventRegistrar registrar = EventRegistrar.getInstance();
        //ChallengeBlockBehavior.init(registrar);

        //Configs
        HBStructuresModConfig.init(registrar);

        //Managers
        StructureConceptManager.init(registrar);

        //register local events
        registrar.registerOnBeforeServerStarted(this::onServerStarting);

    }

    private void onServerStarting(ServerStartingEvent e) {
        CONFIG = Balm.getConfig().getActiveConfig(StructuresTimeConfig.class);
        //this.DEV_MODE = CONFIG.devMode;
        this.DEV_MODE = false;
    }


}
