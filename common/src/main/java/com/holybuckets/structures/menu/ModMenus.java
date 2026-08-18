package com.holybuckets.structures.menu;

import com.holybuckets.structures.Constants;
import com.holybuckets.structures.block.be.TemplateBlockEntity;
import net.blay09.mods.balm.api.DeferredObject;
import net.blay09.mods.balm.api.menu.BalmMenuFactory;
import net.blay09.mods.balm.api.menu.BalmMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ModMenus {

    public static DeferredObject<MenuType<TemplateChestEntityMenu>> countingChestMenu;


    public static void initialize(BalmMenus menus)
    {
        countingChestMenu = menus.registerMenu(id("counting_chest_menu"),
            new BalmMenuFactory<TemplateChestEntityMenu, TemplateChestEntityMenu.Data>() {

                @Override
                public TemplateChestEntityMenu create(int syncId, Inventory inventory, TemplateChestEntityMenu.Data data) {
                    BlockPos pos = data.pos();
                    Level level = inventory.player.level();
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof TemplateBlockEntity cbe) {
                        cbe.setLevel(level);
                        return new TemplateChestEntityMenu(syncId, inventory, cbe);
                    }
                    return null;
                }

                @Override
                public StreamCodec<RegistryFriendlyByteBuf, TemplateChestEntityMenu.Data> getStreamCodec() {
                    return TemplateChestEntityMenu.STREAM_CODEC;
                }
            });
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, name);
    }

}
