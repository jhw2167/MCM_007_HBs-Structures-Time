# ChunkRegenerator: 1.20.1 → 1.21.1

Signatures verified against decompiled 1.21.1 sources. Goal of the class: synchronously
regenerate/refresh overworld terrain in an area. Drive chunk methods directly, not the async pipeline.

## Mechanical replacements

- `ChunkAccess#setStatus` → `setPersistedStatus`; `getStatus` → `getPersistedStatus`.
- Import `net.minecraft.world.level.chunk.status.ChunkStatus` (moved out of `.chunk`).
- `createBiomes(Executor,…)` / `fillFromNoise(Executor,…)` — leading `Executor` removed.
- `ClientboundForgetLevelChunkPacket(int,int)` → `(ChunkPos)`.
- `saveWithFullMetadata()` → `saveWithFullMetadata(HolderLookup.Provider)`.
- `BlockEntity#load(tag)` → `loadWithComponents(tag, HolderLookup.Provider)` (provider from `level.registryAccess()`).
- `applyCarvers` still takes `GenerationStep.Carving`. `applyBiomeDecoration` unchanged.

## Reuse as-is

`createProtoChunk`, all copy-back (`copySections`, `copyBlockStates`, `copyLoot`, `notifyClients`).

## BIOMES — direct, no future

```java
protoChunk.fillBiomesFromNoise(generator.getBiomeSource(), randomState.sampler());
protoChunk.setPersistedStatus(ChunkStatus.BIOMES);
```

## NOISE — inline fillFromNoise synchronously via invoker mixin

`doFill` is private on final `NoiseBasedChunkGenerator`. Expose with `@Invoker` (works on final classes):

```java
@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseBasedChunkGeneratorInvoker {
    @Invoker("doFill")
    ChunkAccess invokeDoFill(Blender blender, StructureManager structureManager,
                             RandomState random, ChunkAccess chunk, int minCellY, int cellCountY);
}
```

Register in `hbs_structures.mixins.json` `"mixins"` array (server-safe). Then:

```java
if (generator instanceof NoiseBasedChunkGenerator nbcg) {
    NoiseSettings ns = nbcg.generatorSettings().value().noiseSettings()
        .clampToHeightAccessor(protoChunk.getHeightAccessorForGeneration());
    int cellHeight = ns.getCellHeight();
    int minCellY   = Mth.floorDiv(ns.minY(), cellHeight);
    int cellCountY = Mth.floorDiv(ns.height(), cellHeight);
    if (cellCountY > 0) {
        int top = protoChunk.getSectionIndex(cellCountY * cellHeight - 1 + ns.minY());
        int bot = protoChunk.getSectionIndex(ns.minY());
        List<LevelChunkSection> acquired = new ArrayList<>();
        for (int s = top; s >= bot; s--) {
            LevelChunkSection sec = protoChunk.getSection(s);
            sec.acquire();
            acquired.add(sec);
        }
        try {
            ((NoiseBasedChunkGeneratorInvoker) generator).invokeDoFill(
                Blender.of(region),
                level.structureManager().forWorldGenRegion(region),
                randomState, protoChunk, minCellY, cellCountY);
        } finally {
            acquired.forEach(LevelChunkSection::release);
        }
    }
}
protoChunk.setPersistedStatus(ChunkStatus.NOISE);
```

Guard on `instanceof NoiseBasedChunkGenerator` — flat/debug/datapack generators lack `doFill`.
Overworld is always noise-based.

## WorldGenRegion — new constructor

```
1.20.1: WorldGenRegion(ServerLevel, List<ChunkAccess>, ChunkStatus, int writeRadius)
1.21.1: WorldGenRegion(ServerLevel, StaticCache2D<GenerationChunkHolder>, ChunkStep, ChunkAccess center)
```

- `ChunkStatus` arg → `ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.SURFACE)`.
- `writeRadius` arg gone; baked into step as `blockStateWriteRadius()`, enforced by `ensureCanWrite`.
- Cache cells are `GenerationChunkHolder` (abstract; the getter `WorldGenRegion` calls reads a private
  `futures` array). Subclass and override `getChunkIfPresentUnchecked` to return the scratch chunk.

### Single-chunk StaticCache2D

`buildSurface` is per-chunk (16×16 column loop, no neighbour reads), so use size `0` — one cell:

```java
// create(cx, cz, size, init): min = c - size, side = 2*size + 1  → size 0 = 1×1 grid
StaticCache2D<GenerationChunkHolder> cache =
    StaticCache2D.create(pos.x, pos.z, 0, (x, z) -> holderFor(protoChunk));
```

Then:
```java
generator.buildSurface(region, level.structureManager().forWorldGenRegion(region),
    randomState, protoChunk);
protoChunk.setPersistedStatus(ChunkStatus.SURFACE);
```
where `region` uses the SURFACE step and this single-chunk cache.

## Notes

- Narrow the `catch (RuntimeException e) {}` swallow before testing — region failures surface as `ReportedException`.
- Test `copyLoot` round-trip (data components now) and block-entity copy separately.
