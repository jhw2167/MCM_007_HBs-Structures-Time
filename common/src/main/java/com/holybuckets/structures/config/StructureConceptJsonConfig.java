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
 * Config file format — a top-level JSON array:
 * [
 *   {
 *     "structureConceptId": "village",
 *     "comments": "...",
 *     "stages": [
 *       { "stage": 0, "structureId": "minecraft:witch_hut" },
 *       ...
 *     ]
 *   },
 *   ...
 * ]
 */
public class StructureConceptJsonConfig implements IStringSerializable {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    public static final String DEF_CONFIG_FILE_PATH = "config/HBStructuresConceptConfig.json";

    /**
     * Embedded default JSON. Used when no config file is found on disk.
     * Mirrors the format described above exactly.
     */
    public static final String DEFAULT_JSON =
        "[\n" +
        "    {\n" +
        "        \"structureConceptId\": \"village\",\n" +
        "        \"comments\": \"A village that starts as a witch hut, evolves into a village, skips stage 2, and then becomes a pillager fort\",\n" +
        "        \"stages\": [\n" +
        "            {\n" +
        "                \"stage\": 0,\n" +
        "                \"structureId\": \"minecraft:witch_hut\"\n" +
        "            },\n" +
        "            {\n" +
        "                \"stage\": 1,\n" +
        "                \"structureId\": \"minecraft:village_plains\"\n" +
        "            },\n" +
        "            {\n" +
        "                \"stage\": 2,\n" +
        "                \"structureId\": \"\"\n" +
        "            },\n" +
        "            {\n" +
        "                \"stage\": 3,\n" +
        "                \"structureId\": \"minecraft:pillager_outpost\"\n" +
        "            }\n" +
        "        ]\n" +
        "    },\n" +
        "    {\n" +
        "        \"structureConceptId\": \"vanishingShip\",\n" +
        "        \"comments\": \"A Shipwreck which disappears after the early game\",\n" +
        "        \"stages\": [\n" +
        "            {\n" +
        "                \"stage\": 0,\n" +
        "                \"structureId\": \"minecraft:shipwreck\"\n" +
        "            },\n" +
        "            {\n" +
        "                \"stage\": 1,\n" +
        "                \"structureId\": \"empty\"\n" +
        "            }\n" +
        "        ]\n" +
        "    }\n" +
        "]";

    /** Pre-built default config instance, used as the fallback. */
    public static final StructureConceptJsonConfig DEFAULT_CONFIG = buildDefaultConfig();

    // -----------------------------------------------------------------------
    // Instance state
    // -----------------------------------------------------------------------

    /** Ordered map preserving insertion order from the JSON array. */
    private final Map<String, StructureConcept> conceptMap;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // IStringSerializable
    // -----------------------------------------------------------------------

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
        if (!parsed.isJsonArray()) {
            // Tolerate a wrapped object in case the file was saved that way
            if (parsed.isJsonObject()) {
                JsonObject obj = parsed.getAsJsonObject();
                // Look for a root array field by any name as a fallback
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    if (entry.getValue().isJsonArray()) {
                        parseArray(entry.getValue().getAsJsonArray());
                        return;
                    }
                }
            }
            return;
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

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Default config builder
    // -----------------------------------------------------------------------

    private static StructureConceptJsonConfig buildDefaultConfig() {
        StructureConceptJsonConfig config = new StructureConceptJsonConfig(List.of());
        config.deserialize(DEFAULT_JSON);
        return config;
    }
}
