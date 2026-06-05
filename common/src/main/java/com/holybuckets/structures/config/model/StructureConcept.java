package com.holybuckets.structures.config.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.structures.LoggerProject;
import com.holybuckets.structures.StructuresOverTimeMain;
import com.holybuckets.structures.config.ModConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private int cycleStage;                     //loops structures back to this  stage after its last stage, -1 for no cycle
    private int uniqueStage;                     //stage after which only one instance of this concept may exist, -1 for none

    private final List<StructureConceptStage> stages;

    private ResourceLocation sourceStructure;


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
        this.cycleStage                     = StructuresOverTimeMain.CONFIG.defaultConceptConfigs.cycleStage;
        this.uniqueStage                       = StructuresOverTimeMain.CONFIG.defaultConceptConfigs.uniqueStage;
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

    public int getCycleStage() { return cycleStage; }

    public void setCycleStage(int cycleStage) { this.cycleStage = cycleStage; }

    public int getUniqueStage() {
        return uniqueStage;
    }

    public void setUniqueStage(int uniqueStage) {
        this.uniqueStage = uniqueStage;
    }

    @Nullable
    private Object getStructureUpgradeTrigger(int stageNo) {
        StructureConceptStage stage = getStage(stageNo);
        if (stage == null) return null;
        return parseTrigger( stage.getUpgradeStructureTrigger() );
    }


    public Object parseTrigger(String triggerString)
    {
        try {
            return Integer.parseInt(triggerString);
        } catch (NumberFormatException e) {}

        Item item = HBUtil.ItemUtil.itemNameToItem(triggerString);
        if (item != null && !item.equals(Items.AIR)) return item;

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
    public Item getStructureUpgradeItem(int s) {
        StructureConceptStage stage = getStage(s);
        if (stage == null) return null;
        return stage.getUpgradeStructureOnItemTrigger();
    }

    @Nullable
    public ResourceKey<Level> getStructureUpgradeDimension(int s) {
        StructureConceptStage stage = getStage(s);
        if (stage == null) return null;
        return stage.getUpgradeStructureOnDimensionTrigger();
    }

    @Nullable
    public Long getStructureUpgradeDays(int s) {
        StructureConceptStage stage = getStage(s);
        if (stage == null) return null;
        return stage.getUpgradeStructureOnDayCount();
    }

    @Nullable
    public Long getStructureUpgradeDayCycle(int s) {
        StructureConceptStage stage = getStage(s);
        if (stage == null) return null;
        return stage.getUpgradeStructureOnDayCycle();
    }

    @Nullable
    public Map<EntityType<?>, Integer> getStructureUpgradeMobsKilled(int s) {
        StructureConceptStage stage = getStage(s);
        if (stage == null) return null;
        return stage.getUpgradeStructureOnMobsKilled();
    }

    @Nullable
    public Map<EntityType<?>, Integer> getStructureUpgradeTotalEntities(int s) {
        StructureConceptStage stage = getStage(s);
        if (stage == null) return null;
        return stage.getUpgradeStructureOnTotalEntities();
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
     * Stage numbers are 0-indexed (stage 1 = canvas1, stage 2 = canvas2, etc.).
     * 0 is the structure that spawns initially in the world
     */
    @Nullable
    public StructureConceptStage getStage(int stageNumber) {
        if( stageNumber < 0 ) return  new StructureConceptStage(-1, ModConfig.EMPTY_STRUCTURE_LOC.toString());
        StructureConceptStage result = new StructureConceptStage(stageNumber, ModConfig.SKIP_STRUCTURE_LOC.toString());
        if(cycleStage!=-1 && stageNumber>getMaxStage()+1) stageNumber=cycleStage;
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


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StructureConcept)) return false;
        StructureConcept other = (StructureConcept) o;
        if(this.structureConceptId == null) {
            if( other.structureConceptId == null) return true;
            else return false;
        }
        return this.structureConceptId.equals(other.structureConceptId);
    }

    @Override
    public int hashCode() {
        return structureConceptId != null ? structureConceptId.hashCode() : 0;
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
        obj.addProperty("cycleStage", cycleStage);
        obj.addProperty("uniqueStage", uniqueStage);

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


        List<StructureConceptStage> stages = new ArrayList<>();
        if (obj.has("stages") && obj.get("stages").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("stages");
            for (JsonElement elem : arr) {
                if (elem.isJsonObject()) {
                    stages.add(StructureConceptStage.deserialize(elem.getAsJsonObject()));
                }
            }
        }

        StructureConcept concept = new StructureConcept(conceptId, sourceId, comments, stages,
            stopSpawn, stopChest, stopDays);
        if (obj.has("cycleStage")) concept.setCycleStage(obj.get("cycleStage").getAsInt());
        if (obj.has("uniqueStage")) concept.setUniqueStage(obj.get("uniqueStage").getAsInt());
        return concept;
    }
}
