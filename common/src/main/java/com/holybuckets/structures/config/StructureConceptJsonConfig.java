package com.holybuckets.structures.config;

import com.google.gson.*;
import com.holybuckets.foundation.modelInterface.IStringSerializable;
import com.holybuckets.structures.config.model.StructureConcept;

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



    /** Pre-built default config instance, used as the fallback. */
    public static final StructureConceptJsonConfig DEFAULT_CONFIG = buildDefaultConfig();


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
    public void deserialize(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) return;

        JsonElement parsed = JsonParser.parseString(jsonString);
        if(!parsed.isJsonArray()) {
            throw new JsonParseException("Expected a JSON array at the root of the config");
        }
        parseArray(parsed.getAsJsonArray());
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


    private static StructureConceptJsonConfig buildDefaultConfig() {
        List<StructureConcept> concepts = new ArrayList<>();

        // village: witch hut → village → (empty) → pillager outpost
        List<StructureConcept.StructureConceptStage> villageStages = List.of(
            new StructureConcept.StructureConceptStage(1, "minecraft:swamp_hut"),
            new StructureConcept.StructureConceptStage(2, "minecraft:village_plains"),
            new StructureConcept.StructureConceptStage(3, ""),
            new StructureConcept.StructureConceptStage(4, "minecraft:pillager_outpost")
        );
        concepts.add(new StructureConcept(
            "village",
            "minecraft:village_plains",
            "A village that starts as a witch hut, evolves into a village, skips stage 3, and then becomes a pillager outpost",
            villageStages
        ));

        // vanishingShip: shipwreck → empty
        List<StructureConcept.StructureConceptStage> shipStages = List.of(
            new StructureConcept.StructureConceptStage(1, "minecraft:shipwreck"),
            new StructureConcept.StructureConceptStage(2, "empty")
        );
        concepts.add(new StructureConcept(
            "vanishingShip",
            "minecraft:shipwreck",
            "A Shipwreck which disappears after the early game",
            shipStages
        ));

        return new StructureConceptJsonConfig(concepts);
    }
}
