# HB Structures — Command Reference

| # | Command | Arguments | Description |
|---|---------|-----------|-------------|
| 1 | `/hbStructures getDetails` | `[x z]` | Gets details for the structure in the specified chunk. Returns an error if no concept is found in the chunk. Argument is optional, if null, we will search for the nearest structure. Not implemented |
| 2 | `/hbStructures config` | `<structureConceptId> [showAllStages]` | Prints the structureConcept config and optionally prints data on all stages in the concept |
| 3 | `/hbStructures stageConfig` | `<structureConceptId> [stageNo]` | Shows all details for any numbered stage in any concept. If user fails to provide a stage number, return the total number of stages |
| 4 | `/hbStructures locate` | `<structureConceptId>` | Returns the location of the structure concept from list of existing structures. If not found, use internal locate command to find structure of the matching origin type |
| 5 | `/hbStructures stopUpgrades` | `[x z]` | Stops the structure at the specified chunk from upgrading unless forced by commands. If argument is not provided, applies it to nearest structure |
| 6 | `/hbStructures forceUpgrade` | `[chunkX chunkZ] [confirm]` | Forces a structure to upgrade, enables further upgrades as well. Usage without the boolean set to true specifies which structure will be upgraded and reminds the player that the area will be completely cleared, but does not force the upgrade |
| 7 | `/hbStructures resumeUpgrades` | `[x z]` | Allows structure to continue upgrading, but doesnt force it to do so yet |

**Argument notation:** `<required>` `[optional]`

**Chunk position:** All chunk position arguments take two integers — chunk X and chunk Z coordinates.

---

## Structure Concept JSON Configuration

### Top-Level Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| `structureConceptId` | string | Unique identifier for this concept. Used by commands to reference the concept. |
| `sourceStructureId` | string | The structure ID that triggers this concept during worldgen. When this structure is intercepted in chunk generation, the chunk is flagged as belonging to this concept. |
| `comments` | string | Freeform description of the concept's progression. |
| `stopUpgradeIfSpawnpointSet` | boolean | If true, the structure stops upgrading when a player sets their spawnpoint inside it. |
| `stopUpgradeOnTotalChestCount` | int | Stops upgrading if the total number of chests placed in the structure reaches this count. Use `-1` to disable. |
| `stopUpgradeOnDaysSpentInStructure` | int | Stops upgrading if a player has spent this many in-game days inside the structure. Use `0` to disable. |
| `stages` | array | Ordered list of stage objects defining the structure's progression. |

### Stage Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `stage` | int | — | Stage index (0-indexed). Stage 0 is the initial placement. |
| `structureId` | string | — | The structure to place at this stage. Use `""` or `"empty"` to clear the area. Use `"skip"` to leave the previous structure unchanged. Supports modded IDs (e.g. `nova_structures:tavern_oak`). |
| `upgradeStructureTrigger` | string | `"32"` | What triggers the upgrade to the next stage. Can be: a number of in-game days (e.g. `"32"`), an item the player must obtain (e.g. `"diamond_sword"`), or a dimension the player must visit (e.g. `"minecraft:the_nether"`). |
| `includeEntities` | boolean | `false` | Whether to spawn entities (mobs) defined in the structure's NBT when placing this stage. |
| `includeLoot` | boolean | `true` | Whether to populate loot tables in containers when placing this stage. |

### Example

```json
{
  "structureConceptId": "village",
  "sourceStructureId": "nova_structures:tavern_oak",
  "comments": "A fort that grows from a swamp_hut to a hoglin fort to pillager outpost",
  "stopUpgradeIfSpawnpointSet": true,
  "stopUpgradeOnTotalChestCount": -1,
  "stopUpgradeOnDaysSpentInStructure": 2,
  "stages": [
    {
      "stage": 0,
      "structureId": "swamp_hut",
      "upgradeStructureTrigger": "diamond_sword"
    },
    {
      "stage": 1,
      "structureId": "nova_structures:hamlet",
      "upgradeStructureTrigger": "diamond_sword",
      "includeLoot": false
    },
    {
      "stage": 2,
      "structureId": "nova_structures:tavern_oak",
      "upgradeStructureTrigger": "diamond_sword",
      "includeEntities": true,
      "includeLoot": true
    },
    {
      "stage": 3,
      "structureId": "minecraft:village_plains",
      "upgradeStructureTrigger": "minecraft:the_nether",
      "includeEntities": true,
      "includeLoot": true
    },
    {
      "stage": 4,
      "structureId": "towns_and_towers:village_meadow",
      "upgradeStructureTrigger": "1",
      "includeEntities": false,
      "includeLoot": true
    }
  ]
}
```
