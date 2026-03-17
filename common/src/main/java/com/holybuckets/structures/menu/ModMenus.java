package com.holybuckets.structures.menu;

import com.holybuckets.structures.Constants;
import com.holybuckets.structures.block.be.TemplateBlockEntity;
import net.blay09.mods.balm.api.DeferredObject;
import net.blay09.mods.balm.api.menu.BalmMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ModMenus {

    public static DeferredObject<MenuType<TemplateChestEntityMenu>> countingChestMenu;


    public static void initialize(BalmMenus menus)
    {
        countingChestMenu = menus.registerMenu(id("counting_chest_menu"),
            (syncId, inventory, buf) -> {
                BlockPos pos = buf.readBlockPos();
                Level level = inventory.player.level();
                BlockEntity be = inventory.player.level().getBlockEntity(pos);
                if( be instanceof TemplateBlockEntity) {
                    TemplateBlockEntity cbe = (TemplateBlockEntity) be;
                    cbe.setLevel(level);
                    return new TemplateChestEntityMenu(syncId, inventory, cbe);
                }
                return null;
            });
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation(Constants.MOD_ID, name);
    }

}


