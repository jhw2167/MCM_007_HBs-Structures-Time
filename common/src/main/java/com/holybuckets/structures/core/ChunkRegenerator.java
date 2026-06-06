package com.holybuckets.structures.core;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.model.ManagedChunk;
import com.holybuckets.foundation.model.ManagedChunkUtility;
import com.holybuckets.structures.LoggerProject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Regenerates pristine terrain for a chunk by replaying the worldgen pipeline
 * (BIOMES → NOISE → SURFACE → CARVERS) on a scratch ProtoChunk, then copying
 * the resulting block sections back into the live LevelChunk. Structures and
 * biome features (trees, ores) are intentionally omitted.
 */
public class ChunkRegenerator {

    private static final String CLASS_ID = "012";

    //Rebuilds chunk over specified area to reset terrain for the next structure
    public static boolean resetTerrain(ProtoChunk protoChunk, ServerLevel level, ChunkPos pos, List<ChunkAccess> localChunks) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();

        if (protoChunk == null) return false;

        WorldGenRegion region = new WorldGenRegion(level, localChunks, ChunkStatus.FEATURES, 0);

        try {

            protoChunk.setStatus(ChunkStatus.STRUCTURE_STARTS);

            protoChunk.setStatus(ChunkStatus.STRUCTURE_REFERENCES);

            CompletableFuture<ChunkAccess> biomesFuture = generator.createBiomes(
                level.getServer(), randomState, Blender.empty(),
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

           /* generator.applyCarvers(region, level.getSeed(), randomState, level.getBiomeManager(),
                level.structureManager().forWorldGenRegion(region), protoChunk,
                GenerationStep.Carving.AIR); //removed for performance concerns
            */
            protoChunk.setStatus(ChunkStatus.CARVERS);

            /*
            if(applyDecoration) {
                List<ChunkAccess> genChunks = new ArrayList<>(localChunks.size());
                localChunks.forEach(c -> genChunks.add(createProtoChunk(level, c.getPos())));
                region = new WorldGenRegion(level, genChunks, ChunkStatus.FEATURES, 1);
                generator.applyBiomeDecoration(region, protoChunk, level.structureManager().forWorldGenRegion(region));
            }
            protoChunk.setStatus(ChunkStatus.FEATURES);
             */


        } catch (RuntimeException e) {
            //catch and move on from carver runtime exceptions for OOB region check
        }
        catch (Exception e) {
            e.getMessage();
            e.printStackTrace();
            throw new RuntimeException("Terrain regeneration failed at " + pos + ": " + e.getMessage(), e);
        }


        //LoggerProject.logDebug("012010", "Regenerated terrain for chunk " + pos);
        return true;
    }

    /**
     * Applies biome decoration (trees, flowers, grass, ores) to cached ProtoChunks.
     * Builds a contiguous square WorldGenRegion from CHUNK_CACHE, filling gaps with
     * live LevelChunks so the region grid is valid for WorldGenRegion's index math.
     * Returns false if any chunk in the grid is not fully loaded.
     */
    public static boolean applyDecorationBatch(ServerLevel level, List<ChunkPos> chunksToDecorate,
                                               Map<Structure, StructureStart> starts,
                                               BoundingBox structureArea) {

            if (chunksToDecorate.isEmpty() || CHUNK_CACHE.isEmpty()) return false;

            ManagedChunkUtility util = ManagedChunkUtility.getInstance(level);
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            if (util == null) return false;

            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (ChunkPos cp : chunksToDecorate) {
                minX = Math.min(minX, cp.x);
                maxX = Math.max(maxX, cp.x);
                minZ = Math.min(minZ, cp.z);
                maxZ = Math.max(maxZ, cp.z);
            }

            // Pad by 5 on each side: decoration can write ~2 chunks out from center,
            // plus 1 read-only border, plus safety margin for large features (trees)
            int C = 3;
            minX -= C;
            maxX += C;
            minZ -= C;
            maxZ += C;

            int sideX = maxX - minX + 1;
            int sideZ = maxZ - minZ + 1;
            int side = Math.max(sideX, sideZ);

            // WorldGenRegion requires a square grid, make odd so center is clean
            if (side % 2 == 0) side++;

            // Re-center both axes to the square
            int centerX = (minX + maxX) / 2;
            int centerZ = (minZ + maxZ) / 2;
            minX = centerX - side / 2;
            maxX = centerX + side / 2;
            minZ = centerZ - side / 2;
            maxZ = centerZ + side / 2;

            List<ChunkAccess> regionChunks = new ArrayList<>(side * side);
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    regionChunks.add(createProtoChunk(level, new ChunkPos(x, z)));
                }
            }

            // writeRadius controls which chunks accept setBlock — reserve 2 outer rings
            // as read-only buffer that decoration can query but not write to
            int writeRadius = side / 2 - 2;
            WorldGenRegion region = new WorldGenRegion(level, regionChunks, ChunkStatus.FEATURES, writeRadius);

            // 5. Set structure starts + references on all proto chunks
            if(!starts.isEmpty())
            {
                for (ProtoChunk proto : CHUNK_CACHE.values()) {
                    proto.setAllStarts(starts);
                    starts.forEach((s, start) ->
                        proto.addReferenceForStructure(s, start.getChunkPos().toLong()));
                }
            }


            // 6. Decorate only the requested chunks
            for (ChunkPos cp : chunksToDecorate) {
                ProtoChunk centerChunk = CHUNK_CACHE.get(cp);
                if (centerChunk == null) continue;

                generator.applyBiomeDecoration(region, centerChunk,
                    level.structureManager().forWorldGenRegion(region));
                centerChunk.setStatus(ChunkStatus.FEATURES);
            }

            return true;
    }


    // Convenience overload: regenerate an entire chunk with no bounding box filter.
    public static boolean regenerateChunk(ProtoChunk chunk, ServerLevel level, ChunkPos pos) {
        ManagedChunkUtility util = ManagedChunkUtility.getInstance(level);
        if (util == null) return false;
        if (!util.isChunkFullyLoaded(pos)) return false;

        BoundingBox fullChunk = new BoundingBox(
            pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
            pos.getMaxBlockX(), level.getMaxBuildHeight(), pos.getMaxBlockZ()
        );

        List<String> localChunks = HBUtil.ChunkUtil.getLocalChunkIds(pos, 8);
        boolean allLoaded = localChunks.stream().allMatch(util::isChunkFullyLoaded);
        if (!allLoaded) return false;
        List<ChunkAccess> chunks = localChunks.stream()
            .map(id -> util.getManagedChunk(id).getCachedLevelChunk())
            .collect(Collectors.toList());


        return resetTerrain(chunk, level, pos, chunks);
    }

    private static Map<ChunkPos, ProtoChunk> CHUNK_CACHE = new ConcurrentHashMap<>();

    public static ProtoChunk createProtoChunk(Level level, ChunkPos pos) {
        if (CHUNK_CACHE.containsKey(pos)) return CHUNK_CACHE.get(pos);
        try {
            Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
            ProtoChunk pc = new ProtoChunk(pos, UpgradeData.EMPTY, level, biomeRegistry, null);
            CHUNK_CACHE.put(pos, pc);
            return pc;  // <-- return the newly created chunk
        } catch (Exception e) {
            LoggerProject.logError(CLASS_ID + "002", "Failed to create scratch chunk: " + e.getMessage());
            return null;
        }
    }

    public static void clearCache(Set<ChunkPos> toClear) {
        CHUNK_CACHE.keySet().removeAll(toClear);
    }

    public static void copyChunk(ServerLevel level, ChunkPos pos, BoundingBox area,
    List<BlockPos> lootPos) {
        ProtoChunk proto = CHUNK_CACHE.get(pos);
        if (proto == null) return;

        LevelChunk live = level.getChunk(pos.x, pos.z);
        copySections(proto, live, area);
        live.setUnsaved(true);
        notifyClients(level, live, area);

        //save all the lootable entities for players to loot
        lootPos.addAll(proto.getBlockEntitiesPos().stream()
            .filter(area::isInside)
            .collect(Collectors.toList()));
    }

    // Copies block state data from the scratch ProtoChunk into the live LevelChunk, bounded by region.
    private static void copySections(ProtoChunk source, LevelChunk target, BoundingBox region) {
        int minSection = target.getMinSection();
        int maxSection = target.getMaxSection();
        Level level = target.getLevel();

        for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
            int blockMinY = SectionPos.sectionToBlockCoord(sectionY);
            int blockMaxY = blockMinY + 15;

            if (blockMaxY < region.minY() || blockMinY > region.maxY()) continue;

            int index = target.getSectionIndexFromSectionY(sectionY);
            LevelChunkSection sourceSection = source.getSection(index);
            LevelChunkSection targetSection = target.getSection(index);

            if (sourceSection.hasOnlyAir() && targetSection.hasOnlyAir()) continue;
            copyBlockStates(sourceSection, targetSection, target.getPos(), sectionY, region, target.getLevel());

            // Mark affected sections dirty in the light engine so it recomputes
            SectionPos sectionPos = SectionPos.of(target.getPos(), sectionY);
            level.getLightEngine().updateSectionStatus(sectionPos, false);

        }

        //get all block entities in protochunk and copy them to the live chunk
        for (BlockPos bePos : source.getBlockEntitiesPos()) {
            if (!region.isInside(bePos)) continue;
            BlockEntity protoBe = source.getBlockEntity(bePos);
            if (protoBe == null) continue;
            level.setBlockEntity(protoBe);
        }


        // Also flag sky + block light as needing a full re-check for this chunk
        level.getChunkSource().getLightEngine().propagateLightSources(target.getPos());
    }

    // Per-block copy within a section, respecting the bounding box.
    private static void copyBlockStates(LevelChunkSection source, LevelChunkSection target,
                                        ChunkPos chunkPos, int sectionY, BoundingBox region,
                                        Level realWorld) {
        int baseX = chunkPos.getMinBlockX();
        int baseY = SectionPos.sectionToBlockCoord(sectionY);
        int baseZ = chunkPos.getMinBlockZ();

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int worldX = baseX + x;
                    int worldY = baseY + y;
                    int worldZ = baseZ + z;

                    if(!region.isInside(worldX, worldY, worldZ)) continue;
                    BlockState state = source.getBlockState(x, y, z);
                    if(state.equals(target.getBlockState(x, y, z))) continue;

                    BlockPos bp = new BlockPos(worldX, worldY, worldZ);
                    if( realWorld.getBlockEntity(bp) != null ) {
                       realWorld.removeBlockEntity(bp);
                    }

                    realWorld.setBlock(bp, state, Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_CLIENTS);
                    //target.setBlockState(x, y, z, state);
                }
            }
        }
    }

    /**
     * Copies chest loot for structures for any block entity in the passed position.
     * Attempts to copy using NBT save and load
     * @param level
     * @param pos
     */
    public static void copyLoot(ServerLevel level, BlockPos pos) {
        ProtoChunk proto = CHUNK_CACHE.get(new ChunkPos(pos));
        if (proto == null) return;
        BlockEntity oldBe = proto.getBlockEntity(pos);
        BlockEntity newBe = level.getBlockEntity(pos);
        if (oldBe == null || newBe==null) return;
        CompoundTag itemData = oldBe.saveWithFullMetadata();
        newBe.load(itemData);
    }

    /**
     * Copy Mobs - copy the entities and all their properties from the protochunk to the live chunk.
     * @param level
     * @param chunk
     * @param region
     */


    private static void notifyClients(ServerLevel level, LevelChunk chunk, BoundingBox region) {
        level.getChunkSource().getLightEngine().propagateLightSources(chunk.getPos());

        ClientboundForgetLevelChunkPacket forget =
            new ClientboundForgetLevelChunkPacket(chunk.getPos().x, chunk.getPos().z);

        HBUtil.PlayerUtil.getAllPlayers().forEach(player -> {
            player.connection.send(forget);
            player.connection.send(new ClientboundLevelChunkWithLightPacket(
                chunk, level.getLightEngine(), null, null
            ));
        });
    }


}