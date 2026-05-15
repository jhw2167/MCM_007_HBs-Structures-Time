package com.holybuckets.structures.config;

import com.holybuckets.structures.Constants;
import net.blay09.mods.balm.api.config.reflection.Comment;
import net.blay09.mods.balm.api.config.reflection.Config;
import net.blay09.mods.balm.api.config.reflection.NestedType;

import java.util.Set;


@Config(Constants.MOD_ID)
public class StructuresTimeConfig {

    @Comment("devMode==true...")
    public boolean devMode = false;
    @Comment("The file path to your structure concepts configuration. This file determines the origin structure and order for each structure in your progression chain")
    public String structureProgressConfig = "config/HBStructuresConceptConfig.json";

    @Comment("The file path to your general Structures Over Time Config. This file blacklists structures from spawning naturally and allows you to control the stage progression")
    public String structureGeneralConfig = "config/HBStructuresGeneralConfig.json";

    @Comment("Prevent certain structures from spawning normally in the world, they will only spawn via structure upgrades. You should not blacklist the ORIGIN STRUCTURES for your structure concepts")
    @NestedType(String.class)
    Set<String> naturalStructureSpawnsBlacklist = Set.of(
        "minecraft:igloo",
        "nova_structures:tavern_oak",
        "towns_and_towers:village_meadow"
        /*
        "nova_structures:villages_jungle",
        "nova_structures:villages_swamp",
        "nova_structures:abandoned_hut",
        "towns_and_towers:towns",
         "nova_structures:tavern_acacia",
        "nova_structures:tavern_birch",
        "nova_structures:tavern_cherry",
        "nova_structures:tavern_dark_oak",
        "nova_structures:tavern_desert",
        "nova_structures:tavern_jungle",
        "nova_structures:tavern_mangrove",
        "nova_structures:tavern_snowy",
        "nova_structures:tavern_spruce",
        "nova_structures:tavern_swamp",
        "towns_and_towers:village_badlands",
        "towns_and_towers:village_beach",
        "towns_and_towers:village_birch_forest",
        "towns_and_towers:village_flower_forest",
        "towns_and_towers:village_grove",
        "nova_structures:village_jungle",
        "towns_and_towers:village_jungle",
        "towns_and_towers:village_meadow",
        "towns_and_towers:village_mushroom_fields",
        "towns_and_towers:village_ocean",
        "towns_and_towers:village_old_growth_taiga",
        "towns_and_towers:village_savanna_plateau",
        "towns_and_towers:village_snowy_slopes",
        "towns_and_towers:village_snowy_taiga",
        "towns_and_towers:village_sparse_jungle",
        "towns_and_towers:village_sunflower_plains",
        "nova_structures:village_swamp",
        "towns_and_towers:village_swamp",
        "towns_and_towers:village_wooded_badlands"
         */
    );

    @Comment("Notifies all players with a chat message when a structure upgrade has been triggered")
    public boolean enableStructureUpgradeTriggeredNotification = true;

    @Comment("Warns nearby players when a structure is about to upgrade to the next stage OR if a structure was rejected from upgrading")
    public boolean enableStructureUpgradeWarning = true;

    //add an int time delay for triggering and processing a structure upgrade


    public static class DefaultStructureConceptConfigs {

        @Comment("Default stopUpgradeIfSpawnpointSet: true. If true, prevents a structure from upgrading to the next stage if the player has set their spawn point within that structure. This is to prevent players from accidentally losing access to their spawn point when a structure upgrades.")
        public boolean stopUpgradeIfSpawnpointSet = true;         //stops structure from upgrading if player spawnpoint is set in the structure

        @Comment("Default stopUpgradeOnTotalChestCount: -1. If a structure has more than this number of chests, it will not upgrade to the next stage. This is a good way to assess if a player has used a structure to establish a base and we don't want to overwrite their work. Set to -1 to ignore.")
        public int stopUpgradeOnTotalChestCount = -1;       //stops structure upgrade if a lot of chest on placed in the structure

        @Comment("Default stopUpgradeOnDaysSpentInStructure: 8. If a player has woken up (from a bed) at least this number of times then this structure will not upgrade unless forced. Set to -1 to ignore.")
        public int stopUpgradeOnDaysSpentInStructure = 1;    //stops structure upgrade if player has spent a lot of time in the structure


        @Comment("Default upgradeStructureTrigger: 32. By default a structure will upgrade to its next stage after 32 days. You can change this number, set it to an item or dimension name. This setting only changes the default value; edit the value(s) in HBStructuresConceptConfig.json to change it for each structure and stage.")
        public String upgradeStructureTrigger = "32";

    }

    public DefaultStructureConceptConfigs defaultConceptConfigs = new DefaultStructureConceptConfigs();
}