package com.holybuckets.structures;

import com.holybuckets.structures.client.CommonClassClient;
import com.holybuckets.structures.client.IBewlrRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class StructuresOverTimeMainForgeClient {


    public static void clientInitializeForge() {
        CommonClassClient.initClient();
        //Item challengeChest = ModBlocks.challengeChest.asItem();
       // setBlockEntityRender( challengeChest, ChallengeItemBlockRenderer.CHEST_RENDERER);
    }

        private static void setBlockEntityRender(Object item, BlockEntityWithoutLevelRenderer renderer) {
            ((IBewlrRenderer) item).setBlockEntityWithoutLevelRenderer(renderer);
        }

}
