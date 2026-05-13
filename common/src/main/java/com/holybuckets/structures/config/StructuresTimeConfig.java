package com.holybuckets.structures.config;

import com.holybuckets.structures.Constants;
import net.blay09.mods.balm.api.config.reflection.Comment;
import net.blay09.mods.balm.api.config.reflection.Config;


@Config(Constants.MOD_ID)
public class StructuresTimeConfig {

    @Comment("devMode==true...")
    public boolean devMode = false;
    @Comment("The file path to your structure concepts configuration. This file determines the origin structure and order for each structure in your progression chain")
    public String structureProgressConfig = "config/HBStructuresConceptConfig.json";

    @Comment("The file path to your general Structures Over Time Config. This file blacklists structures from spawning naturally and allows you to control the stage progression")
    public String structureGeneralConfig = "config/HBStructuresGeneralConfig.json";


    public static class SatelliteBlockConfig {

        @Comment("Satellite will not operate below this y level")
        public int minSatelliteWorkingHeight = 256;
    }

    //public SatelliteBlockConfig displayConfig = new SatelliteBlockConfig();
}