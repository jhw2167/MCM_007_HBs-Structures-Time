package com.holybuckets.structures.config;

import com.google.gson.*;
import com.holybuckets.foundation.modelInterface.IStringSerializable;
import com.holybuckets.structures.config.model.StructureConcept;
import com.holybuckets.structures.config.model.StructureConceptStage;

import javax.annotation.Nullable;
import java.util.*;


/**
 * Class: StructureConceptJsonConfig
 * Description: Parses and holds the full list of StructureConcept entries read
 * from the JSON config file (or the embedded default).
 *
 */
public class StructureConceptJsonConfig implements IStringSerializable {


    public static final String DEF_CONFIG_FILE_PATH = "config/HBStructuresConceptConfig.json";




    /** Ordered map preserving insertion order from the JSON array. */
    private final Map<String, StructureConcept> conceptMap;

    public StructureConceptJsonConfig(List<StructureConcept> concepts) {
        this.conceptMap = new LinkedHashMap<>();
        if (concepts != null) {
            concepts.forEach(c -> conceptMap.put(c.getStructureConceptId(), c));
        }
    }

    /** Parse directly from a JSON string. */
    public StructureConceptJsonConfig(String jsonString) {
        this(List.of());
        deserialize(jsonString);
    }



    public Set<String> getAllConceptIds() {
        return Collections.unmodifiableSet(conceptMap.keySet());
    }

    public Collection<StructureConcept> getAllConcepts() {
        return Collections.unmodifiableCollection(conceptMap.values());
    }

    @Nullable
    public StructureConcept getConcept(String conceptId) {
        return conceptMap.get(conceptId);
    }

    public boolean hasConcept(String conceptId) {
        return conceptMap.containsKey(conceptId);
    }

    public int size() {
        return conceptMap.size();
    }

    /** Removes a concept by id. Used during registry resolution pruning. */
    public void removeConcept(String conceptId) {
        conceptMap.remove(conceptId);
    }


    //** SERIALIZERS **//

    @Override
    public String serialize() {
        JsonArray root = new JsonArray();
        for (StructureConcept concept : conceptMap.values()) {
            root.add(concept.serialize());
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(root);
    }

    @Override
    public void deserialize(String jsonString) throws RuntimeException {
        if (jsonString == null || jsonString.isBlank()) return;

        try {
            JsonElement parsed = JsonParser.parseString(jsonString);
            if(!parsed.isJsonArray()) {
                throw new JsonParseException("Expected a JSON array at the root of the config");
            }
            parseArray(parsed.getAsJsonArray());
        } catch (Exception e) {
            throw new RuntimeException("Invalid JSON format for StructureConceptJsonConfig", e);
        }

    }

    private void parseArray(JsonArray array) {
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            StructureConcept concept = StructureConcept.deserialize(element.getAsJsonObject());
            if (concept.getStructureConceptId() != null && !concept.getStructureConceptId().isEmpty()) {
                conceptMap.put(concept.getStructureConceptId(), concept);
            }
        }
    }


    //** DEFAULTS **//

    //triggers are stored on the stage they advance INTO, and must be set through the
    //typed raw setters - upgradeStructureTrigger alone is never hydrated
    private static StructureConceptStage stage(int stage, String structureId, boolean addMobs, boolean addLoot) {
        return new StructureConceptStage(stage, structureId, null, addMobs, addLoot);
    }

    public static StructureConceptJsonConfig buildDefaultConfig() {
        List<StructureConcept> concepts = new ArrayList<>();

        // village: swamp hut -> village -> pillager outpost
        StructureConceptStage villageS0 = stage(0, "minecraft:swamp_hut", false, true);
        villageS0.setUpgradeStructureOnTotalEntitiesRaw("minecraft:villager,5");

        StructureConceptStage villageS1 = stage(1, "minecraft:village_plains", true, true);
        villageS1.setUpgradeStructureOnItemTriggerRaw("minecraft:diamond_sword");

        StructureConceptStage villageS2 = stage(2, "minecraft:pillager_outpost", true, true);
        villageS2.setUpgradeStructureOnDimensionTriggerRaw("minecraft:the_nether");

        StructureConceptStage villageS3 = stage(3, "empty", true, true);
        villageS3.setUpgradeStructureOnDayCycleRaw("night");



        concepts.add(new StructureConcept(
            "village",
            "minecraft:village_plains",
            "A swamp hut that becomes a plains village when a player holds a diamond sword, "
                + "then a pillager outpost once a player travels to the nether",
            List.of(villageS0, villageS1, villageS2, villageS3),
            true,
            -1,
            8
        ));
        concepts.get(0).setCycleStage(0);

        // test: ruined portal -> pillager outpost -> trial chambers (unique)
        StructureConceptStage testS0 = stage(0, "minecraft:ruined_portal", false, true);

        StructureConceptStage testS1 = stage(1, "minecraft:pillager_outpost", true, true);
        testS1.setUpgradeStructureOnMobsKilledRaw("minecraft:zombie,3");
        //set it to upgrade with wooden sword item
        testS1.setUpgradeStructureOnItemTriggerRaw("minecraft:wooden_sword");

        StructureConceptStage testS2 = stage(2, "minecraft:trial_chambers", true, true);
        testS2.setUpgradeStructureOnDayCountRaw("1");

        StructureConcept test = new StructureConcept(
            "test",
            "minecraft:ruined_portal",
            "A ruined portal that becomes a pillager outpost after three zombie kills, "
                + "then a single world-unique trial chambers",
            List.of(testS0, testS1, testS2),
            false,
            -1,
            -1
        );
        test.setUniqueStage(2);
        concepts.add(test);

        return new StructureConceptJsonConfig(concepts);
    }
}
