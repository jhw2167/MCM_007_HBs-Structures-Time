package com.holybuckets.structures.core;

import com.google.gson.JsonObject;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.structures.config.model.StructureConcept;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

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


    public JsonObject getConfig(String conceptId, boolean showAllStages) {
        StructureConcept concept = StructureConceptManager.MOD_CONFIG.getStructureConcept(conceptId);

        JsonObject json = new JsonObject();
        //Add conceptId, stage, and all config values to json
        json.addProperty("conceptId", conceptId);
        json.addProperty("currentStage", StructureConceptManager.conceptStages.get(concept));

        json.addProperty("totalStages", concept.getStageCount());
        json.addProperty("sourceStructureId", concept.getSourceStructureId());

        return json;
    }
}
