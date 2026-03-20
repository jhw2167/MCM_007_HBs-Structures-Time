package com.holybuckets.structures.config.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Class: StructureConcept
 * Description: Represents a single structure concept configuration entry.
 * A concept defines a progression of Minecraft structures that can evolve
 * through stages over time (e.g. a witch hut → village → pillager outpost).
 *
 * sourceStructureId identifies which vanilla/modded structure triggers this
 * concept during worldgen (e.g. "minecraft:village_plains"). When that
 * structure is intercepted in tryGenerateStructure, the chunk is flagged
 * as belonging to this concept.
 *
 * Stages are indexed from 1 (canvas0 has no structures; canvas1 holds stage 1, etc.).
 */
public class StructureConcept {

    public boolean hasStructure(ResourceLocation loc) {
        if (loc == null) return false;
        for (StructureConceptStage stage : stages) {
            if (stage.is(loc)) return true;
        }
        return false;
    }

    public static class StructureConceptStage {

        private final int stage;
        private final String structureId;
        private ResourceLocation structureLoc;

        public StructureConceptStage(int stage, String structureId) {
            this.stage = stage;
            this.structureId = (structureId == null) ? "" : structureId;
            this.structureLoc = null;
        }

        // -- Getters --

        public int getStage() {
            return stage;
        }

        public String getStructureId() {
            return structureId;
        }

        @Nullable
        public ResourceLocation getStructureLoc() {
            return structureLoc;
        }

        /** Returns true if this stage has an actual structure to place. */
        public boolean hasStructure() {
            return !structureId.isEmpty() && !structureId.equalsIgnoreCase("empty");
        }

        public boolean is(ResourceLocation s) {
            return structureLoc != null && s != null && structureLoc.equals(s);
        }

        /** Returns true if this stage explicitly removes / leaves empty. */
        public boolean isEmpty() {
            return structureId.isEmpty() || structureId.equalsIgnoreCase("empty");
        }

        // -- Registry resolution --

        /** Set the resolved Holder after registry lookup in ModConfig. */
        public void setStructureLoc(ResourceLocation holder) {
            this.structureLoc = holder;
        }

        // -- Serialization --

        public JsonObject serialize() {
            JsonObject obj = new JsonObject();
            obj.addProperty("stage", stage);
            obj.addProperty("structureId", structureId);
            return obj;
        }

        public static StructureConceptStage deserialize(JsonObject obj) {
            int stage       = obj.has("stage")       ? obj.get("stage").getAsInt()         : 1;
            String structId = obj.has("structureId") ? obj.get("structureId").getAsString() : "";
            return new StructureConceptStage(stage, structId);
        }
    }


    private final String structureConceptId;
    private final String sourceStructureId;
    private final String comments;
    private final List<StructureConceptStage> stages;

    private ResourceLocation sourceStructure;

    public StructureConcept(String structureConceptId, String sourceStructureId,
                            String comments, List<StructureConceptStage> stages) {
        this.structureConceptId = structureConceptId;
        this.sourceStructureId  = (sourceStructureId == null) ? "" : sourceStructureId;
        this.comments           = (comments == null) ? "" : comments;
        this.stages             = (stages == null) ? new ArrayList<>() : new ArrayList<>(stages);
        this.sourceStructure    = null;
    }


    public String getStructureConceptId() {
        return structureConceptId;
    }

    public String getSourceStructureId() {
        return sourceStructureId;
    }

    public String getComments() {
        return comments;
    }

    @Nullable
    public ResourceLocation getSourceStructure() {
        return sourceStructure;
    }

    /** Set the resolved source Holder after registry lookup in ModConfig. */
    public void setSourceStructure(ResourceLocation sourceStructure) {
        this.sourceStructure = sourceStructure;
    }

    /** Returns an unmodifiable view of this concept's stages, in definition order. */
    public List<StructureConceptStage> getStages() {
        return Collections.unmodifiableList(stages);
    }

    /** Returns the number of stages defined for this concept. */
    public int getStageCount() {
        return stages.size();
    }

    /** Returns the highest stage index defined in this concept. */
    public int getMaxStage() {
        int max = 0;
        for (StructureConceptStage s : stages) {
            if (s.getStage() > max) max = s.getStage();
        }
        return max;
    }

    /**
     * Returns the stage entry matching the given stage number, or null if not defined.
     * Stage numbers are 1-indexed (stage 1 = canvas1, stage 2 = canvas2, etc.).
     */
    @Nullable
    public StructureConceptStage getStage(int stageNumber) {
        StructureConceptStage result = stages.get(0);
        for (StructureConceptStage s : stages) {
            if (s.getStage() == stageNumber) result = s;
        }
        return result;
    }

    /**
     * Returns the structureId for the given stage number, or null if the stage
     * does not exist. Stage numbers are 1-indexed.
     */
    @Nullable
    public String getStructureIdForStage(int stageNumber) {
        StructureConceptStage s = getStage(stageNumber);
        return (s == null) ? null : s.getStructureId();
    }

    /** Removes a stage from this concept's mutable internal list. */
    public boolean removeStage(int stageNumber) {
        return stages.removeIf(s -> s.getStage() == stageNumber);
    }

    // -- Serialization --

    public JsonObject serialize() {
        JsonObject obj = new JsonObject();
        obj.addProperty("structureConceptId", structureConceptId);
        obj.addProperty("sourceStructureId", sourceStructureId);
        obj.addProperty("comments", comments);

        JsonArray stagesArray = new JsonArray();
        for (StructureConceptStage stage : stages) {
            stagesArray.add(stage.serialize());
        }
        obj.add("stages", stagesArray);

        return obj;
    }

    public static StructureConcept deserialize(JsonObject obj) {
        String conceptId = obj.has("structureConceptId")
            ? obj.get("structureConceptId").getAsString() : "";
        String sourceId  = obj.has("sourceStructureId")
            ? obj.get("sourceStructureId").getAsString() : "";
        String comments  = obj.has("comments")
            ? obj.get("comments").getAsString() : "";

        List<StructureConceptStage> stages = new ArrayList<>();
        if (obj.has("stages") && obj.get("stages").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("stages");
            for (JsonElement elem : arr) {
                if (elem.isJsonObject()) {
                    stages.add(StructureConceptStage.deserialize(elem.getAsJsonObject()));
                }
            }
        }

        return new StructureConcept(conceptId, sourceId, comments, stages);
    }
}
