package com.holybuckets.structures.core;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.model.ManagedChunk;
import com.holybuckets.foundation.model.ManagedChunkUtility;
import com.holybuckets.structures.LoggerProject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Regenerates pristine terrain for a chunk by replaying the worldgen pipeline
 * (BIOMES → NOISE → SURFACE → CARVERS) on a scratch ProtoChunk, then copying
 * the resulting block sections back into the live LevelChunk. Structures and
 * biome features (trees, ores) are intentionally omitted.
 */
public class ChunkRegenerator {

    private static final String CLASS_ID = "012";

    // Replays terrain generation and overwrites the live chunk's block data within the given bounds.
    public static boolean regenerateTerrain(ServerLevel level, ChunkPos pos, BoundingBox area,
                                            List<ChunkAccess> localChunks, Map<Structure, StructureStart> structures)
    {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();

        ProtoChunk protoChunk;
        try {
            Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
            protoChunk = new ProtoChunk(pos,  UpgradeData.EMPTY, level, biomeRegistry, null);
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "002", "Failed to create scratch chunk: " + e.getMessage());
            return false;
        }
        if (protoChunk == null) return false;

        WorldGenRegion region = new WorldGenRegion(level, localChunks, ChunkStatus.FEATURES, 0);

        try {
            structures.forEach((structure, start) -> {
            if (start.isValid()) {
                protoChunk.setStartForStructure(structure, start);
            }
        });
            protoChunk.setStatus(ChunkStatus.STRUCTURE_STARTS);

            protoChunk.setStatus(ChunkStatus.STRUCTURE_REFERENCES);

            CompletableFuture<ChunkAccess> biomesFuture = generator.createBiomes(
                level.getServer(), randomState, Blender.of(region),
                level.structureManager().forWorldGenRegion(region), protoChunk
            );
            level.getServer().managedBlock(biomesFuture::isDone);
            protoChunk.setStatus(ChunkStatus.BIOMES);

            CompletableFuture<ChunkAccess> noiseFuture = generator.fillFromNoise(
                level.getServer(), Blender.of(region), randomState,
                level.structureManager().forWorldGenRegion(region), protoChunk
            );
            level.getServer().managedBlock(noiseFuture::isDone);
            protoChunk.setStatus(ChunkStatus.NOISE);

            generator.buildSurface(region, level.structureManager().forWorldGenRegion(region),
                randomState, protoChunk);
            protoChunk.setStatus(ChunkStatus.SURFACE);

            generator.applyCarvers(region, level.getSeed(), randomState, level.getBiomeManager(),
                level.structureManager().forWorldGenRegion(region), protoChunk,
                GenerationStep.Carving.AIR);
            protoChunk.setStatus(ChunkStatus.CARVERS);
    } catch (Exception e) {
        throw new RuntimeException("Terrain regeneration failed at " + pos + ": " + e.getMessage(), e);
    }

        LevelChunk live = level.getChunk(pos.x, pos.z);
        copySections(protoChunk, live, area);
        live.setUnsaved(true);
        notifyClients(level, live, area);

        LoggerProject.logDebug(CLASS_ID + "010", "Regenerated terrain for chunk " + pos);
        return true;
    }

    // Convenience overload: regenerate an entire chunk with no bounding box filter.
    public static boolean regenerateChunk(ServerLevel level, ChunkPos pos)
    {
        ManagedChunkUtility util = ManagedChunkUtility.getInstance(level);
        if(util==null) return false;
        if(!util.isChunkFullyLoaded(pos)) return false;

        BoundingBox fullChunk = new BoundingBox(
            pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
            pos.getMaxBlockX(), level.getMaxBuildHeight(), pos.getMaxBlockZ()
        );

        List<String> localChunks = HBUtil.ChunkUtil.getLocalChunkIds(pos, 8);
        boolean allLoaded = localChunks.stream().allMatch(util::isChunkFullyLoaded);
        if(!allLoaded) return false;
        List<ChunkAccess> chunks = localChunks.stream()
            .map(id -> util.getManagedChunk(id).getCachedLevelChunk())
            .collect(Collectors.toList());

        return regenerateTerrain(level, pos, fullChunk, chunks, Map.of());
    }


    // Copies block state data from the scratch ProtoChunk into the live LevelChunk, bounded by region.
    private static void copySections(ProtoChunk source, LevelChunk target, BoundingBox region)
    {
        int minSection = target.getMinSection();
        int maxSection = target.getMaxSection();

        for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
            int blockMinY = SectionPos.sectionToBlockCoord(sectionY);
            int blockMaxY = blockMinY + 15;

            if (blockMaxY < region.minY() || blockMinY > region.maxY()) continue;

            int index = target.getSectionIndexFromSectionY(sectionY);
            LevelChunkSection sourceSection = source.getSection(index);
            LevelChunkSection targetSection = target.getSection(index);

            copyBlockStates(sourceSection, targetSection, target.getPos(), sectionY, region);
        }
    }

    // Per-block copy within a section, respecting the bounding box.
    private static void copyBlockStates(LevelChunkSection source, LevelChunkSection target,
                                        ChunkPos chunkPos, int sectionY, BoundingBox region) {
        int baseX = chunkPos.getMinBlockX();
        int baseY = SectionPos.sectionToBlockCoord(sectionY);
        int baseZ = chunkPos.getMinBlockZ();

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int worldX = baseX + x;
                    int worldY = baseY + y;
                    int worldZ = baseZ + z;

                    if (!region.isInside(worldX, worldY, worldZ)) continue;

                    BlockState state = source.getBlockState(x, y, z);
                    target.setBlockState(x, y, z, state);
                }
            }
        }
    }

    // Sends block updates to clients for all blocks within the bounding box.
    private static void notifyClients(ServerLevel level, LevelChunk chunk, BoundingBox region) {
        HBUtil.PlayerUtil.getAllPlayers().forEach(player -> {
            player.connection.send(new ClientboundLevelChunkWithLightPacket(
                chunk, level.getLightEngine(), null, null
            ));
        });
    }
}
