package com.holybuckets.structures.config;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.structures.LoggerProject;
import com.holybuckets.structures.config.model.StructureConcept;
import com.holybuckets.structures.config.model.StructureConcept.StructureConceptStage;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;
import net.blay09.mods.balm.api.event.server.ServerStoppedEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.Structure;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;

/**
 * Class: HBStructuresModConfig
 * Description: Singleton configuration for the HBs Structures Over Time mod.
 *
 * On server start, loads the JSON config and resolves all structureId strings
 * to ResourceLocation references via the server's structure registry. Any
 * concepts or stages whose structureIds fail to resolve are pruned and logged.
 */
public class ModConfig {

    private static final String CLASS_ID = "012";
    private static ModConfig INSTANCE;
    private StructureConceptJsonConfig structureConceptConfig;
    private Map<ResourceLocation, StructureConcept> activeStructureConcepts;

    public static ModConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
        }
        return INSTANCE;
    }

    private ModConfig() {
        this.structureConceptConfig = StructureConceptJsonConfig.DEFAULT_CONFIG;
        this.activeStructureConcepts = new HashMap<>();
    }


    public static void init(EventRegistrar registrar) {
        INSTANCE = ModConfig.getInstance();
        registrar.registerOnBeforeServerStarted(ModConfig::onBeforeServerStarted, EventPriority.High);
        registrar.registerOnServerStopped(ModConfig::onServerStopped, EventPriority.Low);
    }


    /** Returns the loaded (or default) StructureConceptJsonConfig. Never null. */
    public StructureConceptJsonConfig getStructureConceptConfig() {
        return structureConceptConfig;
    }

    public Collection<StructureConcept> getConcepts() {
        return structureConceptConfig.getAllConcepts();
    }

    @Nullable
    public StructureConcept getStructureConcept(String conceptId) {
        return structureConceptConfig.getConcept(conceptId);
    }

    @Nullable
    public StructureConcept getStructureConcept(ResourceLocation loc) {
        return activeStructureConcepts.get(loc);
    }

    private void initStructures(MinecraftServer server)
    {
        Registry<Structure> registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);

        for (StructureConcept concept : structureConceptConfig.getAllConcepts())
        {
            String conceptId = concept.getStructureConceptId();
            String sourceId = concept.getSourceStructureId();

            if (sourceId == null || sourceId.isEmpty()) {
                LoggerProject.logError(CLASS_ID + "010", "StructureConcept '" + conceptId + "' has no sourceStructureId, removing");
                continue;
            }

            ResourceLocation structureLoc = getStructure(registry, sourceId);
            if (structureLoc == null) {
                LoggerProject.logError(CLASS_ID + "011",
                    "StructureConcept '" + conceptId + "': sourceStructureId '"
                    + sourceId + "' not found in registry, removing concept");
                continue;
            }
            concept.setSourceStructure(structureLoc);

            // Resolve each stage's structureId → structureHolder
            List<Integer> stagesToRemove = new ArrayList<>();
            for (StructureConceptStage stage : concept.getStages())
            {
                if (stage.isEmpty()) continue;

                String stageStructId = stage.getStructureId();
                ResourceLocation stageHolder = getStructure(registry, stageStructId);
                if (stageHolder == null) {
                    LoggerProject.logError(CLASS_ID + "012",
                        "StructureConcept '" + conceptId + "' stage " + stage.getStage()
                        + ": structureId '" + stageStructId + "' not found in registry, removing stage");
                    stagesToRemove.add(stage.getStage());
                } else {
                    stage.setStructureHolder(stageHolder);
                }
            }

            for (int stageNum : stagesToRemove) {
                concept.removeStage(stageNum);
            }

            // If all stages were pruned, remove the concept entirely
            if (concept.getStages().isEmpty()) {
                LoggerProject.logError(CLASS_ID + "013",
                "StructureConcept '" + conceptId + "' has no valid stages after resolution, removing");
            }

            activeStructureConcepts.put(concept.getSourceStructure(), concept);
        }

        LoggerProject.logInfo(CLASS_ID + "014",
            "Registry resolution complete: " + structureConceptConfig.size()
            + " concept(s) with valid holders: " + structureConceptConfig.getAllConceptIds());
    }

    /**
     * Looks up a ResourceLocation string in the structure registry and returns its Holder,
     * or null if not found.
     */
    @Nullable
    private static ResourceLocation getStructure(Registry<Structure> registry, String resourceLocationStr) {
        ResourceLocation loc = new ResourceLocation(resourceLocationStr);
        Structure s = registry.getOptional(loc).orElse(null);
        return (s != null) ? loc : null;
    }

    private void onBeforeServerStarted() {
        MinecraftServer server = GeneralConfig.getInstance().getServer();
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
            "Parsed " + structureConceptConfig.size() + " structure concept(s): "
            + structureConceptConfig.getAllConceptIds());

        // Resolve all string ids to registry Holders and prune invalid entries
        initStructures(server);
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

    public boolean isActiveStructure(ResourceLocation structureLoc) {
        return activeStructureConcepts.containsKey(structureLoc);
    }
}
