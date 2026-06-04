package com.holybuckets.structures.config;

import com.google.gson.*;
import com.google.gson.stream.MalformedJsonException;
import com.holybuckets.foundation.modelInterface.IStringSerializable;
import com.holybuckets.structures.LoggerProject;
import com.holybuckets.structures.config.model.StructureConcept;

import javax.annotation.Nullable;
import java.util.*;

import static com.holybuckets.structures.config.ModConfig.EMPTY_STRUCTURE_LOC;

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
    public static StructureConceptJsonConfig buildDefaultConfig() {
        List<StructureConcept> concepts = new ArrayList<>();

        // village: witch hut → village → (empty) → pillager outpost
        List<StructureConcept.StructureConceptStage> villageStages = List.of(
            new StructureConcept.StructureConceptStage(0, "minecraft:swamp_hut", "32", false, true),
            new StructureConcept.StructureConceptStage(1, "minecraft:village_plains", "the_nether", true, true),
            new StructureConcept.StructureConceptStage(2, EMPTY_STRUCTURE_LOC.toString() , "the_end", false, true),
            new StructureConcept.StructureConceptStage(3, "minecraft:pillager_outpost", "32", true, true)
        );
        concepts.add(new StructureConcept(
            "village",
            "minecraft:swamp_hut",
            "A village that starts as a witch hut, evolves into a village, skips stage 3, and then becomes a pillager outpost",
            villageStages,
            true,
            -1,
            8
        ));

        // vanishingShip: shipwreck → empty
        List<StructureConcept.StructureConceptStage> shipStages = List.of(
            new StructureConcept.StructureConceptStage(0, "minecraft:shipwreck", "32", false, true),
            new StructureConcept.StructureConceptStage(1, "", "32", false, true)
        );
        concepts.add(new StructureConcept(
            "vanishingShip",
            "minecraft:shipwreck",
            "A Shipwreck which disappears after the early game",
            shipStages,
            false, 0, 8
        ));

        return new StructureConceptJsonConfig(concepts);
    }
}
