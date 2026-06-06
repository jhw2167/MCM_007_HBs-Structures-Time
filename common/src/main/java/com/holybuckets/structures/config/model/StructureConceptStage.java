package com.holybuckets.structures.config.model;

import com.google.gson.JsonObject;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.structures.StructuresOverTimeMain;
import com.holybuckets.structures.config.ModConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;

public class StructureConceptStage {

    private final int stage;
    private final String structureId;
    private ResourceLocation structureLoc;
    private boolean includeEntities;
    private boolean includeLoot;

    private String upgradeStructureTrigger="32"; //can be an item, a dimension, or a number of days

    private static final long DAY_CYCLE_MORNING  = 1000L;  //overworld tick for morning
    private static final long DAY_CYCLE_NOON      = 6000L;  //overworld tick for noon
    private static final long DAY_CYCLE_EVENING   = 12000L; //overworld tick for evening
    private static final long DAY_CYCLE_MIDNIGHT  = 18000L; //overworld tick for midnight

    private String upgradeStructureOnItemTriggerRaw;      //serialized item name
    private String upgradeStructureOnDimensionTriggerRaw; //serialized dimension id
    private String upgradeStructureOnDayCountRaw;         //serialized day count
    private String upgradeStructureOnDayCycleRaw;         //serialized day cycle keyword
    private String upgradeStructureOnMobsKilledRaw;       //serialized "<entityType>,<count>"
    private String upgradeStructureOnTotalEntitiesRaw;    //serialized "<entityType>,<count>"
    private boolean removeEntitiesAfterStage;             //remove area entities when this stage ends

    private Item upgradeStructureOnItemTrigger;                       //hydrated item
    private ResourceKey<Level> upgradeStructureOnDimensionTrigger;    //hydrated dimension key
    private Long upgradeStructureOnDayCount;                          //hydrated day count
    private Long upgradeStructureOnDayCycle;                          //hydrated overworld tick
    private HashMap<EntityType<?>, Integer> upgradeStructureOnMobsKilled;    //hydrated entity kill counts
    private HashMap<EntityType<?>, Integer> upgradeStructureOnTotalEntities; //hydrated entity total counts

    public StructureConceptStage(int stage, String structureId) {
        this.stage = stage;
        if(structureId == null || structureId.isEmpty())
            this.structureId = ModConfig.EMPTY_STRUCTURE_LOC.toString();
        else
            this.structureId = structureId.trim();

        if( structureId.contains(":") ) {
            this.structureLoc = new ResourceLocation(structureId);
        } else {
            this.structureLoc = new ResourceLocation("minecraft", structureId);
        }
        includeEntities = false;
        includeLoot = true;
        this.upgradeStructureTrigger = StructuresOverTimeMain.CONFIG.defaultConceptConfigs.upgradeStructureTrigger;
        this.removeEntitiesAfterStage = StructuresOverTimeMain.CONFIG.defaultConceptConfigs.removeEntities;
    }

    public StructureConceptStage(int stage, String structureId, String trigger,  boolean addMobs, boolean addLoot) {
        this(stage, structureId);
        this.includeEntities = addMobs;
        this.includeLoot = addLoot;
        if(trigger != null && !trigger.isEmpty())
            this.upgradeStructureTrigger = trigger;
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

    public String getUpgradeStructureTrigger() {
        return upgradeStructureTrigger;
    }

    @Nullable
    public Item getUpgradeStructureOnItemTrigger() {
        return upgradeStructureOnItemTrigger;
    }

    @Nullable
    public ResourceKey<Level> getUpgradeStructureOnDimensionTrigger() {
        return upgradeStructureOnDimensionTrigger;
    }

    @Nullable
    public Long getUpgradeStructureOnDayCount() {
        return upgradeStructureOnDayCount;
    }

    @Nullable
    public Long getUpgradeStructureOnDayCycle() {
        return upgradeStructureOnDayCycle;
    }

    @Nullable
    public HashMap<EntityType<?>, Integer> getUpgradeStructureOnMobsKilled() {
        return upgradeStructureOnMobsKilled;
    }

    @Nullable
    public HashMap<EntityType<?>, Integer> getUpgradeStructureOnTotalEntities() {
        return upgradeStructureOnTotalEntities;
    }

    public boolean isRemoveEntitiesAfterStage() {
        return removeEntitiesAfterStage;
    }

    //setters for the raw JSON-backed trigger strings
    public void setUpgradeStructureOnItemTriggerRaw(String v) { this.upgradeStructureOnItemTriggerRaw = v; }
    public void setUpgradeStructureOnDimensionTriggerRaw(String v) { this.upgradeStructureOnDimensionTriggerRaw = v; }
    public void setUpgradeStructureOnDayCountRaw(String v) { this.upgradeStructureOnDayCountRaw = v; }
    public void setUpgradeStructureOnDayCycleRaw(String v) { this.upgradeStructureOnDayCycleRaw = v; }
    public void setUpgradeStructureOnMobsKilledRaw(String v) { this.upgradeStructureOnMobsKilledRaw = v; }
    public void setUpgradeStructureOnTotalEntitiesRaw(String v) { this.upgradeStructureOnTotalEntitiesRaw = v; }
    public void setRemoveEntitiesAfterStage(boolean v) { this.removeEntitiesAfterStage = v; }

    /** Resolves the raw trigger strings into their typed values; called at beforeServerStarted. */
    public void hydrateTriggers()
    {
        if (notBlank(upgradeStructureOnItemTriggerRaw))
            upgradeStructureOnItemTrigger = HBUtil.ItemUtil.itemNameToItem(upgradeStructureOnItemTriggerRaw);
        if (notBlank(upgradeStructureOnDimensionTriggerRaw))
            upgradeStructureOnDimensionTrigger = ResourceKey.create(
                Registries.DIMENSION, new ResourceLocation(upgradeStructureOnDimensionTriggerRaw.trim()));
        if (notBlank(upgradeStructureOnDayCountRaw))
            upgradeStructureOnDayCount = parseLong(upgradeStructureOnDayCountRaw);
        if (notBlank(upgradeStructureOnDayCycleRaw))
            upgradeStructureOnDayCycle = dayCycleToTicks(upgradeStructureOnDayCycleRaw);
        if (notBlank(upgradeStructureOnMobsKilledRaw))
            upgradeStructureOnMobsKilled = parseEntityCounts(upgradeStructureOnMobsKilledRaw);
        if (notBlank(upgradeStructureOnTotalEntitiesRaw))
            upgradeStructureOnTotalEntities = parseEntityCounts(upgradeStructureOnTotalEntitiesRaw);
    }

    private static boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }

    @Nullable
    private static Long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    //maps a day-cycle keyword to its overworld tick constant
    private static Long dayCycleToTicks(String cycle) {
        switch (cycle.trim().toLowerCase()) {
            case "noon":     return DAY_CYCLE_NOON;
            case "evening":  return DAY_CYCLE_EVENING;
            case "midnight": return DAY_CYCLE_MIDNIGHT;
            default:         return DAY_CYCLE_MORNING;
        }
    }

    //parses "<entityType>,<count>" pairs into a typed entity-count map
    private static HashMap<EntityType<?>, Integer> parseEntityCounts(String raw) {
        HashMap<EntityType<?>, Integer> map = new HashMap<>();
        String[] parts = raw.split(",");
        if (parts.length < 2) return map;
        EntityType<?> type = HBUtil.EntityUtil.entityNameToEntityType(parts[0].trim());
        Long count = parseLong(parts[1]);
        if (type != null && count != null) map.put(type, count.intValue());
        return map;
    }

    /** Returns true if this stage has an actual structure to place. */
    public boolean hasStructure() {
        return !structureId.isEmpty() && !structureId.equals(ModConfig.EMPTY_STRUCTURE_LOC.toString());
    }

    public boolean is(ResourceLocation s) {
        return structureLoc != null && s != null && structureLoc.equals(s);
    }

    /** Returns true if this stage explicitly removes / leaves empty. */
    public boolean isEmptyStruct() {
        return structureId.equals(ModConfig.EMPTY_STRUCTURE_LOC.toString());
    }

    public boolean isSkipStruct() {
        return structureId.equals(ModConfig.SKIP_STRUCTURE_LOC.toString());
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
        if (upgradeStructureOnItemTriggerRaw != null) obj.addProperty("upgradeStructureOnItemTrigger", upgradeStructureOnItemTriggerRaw);
        if (upgradeStructureOnDimensionTriggerRaw != null) obj.addProperty("upgradeStructureOnDimensionTrigger", upgradeStructureOnDimensionTriggerRaw);
        if (upgradeStructureOnDayCountRaw != null) obj.addProperty("upgradeStructureOnDayCount", upgradeStructureOnDayCountRaw);
        if (upgradeStructureOnDayCycleRaw != null) obj.addProperty("upgradeStructureOnDayCycle", upgradeStructureOnDayCycleRaw);
        if (upgradeStructureOnMobsKilledRaw != null) obj.addProperty("upgradeStructureOnMobsKilled", upgradeStructureOnMobsKilledRaw);
        if (upgradeStructureOnTotalEntitiesRaw != null) obj.addProperty("upgradeStructureOnTotalEntities", upgradeStructureOnTotalEntitiesRaw);
        obj.addProperty("removeEntitiesAfterStage", removeEntitiesAfterStage);
        return obj;
    }

    public static StructureConceptStage deserialize(JsonObject obj) {
        int stage       = obj.has("stage")       ? obj.get("stage").getAsInt()         : 1;
        String structId = obj.has("structureId") ? obj.get("structureId").getAsString() : "";
        boolean addMobs = true;
        boolean addLoot = true;
        if( obj.has("includeEntities") ) addMobs = obj.get("includeEntities").getAsBoolean();
        if( obj.has("includeLoot") ) addLoot = obj.get("includeLoot").getAsBoolean();

        //get upgradeStructureTrigger
        String upgradeTrigger = obj.has("upgradeStructureTrigger")
            ? obj.get("upgradeStructureTrigger").getAsString()
            : StructuresOverTimeMain.CONFIG.defaultConceptConfigs.upgradeStructureTrigger;

        //replace constant strings "empty" and "skip" with the config defaults if encountered in the trigger (for backward compatibility with old configs)
        if(upgradeTrigger.equalsIgnoreCase("empty")) {
            upgradeTrigger = ModConfig.EMPTY_STRUCTURE_LOC.toString();
        } else if(upgradeTrigger.equalsIgnoreCase("skip")) {
            upgradeTrigger = ModConfig.SKIP_STRUCTURE_LOC.toString();
        }

        StructureConceptStage result = new StructureConceptStage(stage, structId, upgradeTrigger, addMobs, addLoot);

        if (obj.has("upgradeStructureOnItemTrigger")) result.setUpgradeStructureOnItemTriggerRaw(obj.get("upgradeStructureOnItemTrigger").getAsString());
        if (obj.has("upgradeStructureOnDimensionTrigger")) result.setUpgradeStructureOnDimensionTriggerRaw(obj.get("upgradeStructureOnDimensionTrigger").getAsString());
        if (obj.has("upgradeStructureOnDayCount")) result.setUpgradeStructureOnDayCountRaw(obj.get("upgradeStructureOnDayCount").getAsString());
        if (obj.has("upgradeStructureOnDayCycle")) result.setUpgradeStructureOnDayCycleRaw(obj.get("upgradeStructureOnDayCycle").getAsString());
        if (obj.has("upgradeStructureOnMobsKilled")) result.setUpgradeStructureOnMobsKilledRaw(obj.get("upgradeStructureOnMobsKilled").getAsString());
        if (obj.has("upgradeStructureOnTotalEntities")) result.setUpgradeStructureOnTotalEntitiesRaw(obj.get("upgradeStructureOnTotalEntities").getAsString());
        if (obj.has("removeEntitiesAfterStage")) result.setRemoveEntitiesAfterStage(obj.get("removeEntitiesAfterStage").getAsBoolean());

        return result;
    }
}

