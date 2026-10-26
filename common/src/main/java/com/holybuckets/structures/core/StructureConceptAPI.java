package com.holybuckets.structures.core;

import com.google.gson.JsonObject;
import com.holybuckets.structures.config.model.StructureConcept;
import com.holybuckets.structures.config.model.StructureConceptStage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.holybuckets.foundation.HBUtil.ChunkUtil.*;

/**
 * API class with standard utility methods regarding interacting with ManagedStructureConcept
 */
public class StructureConceptAPI {

    private Level level;
    StructureConceptManager manager;
    public StructureConceptAPI (Level level) {
        this.level = level;
        manager =StructureConceptManager.get(level);
    }

    @Nullable
    public ManagedStructureConceptChunk getNearestStructureChunk(BlockPos blockPos){
        ChunkPos closestChunk = null;
        ChunkPos center = new ChunkPos(blockPos);
        for(ChunkPos pos : manager.getManagedChunks().keySet() ){
            if(closestChunk ==null) closestChunk = pos;
            else if(chunkDist(center, pos) < chunkDist(center, closestChunk)){
                closestChunk = pos;
            }
        }

        return manager.getManagedChunk(closestChunk);
    }

    public ManagedStructureConceptChunk getStructureAtChunk(String chunkPos) {
        return manager.getManagedChunk(getChunkPos(chunkPos));
    }

    @Nullable
    public ManagedStructureConceptChunk getStructureAtChunk(ChunkPos chunkPos) {
        return manager.getManagedChunk(chunkPos);
    }


    public boolean forceUpgradeStructure(ChunkPos chunkPos)
    {
        var managedChunk = manager.getManagedChunk(chunkPos);
        if(managedChunk == null) return false;
        int newStage = managedChunk.getstage()+1;
        var concept = managedChunk.getStructureConcept();
        StructureConceptManager.pendingStageUpgrades.put(concept, newStage);
        StructureConceptManager.conceptStages.put(concept, newStage);
        manager.getManagedChunk(chunkPos).triggerStructureUpgrade(newStage, true);
        return true;
    }


    /**
     * Advances one managed structure to its next stage. Intended for custom upgrade
     * stages driven by another mod. Unlike forceUpgradeStructure this does not move the
     * concept's global stage, so only the structure at this chunk changes.
     *
     * @return true if the upgrade was queued
     */
    public boolean triggerCustomUpgrade(ChunkPos chunkPos) {
        return triggerCustomUpgrade(chunkPos, false);
    }

    /**
     * @param force skip the concept's upgrade rejection checks
     */
    public boolean triggerCustomUpgrade(ChunkPos chunkPos, boolean force)
    {
        if(manager == null || chunkPos == null) return false;
        var managedChunk = manager.getManagedChunk(chunkPos);
        if(managedChunk == null) return false;

        StructureConcept concept = managedChunk.getStructureConcept();
        if(concept == null) return false;

        int newStage = managedChunk.getstage() + 1;
        if(newStage > concept.getMaxStage() && concept.getCycleStage() == -1) return false;

        managedChunk.triggerStructureUpgrade(newStage, force);
        return true;
    }

    /** True if the next stage for this structure has no automatic trigger. */
    public boolean isCustomUpgradeStage(ChunkPos chunkPos) {
        if(manager == null || chunkPos == null) return false;
        var managedChunk = manager.getManagedChunk(chunkPos);
        if(managedChunk == null) return false;
        StructureConcept concept = managedChunk.getStructureConcept();
        if(concept == null) return false;
        return concept.isCustomUpgradeStage(managedChunk.getstage() + 1);
    }

    public List<ManagedStructureConceptChunk> getStructuresNearPoint(BlockPos pos) {
        return getStructuresNearPoint(pos, null, 5);
    }
    //implement getStructuresNearPoint(BlockPos pos, int limit)
    public List<ManagedStructureConceptChunk> getStructuresNearPoint(BlockPos pos, String conceptId, int limit)
    {
        List<ManagedStructureConceptChunk> nearbyStructures = new ArrayList<>();
        ChunkPos center = new ChunkPos(pos);
        //sort manager.getManagedChunks().values() by distance to center
        List<ChunkPos> sortedChunks = new ArrayList<>(manager.getManagedChunks().keySet());
        Collections.sort(sortedChunks, (a, b) -> {
            double distA = chunkDist(center, a);
            double distB = chunkDist(center, b);
            return Double.compare(distA, distB);
        });
        sortedChunks.stream().filter(cp -> {
            var chunk = manager.getManagedChunk(cp);
            if(conceptId == null) return true;
            return chunk.getStructureConcept().getStructureConceptId().equals(conceptId);
        })
        .forEach( cp -> nearbyStructures.add(manager.getManagedChunk(cp)) );

        return nearbyStructures.subList(0, Math.min(limit, nearbyStructures.size()));
    }

    public JsonObject getConfig(String conceptId, boolean showAllStages) {
        StructureConcept concept = StructureConceptManager.MOD_CONFIG.getStructureConcept(conceptId);
        if(concept == null) return null;

        JsonObject json = new JsonObject();
        //Add conceptId, stage, and all config values to json
        json.addProperty("conceptId", conceptId);
        json.addProperty("currentStage", StructureConceptManager.conceptStages.get(concept));

        json.addProperty("totalStages", concept.getStageCount());
        json.addProperty("sourceStructureId", concept.getSourceStructureId());
        json.addProperty("","");

        if(showAllStages)
        {
            List<StructureConceptStage> stages = concept.getStages();
            for(var stage : stages) {
                json.addProperty(""+stage.getStage(), "Structure: " +stage.getStructureId());
            }
        }

        return json;
    }
}
