package com.holybuckets.structures.config;

import com.holybuckets.structures.Constants;
import net.blay09.mods.balm.api.config.reflection.Comment;
import net.blay09.mods.balm.api.config.reflection.Config;


@Config(Constants.MOD_ID)
public class StructuresTimeConfig {

    @Comment("devMode==true...")
    public boolean devMode = false;
    @Comment("Where the structure concept configs can be found")
    public String structureRulesConfig = "config/HBStructuresConceptConfig.json";


    public static class SatelliteBlockConfig {

        @Comment("Satellite will not operate below this y level")
        public int minSatelliteWorkingHeight = 256;
    }

    //public SatelliteBlockConfig displayConfig = new SatelliteBlockConfig();
}