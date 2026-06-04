package com.holybuckets.structures.config;

import com.google.gson.stream.MalformedJsonException;
import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.structures.Constants;
import com.holybuckets.structures.LoggerProject;
import com.holybuckets.structures.config.model.StructureConcept;
import com.holybuckets.structures.config.model.StructureConcept.StructureConceptStage;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;
import net.blay09.mods.balm.api.event.server.ServerStoppedEvent;
import net.minecraft.core.Holder;
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
    private Set<ResourceLocation> structureBlacklist;
    private Map<ResourceLocation, StructureConcept> activeStructureConcepts;
    private Registry<Structure> registry;

    public static ModConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
        }
        return INSTANCE;
    }

    private ModConfig() {
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

        EMPTY_STRUCT = registry.getOptional(EMPTY_STRUCTURE_LOC).orElse(null);
        SKIP_STRUCT = registry.getOptional(SKIP_STRUCTURE_LOC).orElse(null);

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
                String stageStructId = stage.getStructureId();
                ResourceLocation stageHolder = getStructure(registry, stageStructId);
                if (stageHolder == null) {
                    LoggerProject.logError(CLASS_ID + "012",
                        "StructureConcept '" + conceptId + "' stage " + stage.getStage()
                        + ": structureId '" + stageStructId + "' not found in registry, removing stage");
                    stagesToRemove.add(stage.getStage());
                } else {
                    stage.setStructureLoc(stageHolder);
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
        if(resourceLocationStr == null || resourceLocationStr.isEmpty()) return SKIP_STRUCTURE_LOC;
        if(resourceLocationStr.equalsIgnoreCase("skip")) return EMPTY_STRUCTURE_LOC;
        if(resourceLocationStr.equalsIgnoreCase("empty")) return EMPTY_STRUCTURE_LOC;

        ResourceLocation loc = new ResourceLocation(resourceLocationStr);
        Structure s = registry.getOptional(loc).orElse(null);
        return (s != null) ? loc : null;
    }

    public static final ResourceLocation EMPTY_STRUCTURE_LOC = new ResourceLocation(Constants.MOD_ID, "empty");
    public static final ResourceLocation SKIP_STRUCTURE_LOC = new ResourceLocation(Constants.MOD_ID, "skip");

    /** Default structure instance indicating the previous structure should be removed **/
    public static Structure EMPTY_STRUCT;

    /**utility structure that indicates the concept stage should be skipped, but don't touch existing sturcture**/
    public static Structure SKIP_STRUCT;

    private void onBeforeServerStarted()
    {
        MinecraftServer server = GeneralConfig.getInstance().getServer();
        StructuresTimeConfig activeConfig = Balm.getConfig().getActiveConfig(StructuresTimeConfig.class);
        String configPath = activeConfig.structureProgressConfig;

        File configFile        = new File(configPath);
        File defaultConfigFile = new File(StructureConceptJsonConfig.DEF_CONFIG_FILE_PATH);

        LoggerProject.logInfo("012000",
            "Loading structure concept config from: " + configFile.getAbsolutePath());

        String json = HBUtil.FileIO.loadJsonConfigs(
            configFile,
            defaultConfigFile,
            StructureConceptJsonConfig.buildDefaultConfig()
        );
        try {
            this.structureConceptConfig = new StructureConceptJsonConfig(json);
        } catch (RuntimeException e) {
            String msg = String.format("Failed to parse user structure concept config JSON: %s. Error:\n %s.\n\n Default configs will be applied",
                 configFile.getAbsolutePath(), e.getCause() );
            LoggerProject.logError("012002", msg);
            this.structureConceptConfig = new StructureConceptJsonConfig(
                StructureConceptJsonConfig.buildDefaultConfig().serialize()
                );

        }

        LoggerProject.logInfo("012001", "Parsed " + structureConceptConfig.size() + " structure concept(s): "
            + structureConceptConfig.getAllConceptIds());

        //Parse all blackListed structures into resourceLocations
        this.registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        this.structureBlacklist = new HashSet<>();
        for(String blacklistedStruct : activeConfig.naturalStructureSpawnsBlacklist) {
            ResourceLocation loc = getStructure(registry, blacklistedStruct);
            if(loc != null) {
                structureBlacklist.add(loc);
            } else {
                LoggerProject.logError(CLASS_ID + "015", "Failed to blacklisted structure '" + blacklistedStruct);
            }
        }


        // Resolve all string ids to registry Holders and prune invalid entries
        initStructures(server);
    }

    private void onServerStopped() {
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
        if(structureLoc == null) return false;
        return activeStructureConcepts.containsKey(structureLoc);
    }

    public ResourceLocation loc(Structure s) {
        if(s == null) return null;
        return registry.getKey(s);
    }

    public Structure structure(ResourceLocation loc) {
        return registry.get(loc);
    }

    public Holder.Reference reference(Structure structure) {
        if(structure == null) return null;
        return registry.getHolderOrThrow(registry.getResourceKey(structure).orElseThrow());
    }

    /**
     * Tests if a structure is our custom "empty" or "skip" structure
     * returns true on null resource location
     * @param loc
     * @return
     */
    public boolean isEmptyStructure(ResourceLocation loc) {
        if(loc==null) return true;
        return loc.equals(EMPTY_STRUCTURE_LOC);
    }

    public boolean isEmptyStructure(Structure s) {
        if(s==null) return true;
        return s.equals(EMPTY_STRUCT);
    }


    public boolean isSkipStructure(ResourceLocation loc) {
        if(loc==null) return false;
        return loc.equals(SKIP_STRUCTURE_LOC);
    }

        public boolean isSkipStructure(Structure s) {
            if(s==null) return false;
            return s.equals(SKIP_STRUCT);
        }


//implement isBlacklisted against loc and structure
    public boolean isBlacklisted(ResourceLocation loc) {
        if(loc==null) return false;
        return structureBlacklist.contains(loc);
    }

        public boolean isBlacklisted(Structure s) {
            return  isBlacklisted(loc(s));
        }
}
