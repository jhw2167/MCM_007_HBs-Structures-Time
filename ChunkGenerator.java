//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package net.minecraft.world.level.chunk;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride.BoundingBoxType;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.apache.commons.lang3.mutable.MutableBoolean;

public abstract class ChunkGenerator {
    public static final Codec<ChunkGenerator> CODEC;
    protected final BiomeSource biomeSource;
    private final Supplier<List<FeatureSorter.StepFeatureData>> featuresPerStep;
    private final Function<Holder<Biome>, BiomeGenerationSettings> generationSettingsGetter;

    public ChunkGenerator(BiomeSource $$0) {
        this($$0, ($$0x) -> {
            return ((Biome)$$0x.value()).getGenerationSettings();
        });
    }

    public ChunkGenerator(BiomeSource $$0, Function<Holder<Biome>, BiomeGenerationSettings> $$1) {
        this.biomeSource = $$0;
        this.generationSettingsGetter = $$1;
        this.featuresPerStep = Suppliers.memoize(() -> {
            return FeatureSorter.buildFeaturesPerStep(List.copyOf($$0.possibleBiomes()), ($$1x) -> {
                return ((BiomeGenerationSettings)$$1.apply($$1x)).features();
            }, true);
        });
    }

    protected abstract Codec<? extends ChunkGenerator> codec();

    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> $$0, RandomState $$1, long $$2) {
        return ChunkGeneratorStructureState.createForNormal($$1, $$2, this.biomeSource, $$0);
    }

    public Optional<ResourceKey<Codec<? extends ChunkGenerator>>> getTypeNameForDataFixer() {
        return BuiltInRegistries.CHUNK_GENERATOR.getResourceKey(this.codec());
    }

    public CompletableFuture<ChunkAccess> createBiomes(Executor $$0, RandomState $$1, Blender $$2, StructureManager $$3, ChunkAccess $$4) {
        return CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("init_biomes", () -> {
            $$4.fillBiomesFromNoise(this.biomeSource, $$1.sampler());
            return $$4;
        }), Util.backgroundExecutor());
    }

    public abstract void applyCarvers(WorldGenRegion var1, long var2, RandomState var4, BiomeManager var5, StructureManager var6, ChunkAccess var7, GenerationStep.Carving var8);

    @Nullable
    public Pair<BlockPos, ResourceLocation> findNearestMapStructure(ServerLevel $$0, HolderSet<Structure> $$1, BlockPos $$2, int $$3, boolean $$4) {
        ChunkGeneratorStructureState $$5 = $$0.getChunkSource().getGeneratorState();
        Map<StructurePlacement, Set<ResourceLocation>> $$6 = new Object2ObjectArrayMap();
        Iterator var8 = $$1.iterator();

        while(var8.hasNext()) {
            ResourceLocation $$7 = (Holder)var8.next();
            Iterator var10 = $$5.getPlacementsForStructure($$7).iterator();

            while(var10.hasNext()) {
                StructurePlacement $$8 = (StructurePlacement)var10.next();
                ((Set)$$6.computeIfAbsent($$8, ($$0x) -> {
                    return new ObjectArraySet();
                })).add($$7);
            }
        }

        if ($$6.isEmpty()) {
            return null;
        } else {
            Pair<BlockPos, ResourceLocation> $$9 = null;
            double $$10 = Double.MAX_VALUE;
            StructureManager $$11 = $$0.structureManager();
            List<Map.Entry<StructurePlacement, Set<ResourceLocation>>> $$12 = new ArrayList($$6.size());
            Iterator var13 = $$6.entrySet().iterator();

            while(var13.hasNext()) {
                Map.Entry<StructurePlacement, Set<ResourceLocation>> $$13 = (Map.Entry)var13.next();
                StructurePlacement $$14 = (StructurePlacement)$$13.getKey();
                if ($$14 instanceof ConcentricRingsStructurePlacement) {
                    ConcentricRingsStructurePlacement $$15 = (ConcentricRingsStructurePlacement)$$14;
                    Pair<BlockPos, ResourceLocation> $$16 = this.getNearestGeneratedStructure((Set)$$13.getValue(), $$0, $$11, $$2, $$4, $$15);
                    if ($$16 != null) {
                        BlockPos $$17 = (BlockPos)$$16.getFirst();
                        double $$18 = $$2.distSqr($$17);
                        if ($$18 < $$10) {
                            $$10 = $$18;
                            $$9 = $$16;
                        }
                    }
                } else if ($$14 instanceof RandomSpreadStructurePlacement) {
                    $$12.add($$13);
                }
            }

            if (!$$12.isEmpty()) {
                int $$19 = SectionPos.blockToSectionCoord($$2.getX());
                int $$20 = SectionPos.blockToSectionCoord($$2.getZ());

                for(int $$21 = 0; $$21 <= $$3; ++$$21) {
                    boolean $$22 = false;
                    Iterator var30 = $$12.iterator();

                    while(var30.hasNext()) {
                        Map.Entry<StructurePlacement, Set<ResourceLocation>> $$23 = (Map.Entry)var30.next();
                        RandomSpreadStructurePlacement $$24 = (RandomSpreadStructurePlacement)$$23.getKey();
                        Pair<BlockPos, ResourceLocation> $$25 = getNearestGeneratedStructure((Set)$$23.getValue(), $$0, $$11, $$19, $$20, $$21, $$4, $$5.getLevelSeed(), $$24);
                        if ($$25 != null) {
                            $$22 = true;
                            double $$26 = $$2.distSqr((Vec3i)$$25.getFirst());
                            if ($$26 < $$10) {
                                $$10 = $$26;
                                $$9 = $$25;
                            }
                        }
                    }

                    if ($$22) {
                        return $$9;
                    }
                }
            }

            return $$9;
        }
    }

    @Nullable
    private Pair<BlockPos, ResourceLocation> getNearestGeneratedStructure(Set<ResourceLocation> $$0, ServerLevel $$1, StructureManager $$2, BlockPos $$3, boolean $$4, ConcentricRingsStructurePlacement $$5) {
        List<ChunkPos> $$6 = $$1.getChunkSource().getGeneratorState().getRingPositionsFor($$5);
        if ($$6 == null) {
            throw new IllegalStateException("Somehow tried to find structures for a placement that doesn't exist");
        } else {
            Pair<BlockPos, ResourceLocation> $$7 = null;
            double $$8 = Double.MAX_VALUE;
            BlockPos.MutableBlockPos $$9 = new BlockPos.MutableBlockPos();
            Iterator var12 = $$6.iterator();

            while(var12.hasNext()) {
                ChunkPos $$10 = (ChunkPos)var12.next();
                $$9.set(SectionPos.sectionToBlockCoord($$10.x, 8), 32, SectionPos.sectionToBlockCoord($$10.z, 8));
                double $$11 = $$9.distSqr($$3);
                boolean $$12 = $$7 == null || $$11 < $$8;
                if ($$12) {
                    Pair<BlockPos, ResourceLocation> $$13 = getStructureGeneratingAt($$0, $$1, $$2, $$4, $$5, $$10);
                    if ($$13 != null) {
                        $$7 = $$13;
                        $$8 = $$11;
                    }
                }
            }

            return $$7;
        }
    }

    @Nullable
    private static Pair<BlockPos, ResourceLocation> getNearestGeneratedStructure(Set<ResourceLocation> $$0, LevelReader $$1, StructureManager $$2, int $$3, int $$4, int $$5, boolean $$6, long $$7, RandomSpreadStructurePlacement $$8) {
        int $$9 = $$8.spacing();

        for(int $$10 = -$$5; $$10 <= $$5; ++$$10) {
            boolean $$11 = $$10 == -$$5 || $$10 == $$5;

            for(int $$12 = -$$5; $$12 <= $$5; ++$$12) {
                boolean $$13 = $$12 == -$$5 || $$12 == $$5;
                if ($$11 || $$13) {
                    int $$14 = $$3 + $$9 * $$10;
                    int $$15 = $$4 + $$9 * $$12;
                    ChunkPos $$16 = $$8.getPotentialStructureChunk($$7, $$14, $$15);
                    Pair<BlockPos, ResourceLocation> $$17 = getStructureGeneratingAt($$0, $$1, $$2, $$6, $$8, $$16);
                    if ($$17 != null) {
                        return $$17;
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private static Pair<BlockPos, ResourceLocation> getStructureGeneratingAt(Set<ResourceLocation> $$0, LevelReader $$1, StructureManager $$2, boolean $$3, StructurePlacement $$4, ChunkPos $$5) {
        Iterator var6 = $$0.iterator();

        Holder $$6;
        StructureStart $$9;
        do {
            do {
                do {
                    StructureCheckResult $$7;
                    do {
                        if (!var6.hasNext()) {
                            return null;
                        }

                        $$6 = (Holder)var6.next();
                        $$7 = $$2.checkStructurePresence($$5, (Structure)$$6.value(), $$3);
                    } while($$7 == StructureCheckResult.START_NOT_PRESENT);

                    if (!$$3 && $$7 == StructureCheckResult.START_PRESENT) {
                        return Pair.of($$4.getLocatePos($$5), $$6);
                    }

                    ChunkAccess $$8 = $$1.getChunk($$5.x, $$5.z, ChunkStatus.STRUCTURE_STARTS);
                    $$9 = $$2.getStartForStructure(SectionPos.bottomOf($$8), (Structure)$$6.value(), $$8);
                } while($$9 == null);
            } while(!$$9.isValid());
        } while($$3 && !tryAddReference($$2, $$9));

        return Pair.of($$4.getLocatePos($$9.getChunkPos()), $$6);
    }

    private static boolean tryAddReference(StructureManager $$0, StructureStart $$1) {
        if ($$1.canBeReferenced()) {
            $$0.addReference($$1);
            return true;
        } else {
            return false;
        }
    }

    public void applyBiomeDecoration(WorldGenLevel $$0, ChunkAccess $$1, StructureManager $$2) {
        ChunkPos $$3 = $$1.getPos();
        if (!SharedConstants.debugVoidTerrain($$3)) {
            SectionPos $$4 = SectionPos.of($$3, $$0.getMinSection());
            BlockPos $$5 = $$4.origin();
            Registry<Structure> $$6 = $$0.registryAccess().registryOrThrow(Registries.STRUCTURE);
            Map<Integer, List<Structure>> $$7 = (Map)$$6.stream().collect(Collectors.groupingBy(($$0x) -> {
                return $$0x.step().ordinal();
            }));
            List<FeatureSorter.StepFeatureData> $$8 = (List)this.featuresPerStep.get();
            WorldgenRandom $$9 = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
            long $$10 = $$9.setDecorationSeed($$0.getSeed(), $$5.getX(), $$5.getZ());
            Set<Holder<Biome>> $$11 = new ObjectArraySet();
            ChunkPos.rangeClosed($$4.chunk(), 1).forEach(($$2x) -> {
                ChunkAccess $$3x = $$0.getChunk($$2x.x, $$2x.z);
                LevelChunkSection[] var4 = $$3x.getSections();
                int var5 = var4.length;

                for(int var6 = 0; var6 < var5; ++var6) {
                    LevelChunkSection $$4x = var4[var6];
                    PalettedContainerRO var10000 = $$4x.getBiomes();
                    Objects.requireNonNull($$11);
                    var10000.getAll($$11::add);
                }

            });
            $$11.retainAll(this.biomeSource.possibleBiomes());
            int $$12 = $$8.size();

            try {
                Registry<PlacedFeature> $$13 = $$0.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
                int $$14 = Math.max(Decoration.values().length, $$12);

                for(int $$15 = 0; $$15 < $$14; ++$$15) {
                    int $$16 = 0;
                    CrashReportCategory var10000;
                    Iterator var20;
                    if ($$2.shouldGenerateStructures()) {
                        List<Structure> $$17 = (List)$$7.getOrDefault($$15, Collections.emptyList());

                        for(var20 = $$17.iterator(); var20.hasNext(); ++$$16) {
                            Structure $$18 = (Structure)var20.next();
                            $$9.setFeatureSeed($$10, $$16, $$15);
                            Supplier<String> $$19 = () -> {
                                Optional var10000 = $$6.getResourceKey($$18).map(Object::toString);
                                Objects.requireNonNull($$18);
                                return (String)var10000.orElseGet($$18::toString);
                            };

                            try {
                                $$0.setCurrentlyGenerating($$19);
                                $$2.startsForStructure($$4, $$18).forEach(($$5x) -> {
                                    $$5x.placeInChunk($$0, $$2, this, $$9, getWritableArea($$1), $$3);
                                });
                            } catch (Exception var29) {
                                Exception $$20 = var29;
                                CrashReport $$21 = CrashReport.forThrowable($$20, "Feature placement");
                                var10000 = $$21.addCategory("Feature");
                                Objects.requireNonNull($$19);
                                var10000.setDetail("Description", $$19::get);
                                throw new ReportedException($$21);
                            }
                        }
                    }

                    if ($$15 < $$12) {
                        IntSet $$22 = new IntArraySet();
                        var20 = $$11.iterator();

                        while(var20.hasNext()) {
                            Holder<Biome> $$23 = (Holder)var20.next();
                            List<HolderSet<PlacedFeature>> $$24 = ((BiomeGenerationSettings)this.generationSettingsGetter.apply($$23)).features();
                            if ($$15 < $$24.size()) {
                                HolderSet<PlacedFeature> $$25 = (HolderSet)$$24.get($$15);
                                FeatureSorter.StepFeatureData $$26 = (FeatureSorter.StepFeatureData)$$8.get($$15);
                                $$25.stream().map(Holder::value).forEach(($$2x) -> {
                                    $$22.add($$26.indexMapping().applyAsInt($$2x));
                                });
                            }
                        }

                        int $$27 = $$22.size();
                        int[] $$28 = $$22.toIntArray();
                        Arrays.sort($$28);
                        FeatureSorter.StepFeatureData $$29 = (FeatureSorter.StepFeatureData)$$8.get($$15);

                        for(int $$30 = 0; $$30 < $$27; ++$$30) {
                            int $$31 = $$28[$$30];
                            PlacedFeature $$32 = (PlacedFeature)$$29.features().get($$31);
                            Supplier<String> $$33 = () -> {
                                Optional var10000 = $$13.getResourceKey($$32).map(Object::toString);
                                Objects.requireNonNull($$32);
                                return (String)var10000.orElseGet($$32::toString);
                            };
                            $$9.setFeatureSeed($$10, $$31, $$15);

                            try {
                                $$0.setCurrentlyGenerating($$33);
                                $$32.placeWithBiomeCheck($$0, this, $$9, $$5);
                            } catch (Exception var30) {
                                Exception $$34 = var30;
                                CrashReport $$35 = CrashReport.forThrowable($$34, "Feature placement");
                                var10000 = $$35.addCategory("Feature");
                                Objects.requireNonNull($$33);
                                var10000.setDetail("Description", $$33::get);
                                throw new ReportedException($$35);
                            }
                        }
                    }
                }

                $$0.setCurrentlyGenerating((Supplier)null);
            } catch (Exception var31) {
                Exception $$36 = var31;
                CrashReport $$37 = CrashReport.forThrowable($$36, "Biome decoration");
                $$37.addCategory("Generation").setDetail("CenterX", $$3.x).setDetail("CenterZ", $$3.z).setDetail("Seed", $$10);
                throw new ReportedException($$37);
            }
        }
    }

    private static BoundingBox getWritableArea(ChunkAccess $$0) {
        ChunkPos $$1 = $$0.getPos();
        int $$2 = $$1.getMinBlockX();
        int $$3 = $$1.getMinBlockZ();
        LevelHeightAccessor $$4 = $$0.getHeightAccessorForGeneration();
        int $$5 = $$4.getMinBuildHeight() + 1;
        int $$6 = $$4.getMaxBuildHeight() - 1;
        return new BoundingBox($$2, $$5, $$3, $$2 + 15, $$6, $$3 + 15);
    }

    public abstract void buildSurface(WorldGenRegion var1, StructureManager var2, RandomState var3, ChunkAccess var4);

    public abstract void spawnOriginalMobs(WorldGenRegion var1);

    public int getSpawnHeight(LevelHeightAccessor $$0) {
        return 64;
    }

    public BiomeSource getBiomeSource() {
        return this.biomeSource;
    }

    public abstract int getGenDepth();

    public WeightedRandomList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> $$0, StructureManager $$1, MobCategory $$2, BlockPos $$3) {
        Map<Structure, LongSet> $$4 = $$1.getAllStructuresAt($$3);
        Iterator var6 = $$4.entrySet().iterator();

        while(var6.hasNext()) {
            Map.Entry<Structure, LongSet> $$5 = (Map.Entry)var6.next();
            Structure $$6 = (Structure)$$5.getKey();
            StructureSpawnOverride $$7 = (StructureSpawnOverride)$$6.spawnOverrides().get($$2);
            if ($$7 != null) {
                MutableBoolean $$8 = new MutableBoolean(false);
                Predicate<StructureStart> $$9 = $$7.boundingBox() == BoundingBoxType.PIECE ? ($$2x) -> {
                    return $$1.structureHasPieceAt($$3, $$2x);
                } : ($$1x) -> {
                    return $$1x.getBoundingBox().isInside($$3);
                };
                $$1.fillStartsForStructure($$6, (LongSet)$$5.getValue(), ($$2x) -> {
                    if ($$8.isFalse() && $$9.test($$2x)) {
                        $$8.setTrue();
                    }

                });
                if ($$8.isTrue()) {
                    return $$7.spawns();
                }
            }
        }

        return ((Biome)$$0.value()).getMobSettings().getMobs($$2);
    }

    public void createStructures(RegistryAccess $$0, ChunkGeneratorStructureState $$1, StructureManager $$2, ChunkAccess $$3, StructureTemplateManager $$4) {
        ChunkPos $$5 = $$3.getPos();
        SectionPos $$6 = SectionPos.bottomOf($$3);
        RandomState $$7 = $$1.randomState();
        $$1.possibleStructureSets().forEach(($$8) -> {
            StructurePlacement $$9 = ((StructureSet)$$8.value()).placement();
            List<StructureSet.StructureSelectionEntry> $$10 = ((StructureSet)$$8.value()).structures();
            Iterator var12 = $$10.iterator();

            while(var12.hasNext()) {
                StructureSet.StructureSelectionEntry $$11 = (StructureSet.StructureSelectionEntry)var12.next();
                StructureStart $$12 = $$2.getStartForStructure($$6, (Structure)$$11.structure().value(), $$3);
                if ($$12 != null && $$12.isValid()) {
                    return;
                }
            }

            if ($$9.isStructureChunk($$1, $$5.x, $$5.z)) {
                if ($$10.size() == 1) {
                    this.tryGenerateStructure((StructureSet.StructureSelectionEntry)$$10.get(0), $$2, $$0, $$7, $$4, $$1.getLevelSeed(), $$3, $$5, $$6);
                } else {
                    ArrayList<StructureSet.StructureSelectionEntry> $$13 = new ArrayList($$10.size());
                    $$13.addAll($$10);
                    WorldgenRandom $$14 = new WorldgenRandom(new LegacyRandomSource(0L));
                    $$14.setLargeFeatureSeed($$1.getLevelSeed(), $$5.x, $$5.z);
                    int $$15 = 0;

                    StructureSet.StructureSelectionEntry $$16;
                    for(Iterator var15 = $$13.iterator(); var15.hasNext(); $$15 += $$16.weight()) {
                        $$16 = (StructureSet.StructureSelectionEntry)var15.next();
                    }

                    while(!$$13.isEmpty()) {
                        int $$17 = $$14.nextInt($$15);
                        int $$18 = 0;

                        for(Iterator var17 = $$13.iterator(); var17.hasNext(); ++$$18) {
                            StructureSet.StructureSelectionEntry $$19 = (StructureSet.StructureSelectionEntry)var17.next();
                            $$17 -= $$19.weight();
                            if ($$17 < 0) {
                                break;
                            }
                        }

                        StructureSet.StructureSelectionEntry $$20 = (StructureSet.StructureSelectionEntry)$$13.get($$18);
                        if (this.tryGenerateStructure($$20, $$2, $$0, $$7, $$4, $$1.getLevelSeed(), $$3, $$5, $$6)) {
                            return;
                        }

                        $$13.remove($$18);
                        $$15 -= $$20.weight();
                    }

                }
            }
        });
    }

    private boolean tryGenerateStructure(StructureSet.StructureSelectionEntry $$0, StructureManager $$1, RegistryAccess $$2, RandomState $$3, StructureTemplateManager $$4, long $$5, ChunkAccess $$6, ChunkPos $$7, SectionPos $$8) {
        Structure $$9 = (Structure)$$0.structure().value();
        int $$10 = fetchReferences($$1, $$6, $$8, $$9);
        HolderSet<Biome> $$11 = $$9.biomes();
        Objects.requireNonNull($$11);
        Predicate<Holder<Biome>> $$12 = $$11::contains;
        StructureStart $$13 = $$9.generate($$2, this, this.biomeSource, $$3, $$4, $$5, $$7, $$10, $$6, $$12);
        if ($$13.isValid()) {
            $$1.setStartForStructure($$8, $$9, $$13, $$6);
            return true;
        } else {
            return false;
        }
    }

    private static int fetchReferences(StructureManager $$0, ChunkAccess $$1, SectionPos $$2, Structure $$3) {
        StructureStart $$4 = $$0.getStartForStructure($$2, $$3, $$1);
        return $$4 != null ? $$4.getReferences() : 0;
    }

    public void createReferences(WorldGenLevel $$0, StructureManager $$1, ChunkAccess $$2) {
        int $$3 = true;
        ChunkPos $$4 = $$2.getPos();
        int $$5 = $$4.x;
        int $$6 = $$4.z;
        int $$7 = $$4.getMinBlockX();
        int $$8 = $$4.getMinBlockZ();
        SectionPos $$9 = SectionPos.bottomOf($$2);

        for(int $$10 = $$5 - 8; $$10 <= $$5 + 8; ++$$10) {
            for(int $$11 = $$6 - 8; $$11 <= $$6 + 8; ++$$11) {
                long $$12 = ChunkPos.asLong($$10, $$11);
                Iterator var15 = $$0.getChunk($$10, $$11).getAllStarts().values().iterator();

                while(var15.hasNext()) {
                    StructureStart $$13 = (StructureStart)var15.next();

                    try {
                        if ($$13.isValid() && $$13.getBoundingBox().intersects($$7, $$8, $$7 + 15, $$8 + 15)) {
                            $$1.addReferenceForStructure($$9, $$13.getStructure(), $$12, $$2);
                            DebugPackets.sendStructurePacket($$0, $$13);
                        }
                    } catch (Exception var21) {
                        Exception $$14 = var21;
                        CrashReport $$15 = CrashReport.forThrowable($$14, "Generating structure reference");
                        CrashReportCategory $$16 = $$15.addCategory("Structure");
                        Optional<? extends Registry<Structure>> $$17 = $$0.registryAccess().registry(Registries.STRUCTURE);
                        $$16.setDetail("Id", () -> {
                            return (String)$$17.map(($$1x) -> {
                                return $$1x.getKey($$13.getStructure()).toString();
                            }).orElse("UNKNOWN");
                        });
                        $$16.setDetail("Name", () -> {
                            return BuiltInRegistries.STRUCTURE_TYPE.getKey($$13.getStructure().type()).toString();
                        });
                        $$16.setDetail("Class", () -> {
                            return $$13.getStructure().getClass().getCanonicalName();
                        });
                        throw new ReportedException($$15);
                    }
                }
            }
        }

    }

    public abstract CompletableFuture<ChunkAccess> fillFromNoise(Executor var1, Blender var2, RandomState var3, StructureManager var4, ChunkAccess var5);

    public abstract int getSeaLevel();

    public abstract int getMinY();

    public abstract int getBaseHeight(int var1, int var2, Heightmap.Types var3, LevelHeightAccessor var4, RandomState var5);

    public abstract NoiseColumn getBaseColumn(int var1, int var2, LevelHeightAccessor var3, RandomState var4);

    public int getFirstFreeHeight(int $$0, int $$1, Heightmap.Types $$2, LevelHeightAccessor $$3, RandomState $$4) {
        return this.getBaseHeight($$0, $$1, $$2, $$3, $$4);
    }

    public int getFirstOccupiedHeight(int $$0, int $$1, Heightmap.Types $$2, LevelHeightAccessor $$3, RandomState $$4) {
        return this.getBaseHeight($$0, $$1, $$2, $$3, $$4) - 1;
    }

    public abstract void addDebugScreenInfo(List<String> var1, RandomState var2, BlockPos var3);

    /** @deprecated */
    @Deprecated
    public BiomeGenerationSettings getBiomeGenerationSettings(Holder<Biome> $$0) {
        return (BiomeGenerationSettings)this.generationSettingsGetter.apply($$0);
    }

    static {
        CODEC = BuiltInRegistries.CHUNK_GENERATOR.byNameCodec().dispatchStable(ChunkGenerator::codec, Function.identity());
    }
}
