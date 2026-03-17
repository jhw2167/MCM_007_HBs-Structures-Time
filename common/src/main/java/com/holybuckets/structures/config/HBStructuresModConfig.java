package com.holybuckets.structures.config;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.structures.LoggerProject;
import com.holybuckets.structures.config.model.StructureConcept;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;
import net.blay09.mods.balm.api.event.server.ServerStoppedEvent;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;

/**
 * Class: HBStructuresModConfig
 * Description: Singleton configuration for the HBs Structures Over Time mod.
 */
public class HBStructuresModConfig {

    private static final String CLASS_ID = "012";
    private static HBStructuresModConfig INSTANCE;
    private StructureConceptJsonConfig structureConceptConfig;

    public static HBStructuresModConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new HBStructuresModConfig();
        }
        return INSTANCE;
    }

    private HBStructuresModConfig() {
        this.structureConceptConfig = StructureConceptJsonConfig.DEFAULT_CONFIG;
    }


    public static void init(EventRegistrar registrar) {
        INSTANCE = HBStructuresModConfig.getInstance();
        // Lowest priority so all other systems are registered before config loads
        registrar.registerOnBeforeServerStarted(HBStructuresModConfig::onBeforeServerStarted, EventPriority.High);
        registrar.registerOnServerStopped(HBStructuresModConfig::onServerStopped, EventPriority.Low);
    }


    /** Returns the loaded (or default) StructureConceptJsonConfig. Never null. */
    public StructureConceptJsonConfig getStructureConceptConfig() {
        return structureConceptConfig;
    }

    /**
     * Convenience: returns all parsed StructureConcept entries.
     * Returns the default config's concepts if config has not been loaded yet.
     */
    public Collection<StructureConcept> getConcepts() {
        return structureConceptConfig.getAllConcepts();
    }

    /**
     * Convenience: looks up a single StructureConcept by its id.
     * Returns null if not found.
     */
    @Nullable
    public StructureConcept getConcept(String conceptId) {
        return structureConceptConfig.getConcept(conceptId);
    }

    private void onBeforeServerStarted() {
        StructuresTimeConfig activeConfig = Balm.getConfig().getActiveConfig(StructuresTimeConfig.class);
        String configPath = activeConfig.structureRulesConfig;

        File configFile        = new File(configPath);
        File defaultConfigFile = new File(StructureConceptJsonConfig.DEF_CONFIG_FILE_PATH);

        LoggerProject.logInfo(CLASS_ID + "000",
            "Loading structure concept config from: " + configFile.getAbsolutePath());

        String json = HBUtil.FileIO.loadJsonConfigs(
            configFile,
            defaultConfigFile,
            StructureConceptJsonConfig.DEFAULT_CONFIG
        );

        this.structureConceptConfig = new StructureConceptJsonConfig(json);

        LoggerProject.logInfo(CLASS_ID + "001",
            "Loaded " + structureConceptConfig.size() + " structure concept(s): "
            + structureConceptConfig.getAllConceptIds());
    }

    private void onServerStopped() {
        this.structureConceptConfig = StructureConceptJsonConfig.DEFAULT_CONFIG;
        INSTANCE = null;
    }

    // -----------------------------------------------------------------------
    // Static event adapters
    // -----------------------------------------------------------------------

    private static void onBeforeServerStarted(ServerStartingEvent event) {
        getInstance().onBeforeServerStarted();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        getInstance().onServerStopped();
    }
}
