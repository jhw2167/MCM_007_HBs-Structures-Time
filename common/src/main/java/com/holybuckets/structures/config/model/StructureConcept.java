package com.holybuckets.structures.config.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.structures.LoggerProject;
import com.holybuckets.structures.StructuresOverTimeMain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
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

    private static final String CLASS_ID = "005";

    private final String structureConceptId;
    private final String sourceStructureId;
    private final String comments;

    private boolean stopUpgradeIfSpawnpointSet;         //stops structure from upgrading if player spawnpoint is set in the structure
    private int stopUpgradeOnTotalChestCount;       //stops structure upgrade if a lot of chest on placed in the structure
    private int stopUpgradeOnDaysSpentInStructure;  //stops structure upgrade if significant time is spent in structure

    private final List<StructureConceptStage> stages;

    private ResourceLocation sourceStructure;

    public static class StructureConceptStage {

        private final int stage;
        private final String structureId;
        private ResourceLocation structureLoc;
        private boolean includeEntities;
        private boolean includeLoot;

        private String upgradeStructureTrigger="32"; //can be an item, a dimension, or a number of days

        public StructureConceptStage(int stage, String structureId) {
            this.stage = stage;
            this.structureId = (structureId == null) ? "" : structureId;
            this.structureLoc = null;
            includeEntities = false;
            includeLoot = true;
        }

        public StructureConceptStage(int stage, String structureId, boolean addMobs, boolean addLoot) {
            this(stage, structureId);
            this.includeEntities = addMobs;
            this.includeLoot = addLoot;
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

        //include loot and mobs getters
        public boolean isIncludeEntities() {
            return includeEntities;
        }

        public boolean isIncludeLoot() {
            return includeLoot;
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

        public boolean isSkip() {
            return structureId.equalsIgnoreCase("skip");
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
            obj.addProperty("includeEntities", includeEntities);
            obj.addProperty("includeLoot", includeLoot);
            obj.addProperty("upgradeStructureTrigger", upgradeStructureTrigger);
            return obj;
        }

        public static StructureConceptStage deserialize(JsonObject obj) {
            int stage       = obj.has("stage")       ? obj.get("stage").getAsInt()         : 1;
            String structId = obj.has("structureId") ? obj.get("structureId").getAsString() : "";
            boolean addMobs = true;
            boolean addLoot = true;
            if( obj.has("includeEntities") ) addMobs = obj.get("includeEntities").getAsBoolean();
            if( obj.has("includeLoot") ) addLoot = obj.get("includeLoot").getAsBoolean();

            return new StructureConceptStage(stage, structId, addMobs, addLoot);
        }
    }


    //** Constructors **//


    public StructureConcept(String structureConceptId, String sourceStructureId,
                            String comments, List<StructureConceptStage> stages) {
        this.structureConceptId = structureConceptId;
        this.sourceStructureId  = (sourceStructureId == null) ? "" : sourceStructureId;
        this.comments           = (comments == null) ? "" : comments;
        this.stages             = (stages == null) ? new ArrayList<>() : new ArrayList<>(stages);
        this.sourceStructure    = null;
        this.stopUpgradeIfSpawnpointSet        = StructuresOverTimeMain.CONFIG.defaultConceptConfigs.stopUpgradeIfSpawnpointSet;
        this.stopUpgradeOnTotalChestCount      = StructuresOverTimeMain.CONFIG.defaultConceptConfigs.stopUpgradeOnTotalChestCount;
        this.stopUpgradeOnDaysSpentInStructure = StructuresOverTimeMain.CONFIG.defaultConceptConfigs.stopUpgradeOnDaysSpentInStructure;
    }

    // Wide constructor delegates to the narrow one then overrides upgrade-stop flags
    public StructureConcept(String structureConceptId, String sourceStructureId,
                            String comments, List<StructureConceptStage> stages,
                            boolean stopUpgradeIfSpawnpointSet,
                            int stopUpgradeOnTotalChestCount,
                            int stopUpgradeOnDaysSpentInStructure) {
        this(structureConceptId, sourceStructureId, comments, stages);
        this.stopUpgradeIfSpawnpointSet        = stopUpgradeIfSpawnpointSet;
        this.stopUpgradeOnTotalChestCount      = stopUpgradeOnTotalChestCount;
        this.stopUpgradeOnDaysSpentInStructure = stopUpgradeOnDaysSpentInStructure;
    }



    public boolean hasStructure(ResourceLocation loc) {
        if (loc == null) return false;
        for (StructureConceptStage stage : stages) {
            if (stage.is(loc)) return true;
        }
        return false;
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

    //add getters for new variables
    public boolean isStopUpgradeIfSpawnpointSet() {
        return stopUpgradeIfSpawnpointSet;
    }

    public int getStopUpgradeOnTotalChestCount() {
        return stopUpgradeOnTotalChestCount;
    }

    public int getStopUpgradeOnDaysSpentInStructure() {
        return stopUpgradeOnDaysSpentInStructure;
    }

    @Nullable
    private Object getStructureUpgradeTrigger(int stageNo) {
        StructureConceptStage stage = getStage(stageNo);
        if (stage == null) return null;
        return parseTrigger( stage.upgradeStructureTrigger );
    }


    public Object parseTrigger(String triggerString)
    {
        try {
            return Integer.parseInt(triggerString);
        } catch (NumberFormatException e) {}

        Item item = HBUtil.ItemUtil.itemNameToItem(triggerString);
        if (item != null) return item;

        Level level = HBUtil.LevelUtil.toLevel(HBUtil.LevelUtil.LevelNameSpace.SERVER, triggerString);
        if (level != null) return level;

        String defTrigger = StructuresOverTimeMain.CONFIG.defaultConceptConfigs.upgradeStructureTrigger;
        if(defTrigger.equals(triggerString) ) {
            String msg = String.format("Default trigger '%s' is invalid. " + CRASHOUT, defTrigger);
            LoggerProject.logError("005002", msg);
            throw new IllegalArgumentException(msg);
        } else {
            LoggerProject.logError("005001", String.format(INVALID_TRIGGER, triggerString));
            return parseTrigger(defTrigger);
        }
    }
    private static final String INVALID_TRIGGER = "Trigger '%s' is neither an integer (days), an item, or a dimension name. Reverting to default structure upgrade trigger.";
    private static final String CRASHOUT = "The default structure upgrade trigger is not a valid integer, item or dimension. Please edit hbs_structures-common to set a valid default trigger.";

    @Nullable
    public Item getStructureUpgradeItem(int stage) {
        Object trigger = getStructureUpgradeTrigger(stage);
        return (trigger instanceof Item) ? (Item) trigger : null;
    }

    @Nullable
    public Level getStructureUpgradeDimension(int stage) {
        Object trigger = getStructureUpgradeTrigger(stage);
        return (trigger instanceof Level) ? (Level) trigger : null;
    }

    @Nullable
    public Integer getStructureUpgradeDays(int stage) {
        if(this.getStageCount()<stage) return null;
        Object trigger = getStructureUpgradeTrigger(stage);
        return (trigger instanceof Integer) ? (Integer) trigger : null;
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
        return stages.stream().toList();
    }

    public List<StructureConceptStage> getStagesNoSkips() {
        return stages.stream().filter(s -> !s.isSkip()).toList();
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
        if( stageNumber < 0 ) return  new StructureConceptStage(-1, "empty");
        StructureConceptStage result = new StructureConceptStage(stageNumber, "skip");
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
        obj.addProperty("stopUpgradeIfSpawnpointSet", stopUpgradeIfSpawnpointSet);
        obj.addProperty("stopUpgradeOnTotalChestCount", stopUpgradeOnTotalChestCount);
        obj.addProperty("stopUpgradeOnDaysSpentInStructure", stopUpgradeOnDaysSpentInStructure);

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

        boolean stopSpawn = obj.has("stopUpgradeIfSpawnpointSet")
            ? obj.get("stopUpgradeIfSpawnpointSet").getAsBoolean()
            : StructuresOverTimeMain.CONFIG.defaultConceptConfigs.stopUpgradeIfSpawnpointSet;
        int stopChest = obj.has("stopUpgradeOnTotalChestCount")
            ? obj.get("stopUpgradeOnTotalChestCount").getAsInt()
            : StructuresOverTimeMain.CONFIG.defaultConceptConfigs.stopUpgradeOnTotalChestCount;
        int stopDays  = obj.has("stopUpgradeOnDaysSpentInStructure")
            ? obj.get("stopUpgradeOnDaysSpentInStructure").getAsInt()
            : StructuresOverTimeMain.CONFIG.defaultConceptConfigs.stopUpgradeOnDaysSpentInStructure;

        //get upgradeStructureTrigger
        String upgradeTrigger = obj.has("upgradeStructureTrigger")
            ? obj.get("upgradeStructureTrigger").getAsString()
            : StructuresOverTimeMain.CONFIG.defaultConceptConfigs.upgradeStructureTrigger;

        List<StructureConceptStage> stages = new ArrayList<>();
        if (obj.has("stages") && obj.get("stages").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("stages");
            for (JsonElement elem : arr) {
                if (elem.isJsonObject()) {
                    stages.add(StructureConceptStage.deserialize(elem.getAsJsonObject()));
                }
            }
        }

        return new StructureConcept(conceptId, sourceId, comments, stages,
            stopSpawn, stopChest, stopDays);
    }
}
