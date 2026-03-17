package com.holybuckets.structures.config.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
 */
public class StructureConcept {


   
    public static class StructureConceptStage {

        private final int stage;
        private final String structureId;

        public StructureConceptStage(int stage, String structureId) {
            this.stage = stage;
            this.structureId = (structureId == null) ? "" : structureId;
        }

        // -- Getters --

        public int getStage() {
            return stage;
        }

        public String getStructureId() {
            return structureId;
        }

        /** Returns true if this stage has an actual structure to place. */
        public boolean hasStructure() {
            return !structureId.isEmpty() && !structureId.equalsIgnoreCase("empty");
        }

        /** Returns true if this stage explicitly removes / leaves empty. */
        public boolean isEmpty() {
            return structureId.isEmpty() || structureId.equalsIgnoreCase("empty");
        }

        // -- Serialization --

        public JsonObject serialize() {
            JsonObject obj = new JsonObject();
            obj.addProperty("stage", stage);
            obj.addProperty("structureId", structureId);
            return obj;
        }

        public static StructureConceptStage deserialize(JsonObject obj) {
            int stage       = obj.has("stage")       ? obj.get("stage").getAsInt()        : 0;
            String structId = obj.has("structureId") ? obj.get("structureId").getAsString() : "";
            return new StructureConceptStage(stage, structId);
        }
    }


    private final String structureConceptId;
    private final String comments;
    private final List<StructureConceptStage> stages;

    public StructureConcept(String structureConceptId, String comments, List<StructureConceptStage> stages) {
        this.structureConceptId = structureConceptId;
        this.comments           = (comments == null) ? "" : comments;
        this.stages             = (stages == null) ? new ArrayList<>() : new ArrayList<>(stages);
    }


    public String getStructureConceptId() {
        return structureConceptId;
    }

    public String getComments() {
        return comments;
    }

    /** Returns an unmodifiable view of this concept's stages, in definition order. */
    public List<StructureConceptStage> getStages() {
        return Collections.unmodifiableList(stages);
    }

    /** Returns the number of stages defined for this concept. */
    public int getStageCount() {
        return stages.size();
    }

    /**
     * Returns the stage entry at the given ordinal, or null if out of range.
     */
    @Nullable
    public StructureConceptStage getStage(int stageIndex) {
        if (stageIndex < 0 || stageIndex >= stages.size()) return null;
        return stages.get(stageIndex);
    }

    /**
     * Returns the structureId for the given stage ordinal, or null if the stage
     * does not exist.
     */
    @Nullable
    public String getStructureIdForStage(int stageIndex) {
        StructureConceptStage s = getStage(stageIndex);
        return (s == null) ? null : s.getStructureId();
    }


    public JsonObject serialize() {
        JsonObject obj = new JsonObject();
        obj.addProperty("structureConceptId", structureConceptId);
        obj.addProperty("comments", comments);

        JsonArray stagesArray = new JsonArray();
        for (StructureConceptStage stage : stages) {
            stagesArray.add(stage.serialize());
        }
        obj.add("stages", stagesArray);

        return obj;
    }

    /**
     * Deserializes a StructureConcept from a JsonObject.
     */
    public static StructureConcept deserialize(JsonObject obj) {
        String conceptId = obj.has("structureConceptId")
            ? obj.get("structureConceptId").getAsString() : "";
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

        return new StructureConcept(conceptId, comments, stages);
    }
}
