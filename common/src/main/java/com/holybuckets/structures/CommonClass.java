package com.holybuckets.structures;

import com.holybuckets.foundation.event.BalmEventRegister;
import com.holybuckets.structures.block.ModBlocks;
import com.holybuckets.structures.block.be.ModBlockEntities;
import com.holybuckets.structures.config.StructuresTimeConfig;
import com.holybuckets.structures.item.ModItems;
import com.holybuckets.structures.menu.ModMenus;
import com.holybuckets.structures.platform.Services;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;


public class CommonClass {

    public static boolean isInitialized = false;
    public static void init()
    {
        if (isInitialized)
            return;

        //Initialize Foundations
        com.holybuckets.foundation.FoundationInitializers.commonInitialize();

        //RegisterConfigs
        Balm.getConfig().registerConfig(StructuresTimeConfig.class);
        StructuresOverTimeMain.INSTANCE = new StructuresOverTimeMain();
        BalmEventRegister.registerEvents();
        BalmEventRegister.registerCommands();
        ModBlocks.initialize(Balm.getBlocks());
        ModBlockEntities.initialize(Balm.getBlockEntities());
        ModItems.initialize(Balm.getItems());
        ModMenus.initialize(Balm.getMenus());
        
        isInitialized = true;
    }

    /**
     * Description: Run sample tests methods
     */
    public static void sample()
    {

    }
}