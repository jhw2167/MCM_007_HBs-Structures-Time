# Multiloader Mod Update Guide: 1.20.1 → 1.21.1 (Fabric, NeoForge + Common)

This guide documents every change required to update a multiloader Minecraft mod from 1.20.1 (Fabric/Forge) to 1.21.1 (Fabric + NeoForge — Forge is replaced). It assumes the mod depends on Balm and HBs_Foundation, uses the `multiloader-common` and `multiloader-loader` convention plugins from `buildSrc`, and follows the project structure: root build.gradle, Common subproject, Fabric subproject, NeoForge subproject, with a shared gradle.properties.

## Critical Principle: Overwrite, Don't Merge

The build.gradle files included in the `updatePack` directory must **overwrite** the existing build.gradle files in each respective subproject. Do not attempt to merge old and new build.gradle files — the build system structure has fundamentally changed (VanillaGradle/ForgeGradle → NeoForge ModDev, manual wiring → convention plugins).

Only two things from the previous build.gradle files must be preserved:

1. **Repositories** — any custom Maven repositories from the old root build.gradle must be copied into the new root build.gradle's `subprojects` block (e.g. Twelve Iterations for Balm, BlameJared for JEI).
2. **Subproject-specific dependencies** — each subproject's unique dependencies (e.g. `hbs_foundation`, mod-specific libs) must be added to the new build.gradle's `dependencies` block.

Everything else (plugins, Java toolchain, jar manifest, processResources, publishing, source wiring) is handled by the convention plugins and the new build.gradle templates.

---

## 1. Copy buildSrc from HBs_Foundation

The project requires the `multiloader-common` and `multiloader-loader` convention plugins. Copy the entire `buildSrc/` directory from HBs_Foundation into the target mod's root. This provides:

- `multiloader-common.gradle` — handles java-library, maven-publish, Java toolchain, jar manifest, processResources expansion, publishing, and outgoing capability declarations.
- `multiloader-loader.gradle` — applies `multiloader-common`, then wires Common's source and resources into the loader subproject via `commonJava`/`commonResources` configurations.

Since the convention plugins handle processResources, jar configuration, publishing, and source wiring, the root and subproject build.gradle files become much simpler.

### Audit `expandProps` against your resource templates

`buildSrc/src/main/groovy/multiloader-common.gradle` defines an `expandProps` map applied to `processResources`. Every `${...}` placeholder in your mod's resource templates (`fabric.mod.json`, `neoforge.mods.toml`, `pack.mcmeta`, mixin JSONs) must have a matching key in `expandProps`. Common entries:

- `mod_id`, `mod_name`, `mod_version`, `mod_authors`, `mod_description`
- `credits`
- `minecraft_version`, `minecraft_version_range`, `java_version`
- `neoforge_version`, `neoforge_version_range`, `neoforge_loader_version_range`
- `fabric_version`, `fabric_loader_version`
- `balm_version`, `balm_version_range`
- `foundation_version`, `foundation_version_min`

If a template references a property not in `expandProps`, Gradle fails with `Could not find property`.

After copying buildSrc, **remove the `processResources` block from the root build.gradle entirely** — repositories stay, processResources is now handled by the convention plugin.

---

## 2. Gradle Wrapper (gradle/wrapper/gradle-wrapper.properties)

Update the Gradle distribution to 8.12:

```
distributionUrl=https\://services.gradle.org/distributions/gradle-8.12-bin.zip
```

### If you hit "mod compiled with Loom 1.14 but using Loom 1.8" (and 1.14 doesn't exist on plugin portal)

A transitive mod was built against a newer Loom plugin than yours. Upgrade Gradle and Loom together:

```
# gradle-wrapper.properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.1.2-bin.zip
```

```properties
# gradle.properties
loom_version=1.14-SNAPSHOT
```

Run `./gradlew --refresh-dependencies clean build` after switching so the new Loom snapshot is fetched.

---

## 3. gradle.properties

### Authoritative version values for 1.21.1:

```properties
# Common
java_version=21
minecraft_version=1.21.1
minecraft_version_range=[1.21.1, 1.22)

# NeoForge
neoforge_version=21.1.8
neoforge_version_range=[21-beta,)
neoforge_loader_version_range=[1,)
neo_form_version=1.21.1-20240808.144430
parchment_minecraft=1.21
parchment_version=2024.06.23

# Fabric
fabric_version=0.116.11+1.21.1
fabric_loader_version=0.17.3
loom_version=1.8-SNAPSHOT

# Dependencies
balm_version=21.0.56+1.21.1
balm_version_range=[21.0.56,)
foundation_version=1.7.0-SNAPSHOT
foundation_version_min=1.7.0
```

### Mod metadata properties to add:

```properties
# Mod options
credits=Holy_Buckets
```

- **`credits`** — required by the `multiloader-common` convention plugin's processResources expandProps.
- **`foundation_version_min`** — declare the minimum supported HBs_Foundation version; referenced by mod metadata templates.
- **`neo_form_version`**, **`parchment_minecraft`**, **`parchment_version`** — required by Common's NeoForge ModDev configuration.

### Forge removal

Delete all `forge_*` properties (`forge_version`, `forge_version_range`, `forge_loader_version_range`). Forge is no longer supported in this multiloader template — NeoForge has replaced it.

### Key points:
- Remove any commented-out old `balm_version` lines.
- The `version` property should NOT be in gradle.properties. The root build.gradle sets `version = mod_version` to derive the project version from `mod_version`. Having both causes conflicts.

---

## 4. settings.gradle

### Plugin repositories — replace old Forge maven with NeoForge:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            name = 'Fabric'
            url = 'https://maven.fabricmc.net/'
        }
        maven {
            name = 'NeoForge'
            url = 'https://maven.neoforged.net/releases'
        }
        maven {
            name = 'Sponge Snapshots'
            url = 'https://repo.spongepowered.org/repository/maven-public/'
        }
    }
}
```

### Subproject includes — swap forge for neoforge:

```groovy
include("common")
include("fabric")
include("neoforge")
// 'forge' subproject removed — NeoForge replaces it in 1.21.1
```

---

## 5. Root build.gradle — Overwrite Entirely

Replace the entire root build.gradle. The convention plugins now handle most of what the old subprojects block did. The root file only needs to:

1. Declare the top-level plugins (Fabric Loom and NeoForge ModDev, both `apply(false)`).
2. Set `version = mod_version`.
3. Configure allprojects Javadoc settings.
4. In the subprojects block: declare shared repositories and shared dependencies.

**The new root build.gradle:**

```groovy
plugins {
    // see https://fabricmc.net/develop/ for new versions
    id 'fabric-loom' version "${loom_version}" apply(false)
    // see https://projects.neoforged.net/neoforged/moddevgradle for new versions
    id 'net.neoforged.moddev' version '2.0.49-beta' apply(false)
}

version = mod_version

allprojects {
    apply plugin: "idea"

    tasks.withType(Javadoc).configureEach {
        options.addStringOption('Xdoclint:none', '-quiet')
        options.encoding = 'UTF-8'
    }
}

subprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        // ADD YOUR PROJECT'S CUSTOM REPOSITORIES HERE
        // Copy any custom Maven repos from the old root build.gradle.
        // Common repos needed by most HB mods:
        maven {
            url "https://maven.twelveiterations.com/repository/maven-public/"
            content {
                includeGroup "net.blay09.mods"
            }
        }
    }

    plugins.withType(JavaPlugin) {
        dependencies {
            implementation "com.google.code.gson:gson:${gson_version}"
            implementation "org.antlr:antlr4-runtime:${antlr_version}"
            implementation "org.xerial:sqlite-jdbc:${sqlite_version}"
        }
    }

    tasks.withType(JavaCompile).configureEach {
        it.options.encoding = 'UTF-8'
    }
}
```

### What was removed vs. the old root build.gradle:
- **Plugins:** ForgeGradle, VanillaGradle, and Mixin plugins are gone. Replaced by `fabric-loom` and `net.neoforged.moddev`.
- **`plugins.withType(JavaPlugin)` block:** No longer configures jar manifest, sourcesJar, Java toolchain, or `withSourcesJar()`/`withJavadocJar()` — the `multiloader-common` convention plugin handles all of this.
- **`processResources` block:** Entirely removed from root. The `multiloader-common` convention plugin handles resource expansion with its own expandProps map.
- **Hardcoded `JavaVersion.VERSION_17`:** replaced by the convention plugin's `JavaLanguageVersion.of(java_version.toInteger())`, which honors `java_version=21` from gradle.properties. Audit the old root for any hard-coded `17` literal and remove it.
- **Shared dependencies:** Only truly shared deps (gson, antlr, sqlite) remain. Balm and foundation are declared per-subproject.

### Why `mavenLocal()` matters:
HBs_Foundation is published to the local Maven repository (`~/.m2/repository`) via `publishToMavenLocal`. Without `mavenLocal()` in the repositories block, Gradle cannot find it. If dependency resolution fails with "Could not find com.holybuckets.foundation:...", confirm that `mavenLocal()` is present and that you ran `publishToMavenLocal` on the foundation project.

---

## 6. Common/build.gradle — Overwrite with Template

Overwrite `Common/build.gradle` with the template from `updatePack/common-build.gradle`, then add the mod's specific dependencies.

**The new Common/build.gradle:**

```groovy
plugins {
    id 'multiloader-common'
    id 'net.neoforged.moddev'
}

neoForge {
    neoFormVersion = neo_form_version
    // Automatically enable AccessTransformers if the file exists
    def at = file('src/main/resources/META-INF/accesstransformer.cfg')
    if (at.exists()) {
        accessTransformers.from(at.absolutePath)
    }
    parchment {
        minecraftVersion = parchment_minecraft
        mappingsVersion = parchment_version
    }
}

dependencies {
    compileOnly group: 'org.spongepowered', name: 'mixin', version: '0.8.5'
    implementation group: 'com.google.code.findbugs', name: 'jsr305', version: '3.0.1'

    implementation("net.blay09.mods:balm-common:${balm_version}") {
        changing = true
    }

    // ADD YOUR MOD'S COMMON DEPENDENCIES HERE
    // Example for a mod that depends on HBs_Foundation:
    implementation("com.holybuckets.foundation:hbs_foundation-common-${minecraft_version}:${foundation_version}") {
        changing = true
    }
}

configurations {
    commonJava {
        canBeResolved = false
        canBeConsumed = true
    }
    commonResources {
        canBeResolved = false
        canBeConsumed = true
    }
}

artifacts {
    commonJava sourceSets.main.java.sourceDirectories.singleFile
    commonResources sourceSets.main.resources.sourceDirectories.singleFile
}
```

### What changed:
- **`multiloader-common` replaces `idea`, `java`, `maven-publish`** — the convention plugin applies all three plus configures Java toolchain, jar manifest, processResources, publishing, and capabilities.
- **`net.neoforged.moddev` replaces `org.spongepowered.gradle.vanilla`** — NeoForm (via ModDev) is the new tool for deobfuscating Minecraft sources. This is build tooling, not mod loader support.
- **No `base {}` block** — the convention plugin sets `archivesName` using `mod_id`, `project.name`, and `minecraft_version`.
- **No `publishing {}` block** — the convention plugin configures publishing.
- **`commonJava`/`commonResources` configurations** — these expose Common's sources to loader subprojects via the `multiloader-loader` plugin.

### Carrying over old dependencies

From the old Common/build.gradle, copy any `implementation`, `compileOnly`, or `modImplementation` declarations specific to your mod (notably your `hbs_foundation-common` dependency, plus mod-specific libs from oreCluster, Balm extensions, etc.). The new template only seeds Balm and a placeholder foundation line; everything mod-specific must be merged in by hand.

---

## 7. Fabric/build.gradle — Overwrite with Template

Overwrite `Fabric/build.gradle` with the template from `updatePack/fabric-build.gradle`, then add the mod's specific dependencies.

**The new Fabric/build.gradle:**

```groovy
plugins {
    id 'multiloader-loader'
    id 'fabric-loom'
}

version = "${mod_version}"

dependencies {
    minecraft "com.mojang:minecraft:${minecraft_version}"
    mappings loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${parchment_minecraft}:${parchment_version}@zip")
    }
    modImplementation "net.fabricmc:fabric-loader:${fabric_loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${fabric_version}"

    modImplementation("net.blay09.mods:balm-fabric:${balm_version}") {
        transitive = false
    }

    // ADD YOUR MOD'S FABRIC DEPENDENCIES HERE
    // Example for a mod that depends on HBs_Foundation:
    modImplementation("com.holybuckets.foundation:hbs_foundation-fabric-${minecraft_version}:${foundation_version}") {
        transitive = false
        changing = true
    }

    implementation group: 'com.google.code.findbugs', name: 'jsr305', version: '3.0.1'
}

loom {
    def aw = project(':common').file("src/main/resources/${mod_id}.accesswidener")
    if (aw.exists()) {
        accessWidenerPath.set(aw)
    }
    mixin {
        defaultRefmapName.set("${mod_id}.refmap.json")
    }
    runs {
        client {
            client()
            setConfigName('Fabric Client')
            ideConfigGenerated(true)
            runDir('runs/client')
        }
        server {
            server()
            setConfigName('Fabric Server')
            ideConfigGenerated(true)
            runDir('runs/server')
        }
    }
}
```

### What changed:
- **`multiloader-loader` replaces manual Common wiring** — the old `implementation project(":common")`, `source(project(":common").sourceSets.main.allSource)`, `from project(":common").sourceSets.main.resources`, etc. are all gone. The convention plugin handles all cross-project source/resource wiring via `commonJava`/`commonResources` configurations.
- **No `publishing {}` block** — handled by the convention plugin.
- **No `tasks.withType(JavaCompile)` source inclusion** — handled by the convention plugin.
- **No `tasks.named("sourcesJar")` from common** — handled by the convention plugin.
- **Parchment mappings** — `loom.layered` with Parchment overlay replaces plain `loom.officialMojangMappings()`.
- **Run directories** — separated into `runs/client` and `runs/server` instead of a shared `run/` directory.

---

## 8. NeoForge/build.gradle — Rename forge/ and Overwrite

The old `forge/` subdirectory must be renamed to `neoforge/`. The new NeoForge build.gradle can be copied almost verbatim from a working reference mod (oreCluster's `neoforge/build.gradle` is the recommended template) — just merge in your mod's specific dependencies and update the `pid`/`fid` properties in `gradle.properties` to their NeoForge variants.

### Rename and seed the subproject

```bash
mv forge neoforge
# overwrite neoforge/build.gradle with the orecluster reference
# carry over any mod-specific dependencies from the old forge/build.gradle
```

Update any `gradle/runs` references that pointed to `forge` to reference `neoforge` instead.

### Find/replace `minecraftforge` → `neoforged` across NeoForge sources

Forge-specific package imports (`net.minecraftforge.*`) must become NeoForge (`net.neoforged.*`). Use the project's bundled helper script:

```bash
./folderStreamEditRecurse.sh MCM_000_HBs-Foundation/neoforge/src/main/java/com/holybuckets/ minecraftforge neoforged
```

This rewrites every occurrence of `minecraftforge` to `neoforged` recursively. Audit imports manually afterward — some classes moved package even within NeoForge (e.g. `Mod.EventBusSubscriber` → top-level `@EventBusSubscriber` in `net.neoforged.fml.common.EventBusSubscriber`).

### Update mod metadata files

- **`META-INF/mods.toml`** → rename to **`META-INF/neoforge.mods.toml`**. NeoForge introduced TOML schema tweaks; audit any `[[dependencies.MODID]]` blocks.
- **`pack.mcmeta`** — `pack_format` value for 1.21.1 is `34`.
- **Mixin config JSON** — verify the mixin plugin reference doesn't point to a Forge-specific class.

### Platform helper class

`ForgePlatformHelper.java` (or your equivalent) — change imports only; method bodies stay:

- `net.minecraftforge.fml.ModList` → `net.neoforged.fml.ModList`
- `net.minecraftforge.fml.loading.FMLLoader` → `net.neoforged.fml.loading.FMLLoader`

`ModList.get().isLoaded(...)` and `FMLLoader.isProduction()` exist on the NeoForge equivalents with identical signatures.

---

## 9. Java Code Migration (Vanilla API Changes)

Beyond build system changes, 1.21.1 ships sweeping vanilla API renames. Apply these across every subproject's Java sources.

### 9.1 ResourceLocation construction

The public constructor `new ResourceLocation(namespace, path)` is removed. Use one of:

```java
// Preferred — HBs_Foundation helper:
HBUtil.LOC(Constants.MOD_ID, "my_thing");

// Vanilla factory:
ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "my_thing");

// Single-string form:
ResourceLocation.parse("modid:my_thing");
```

Find/replace `new ResourceLocation(` → `HBUtil.LOC(` (or `ResourceLocation.fromNamespaceAndPath(`) project-wide.

### 9.2 ChunkStatus moved

```java
// 1.20.1:
import net.minecraft.world.level.chunk.ChunkStatus;
// 1.21.1:
import net.minecraft.world.level.chunk.status.ChunkStatus;
```

### 9.3 Block#use rewritten

The single `use(...)` method is split in two and the return type changed:

| 1.20.1 | 1.21.1 |
|--------|--------|
| `public InteractionResult use(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)` | `public InteractionResult useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)` |
| (same `use` handles item-in-hand) | `public ItemInteractionResult useItemOn(ItemStack, BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)` |

- Use `useItemOn` when the interaction depends on the player's held item (e.g. opening a menu only when the player is holding a specific item, or placing/removing items in a slot).
- Use `useWithoutItem` for empty-hand interactions.
- `useItemOn` returns `ItemInteractionResult` (new enum: `SUCCESS`, `CONSUME`, `PASS_TO_DEFAULT_BLOCK_INTERACTION`, `SKIP_DEFAULT_BLOCK_INTERACTION`, `FAIL`).

### 9.4 BlockBehaviour copy

```java
// 1.20.1:
BlockBehaviour.Properties.copy(Blocks.STONE);
// 1.21.1:
BlockBehaviour.Properties.ofFullCopy(Blocks.STONE);
```

### 9.5 BlockEntity load/save signature changes

```java
// 1.20.1:
@Override public void load(CompoundTag tag) { ... }
@Override protected void saveAdditional(CompoundTag tag) { ... }
@Override public CompoundTag getUpdateTag() { ... }
```

```java
// 1.21.1:
@Override public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { ... }
@Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { ... }
@Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { ... }
```

- `load` → `loadAdditional` (and `super.load(tag)` becomes `super.loadAdditional(tag, registries)`).
- Both `saveAdditional` and `loadAdditional` gain a `HolderLookup.Provider registries` parameter.
- `getUpdateTag()` gains the same `registries` parameter.
- When you need a `HolderLookup.Provider` outside these overrides, pull it from `be.getLevel().registryAccess()`.

Pass `registries` through when calling sibling overrides:

```java
@Override
public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    CompoundTag tag = super.getUpdateTag(registries);
    this.saveAdditional(tag, registries);
    return tag;
}
```

And for inter-BE NBT shuttling (e.g. moving a block entity in code):

```java
CompoundTag nbt = oldBE.saveWithFullMetadata(oldBE.getLevel().registryAccess());
// ...
newBE.loadAdditional(nbt, newBE.getLevel().registryAccess());
```

### 9.6 BaseEntityBlock codec override

Any subclass of `BaseEntityBlock` (or anything compiled against it) must override `codec()`:

```java
@Override
protected MapCodec<? extends BaseEntityBlock> codec() {
    return null;
}
```

Returning `null` is acceptable if you don't intend to serialize the block via vanilla codec paths. If you do, supply a real `MapCodec`.

### 9.7 BlockEntity#onDestroyed returns BlockState

The lifecycle methods around block destruction now return `BlockState` (the resulting state after destruction). Audit any override of `onDestroyed`-shaped methods; the new signature reflects the destroyed block's final state and may need a `return state;` at the end.

### 9.8 Networking — CustomPacketPayload

Every custom packet must now implement `CustomPacketPayload`:

```java
public class MyMessage implements CustomPacketPayload {

    public static final String LOCATION = "my_message";

    public static final CustomPacketPayload.Type<MyMessage> TYPE =
        new CustomPacketPayload.Type<>(HBUtil.LOC(Constants.MOD_ID, LOCATION));

    public static final StreamCodec<RegistryFriendlyByteBuf, MyMessage> STREAM_CODEC =
        CustomPacketPayload.codec(Codecs::encode, Codecs::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
```

Three requirements:
1. Implement `CustomPacketPayload`.
2. Expose a static `CustomPacketPayload.Type<MyMessage> TYPE` built from a `ResourceLocation`.
3. Expose a static `StreamCodec<RegistryFriendlyByteBuf, MyMessage> STREAM_CODEC` constructed via `CustomPacketPayload.codec(encoder, decoder)`.

Encoder/decoder methods retain their pre-1.21 signatures (`(Message, FriendlyByteBuf) → FriendlyByteBuf` and `FriendlyByteBuf → Message`); `CustomPacketPayload.codec(...)` accepts these via subtyping (the encoder return value is discarded; `RegistryFriendlyByteBuf extends FriendlyByteBuf`).

Registration with Balm changes from `(ResourceLocation, class, encoder, decoder, handler)` to `(TYPE, class, STREAM_CODEC, handler)`:

```java
networking.registerClientboundPacket(
    MyMessage.TYPE,
    MyMessage.class,
    MyMessage.STREAM_CODEC,
    Handlers::handle
);
```

### 9.9 Forge → NeoForge `@Mod` entry point

```java
// 1.20.1 Forge:
@Mod(Constants.MOD_ID)
public class MyMainForge {
    public MyMainForge() {
        Balm.initialize(Constants.MOD_ID, CommonClass::init);
        DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> BalmClient.initialize(Constants.MOD_ID, MyMainForgeClient::clientInit));
    }
}
```

```java
// 1.21.1 NeoForge:
@Mod(Constants.MOD_ID)
public class MyMainForge {
    public MyMainForge(IEventBus modEventBus) {
        final var context = new NeoForgeLoadContext(modEventBus);
        Balm.initialize(Constants.MOD_ID, context, CommonClass::init);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            BalmClient.initialize(Constants.MOD_ID, MyMainForgeClient::clientInit);
        }
    }
}
```

Key changes:
- The `@Mod` constructor now takes an injected `IEventBus modEventBus` (and optionally `ModContainer`, `Dist`).
- `Balm.initialize(modId, context, initRunnable)` — Balm now requires a `NeoForgeLoadContext` wrapping the mod event bus.
- `DistExecutor.runWhenOn` is gone — use a direct `FMLEnvironment.dist == Dist.CLIENT` check.
- ClientMain initialization should happen **inside the main `@Mod` class's constructor** (not as a separate `@Mod` class) so the mod event bus is captured.

### 9.10 Mod event subscribers

```java
// 1.20.1 Forge:
@Mod.EventBusSubscriber(modid = Constants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
// 1.21.1 NeoForge:
@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
```

`Mod.EventBusSubscriber` was promoted to the top-level `net.neoforged.fml.common.EventBusSubscriber` annotation. The `Bus.MOD`/`Bus.GAME` enum constants moved with it.

---

## 10. Balm Menus — StreamCodec Migration

Balm 21.x requires every screen-opening menu to define a `StreamCodec` for the data passed from server to client when the menu opens. The pre-1.21 pattern of overriding `writeToBufOpening`/`readBufOpening` is gone — replaced by a typed `Data` record + `StreamCodec`. Whatever fields you wrote into the buffer before now become fields on the `Data` record.

### 10.1 Add a Data record + STREAM_CODEC to your menu class

```java
public class MyMenu extends AbstractContainerMenu {

    public record Data(BlockPos pos /*, more fields */) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, MyMenu.Data> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC.cast(),
            MyMenu.Data::pos,
            MyMenu.Data::new);

    // rest of menu...
}
```

**Why `composite`, not `of`:**
- `StreamCodec.of(encoder, decoder)` takes exactly two arguments — a manual encoder + decoder. Use only for hand-rolled codecs.
- `StreamCodec.composite(...)` is the record-style helper: pass `(fieldCodec, getter, fieldCodec, getter, ..., constructor)`. Arity must match your record's fields.

**Why `.cast()`:**
`BlockPos.STREAM_CODEC` is typed `StreamCodec<ByteBuf, BlockPos>`. `.cast()` widens the buffer parameter so it lines up with `RegistryFriendlyByteBuf` (a subtype of `ByteBuf`). Any field codec typed against `ByteBuf` or `FriendlyByteBuf` needs the same `.cast()` to compose into a `RegistryFriendlyByteBuf` codec.

### 10.2 Multi-field example

For a record with five fields (mirrors waystones' `WaystoneEditMenu`):

```java
public record Data(BlockPos pos, Waystone waystone, int modifierCount,
                   Optional<Component> error, List<WaystoneVisibility> visibilityOptions) {}

public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
    BlockPos.STREAM_CODEC.cast(),                 Data::pos,
    WaystoneImpl.STREAM_CODEC,                    Data::waystone,
    ByteBufCodecs.INT,                            Data::modifierCount,
    ComponentSerialization.OPTIONAL_STREAM_CODEC, Data::error,
    WaystoneVisibility.LIST_STREAM_CODEC,         Data::visibilityOptions,
    Data::new
);
```

Built-in field codecs to know:
- Primitives: `ByteBufCodecs.INT`, `ByteBufCodecs.BOOL`, `ByteBufCodecs.STRING_UTF8`, etc.
- `Component`: `ComponentSerialization.STREAM_CODEC` or `ComponentSerialization.OPTIONAL_STREAM_CODEC`.
- Optionals: wrap any codec with `ByteBufCodecs.optional(inner)`.
- Lists: many built-in types expose `LIST_STREAM_CODEC`; otherwise use `ByteBufCodecs.list(inner)`.
- Custom types: expose your own `STREAM_CODEC` static field, then reference it directly.

### 10.3 Wire ModMenus factory to consume Data

The second generic parameter of `BalmMenuFactory` changes from `Object` to your typed `Data` record. `getStreamCodec()` must return the real codec (not `null`).

```java
myMenu = menus.registerMenu(id("my_menu"),
    new BalmMenuFactory<MyMenu, MyMenu.Data>() {
        @Override
        public MyMenu create(int i, Inventory inv, MyMenu.Data data) {
            BlockPos pos = data.pos();
            Level level = inv.player.level();
            if (level.getBlockEntity(pos) instanceof MyBlockEntity be) {
                be.setLevel(level);
                return new MyMenu(i, inv, be);
            }
            return null;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MyMenu.Data> getStreamCodec() {
            return MyMenu.STREAM_CODEC;
        }
    });
```

Returning `null` from `getStreamCodec()` (the pre-migration placeholder) means the menu never actually syncs `Data` to the client — every registration must return the real codec.

### 10.4 BlockEntity menu provider returns typed Data

```java
public BalmMenuProvider getMenuProvider() {
    return new BalmMenuProvider() {
        @Override
        public MyMenu.Data getScreenOpeningData(ServerPlayer player) {
            return new MyMenu.Data(worldPosition);
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MyMenu.Data> getScreenStreamCodec() {
            return MyMenu.STREAM_CODEC;
        }

        @Override
        public Component getDisplayName() {
            return Component.translatable("menu.modid.my_menu");
        }

        @Override
        public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
            MyBlockEntity.this.setLevel(player.level());
            return new MyMenu(syncId, inv, MyBlockEntity.this);
        }
    };
}
```

Changes vs. the pre-1.21 provider:
- `getScreenOpeningData` returns the typed `Data` record (not `Object`, not raw `BlockPos`).
- `getScreenStreamCodec()` returns the typed `StreamCodec<RegistryFriendlyByteBuf, Data>` (not raw `StreamCodec`).
- Call sites (`Balm.getNetworking().openMenu(player, menuProvider)`) need no change — Balm pulls the codec and opening data off the provider.

### 10.5 End-to-end flow

1. Server: `BlockEntity.getMenuProvider()` produces `new Data(worldPos)`.
2. Balm encodes it via `MyMenu.STREAM_CODEC` and ships it to the client.
3. Client: Balm decodes back into `Data`.
4. `ModMenus` factory's `create(int, Inventory, Data)` extracts `data.pos()` and resolves the BE locally.

### 10.6 Migration checklist for each menu

- [ ] Add `public record Data(...) {}` mirroring the fields you previously wrote into the buffer.
- [ ] Add `public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(...)`.
- [ ] Change `BalmMenuFactory<Menu, Object>` → `BalmMenuFactory<Menu, Menu.Data>` in `ModMenus`.
- [ ] Replace `(BlockPos) object` cast in `create(...)` with `data.pos()` (and `data.field()` for every other field).
- [ ] Make `getStreamCodec()` return `Menu.STREAM_CODEC` (was `null`).
- [ ] Change provider's `getScreenOpeningData(...)` to return `new Menu.Data(...)`.
- [ ] Change provider's `getScreenStreamCodec()` return type to the typed `StreamCodec<RegistryFriendlyByteBuf, Menu.Data>`.
- [ ] Delete any `writeToBufOpening`/`readBufOpening` overrides — they're no longer called.

---

## 11. Dependency Naming Convention Change (Important)

The HBs_Foundation artifacts were renamed from PascalCase to lowercase in the 1.21.1 release. Every reference to the foundation dependency must be updated:

| Context | Old artifact ID | New artifact ID |
|---------|----------------|-----------------|
| Common  | `HBs_Foundation-common-${minecraft_version}` | `hbs_foundation-common-${minecraft_version}` |
| Fabric  | `HBs_Foundation-fabric-${minecraft_version}` | `hbs_foundation-fabric-${minecraft_version}` |
| NeoForge | `HBs_Foundation-forge-${minecraft_version}` | `hbs_foundation-neoforge-${minecraft_version}` |

The group ID (`com.holybuckets.foundation`) and version property (`foundation_version`) remain unchanged.

---

## 12. Common Errors and Fixes

### "Could not find method processResources()" on Common
The old `processResources { ... }` call in the root build.gradle fails because NeoForge ModDev does not register that task the same way during evaluation. **Fix:** The `multiloader-common` convention plugin handles processResources. Remove any `processResources` or `tasks.withType(ProcessResources)` configuration from the root build.gradle entirely.

### "Cannot resolve project :common" from Fabric
This happens when Fabric's build.gradle uses `implementation project(":common")` directly. Common uses NeoForge ModDev (NeoForm mappings) and Fabric uses Loom (Mojang mappings) — the compiled artifacts are incompatible. **Fix:** Use `multiloader-loader` convention plugin instead of manual project wiring. The convention plugin consumes Common's raw source via `commonJava`/`commonResources` configurations and recompiles it under Loom's mappings.

### HBs_Foundation "version is unspecified"
Fabric reports a dependency's version as unspecified when that mod's own `fabric.mod.json` has an unexpanded `${version}` placeholder. **Fix:** Ensure the foundation project's processResources expandProps map includes a `"version"` key (or `"mod_version"` if you've standardized on that), and that the `fabric.mod.json` template uses the matching placeholder. The property must be defined somewhere Gradle can resolve it — either in `gradle.properties` or set programmatically in build.gradle (e.g. `version = mod_version`).

### Common and NeoForge subprojects publish with `version=unspecified`
Symptom: after the update, the Common and NeoForge JARs/POMs (and the published Maven artifacts) carry `version: unspecified`, while Fabric is correct. This happens because the `fabric-build.gradle` and `neoforge-build.gradle` templates set `version = "${mod_version}"` at the top of the subproject build.gradle, but the `common-build.gradle` template **does not**, and the root build.gradle only sets `version = mod_version` on the **root** project — Gradle does not propagate that to subprojects automatically.

The `multiloader-common` convention plugin's capability declarations (`capability("$group:...:$mod_version")`) read `mod_version` straight from `gradle.properties`, so they look fine, but `project.version` (used by `jar`, `sourcesJar`, `mavenJava` publication, and the `processResources` `'version' : mod_version` expansion target) stays at Gradle's default `"unspecified"`.

**Fix — apply both layers:**

1. In **`common/build.gradle`**, add right after the `plugins { ... }` block:

```groovy
version = "${mod_version}"
```

2. In the **root `build.gradle`**'s `subprojects { ... }` block, add as the first line so any current or future subproject inherits a real version even if its own build.gradle forgets to set it:

```groovy
subprojects {
    version = mod_version
    // ...rest of subprojects block
}
```

Re-run `./gradlew clean build publishToMavenLocal` and confirm the produced POMs under `~/.m2/repository/com/holybuckets/<modid>/...` carry the actual version, not `unspecified`. Apply this fix to every mod updated with this pack (Foundation, Satellites, compat mods, structures mods, etc.) — the Common-template omission affects all of them.

### fabric-resource-loader-v0 mixin crash (LanguageMixin injection failure)
Fabric API's `fabric-resource-loader-v0` sub-module version 2.0.0 is incompatible with MC 1.21.1. This happens when Fabric Loom resolves an incorrect version of this sub-module. **Fix:** Ensure `loom_version` is set to `1.8-SNAPSHOT` (or the version recommended at [fabricmc.net/develop](https://fabricmc.net/develop/)). Stale or mismatched Loom snapshots can decompose the Fabric API BOM into wrong sub-module versions. After changing Loom version, run a clean build (`./gradlew clean build`).

### "mod was compiled with Loom 1.14 but you are using Loom 1.8" — and 1.14 doesn't exist
A transitive mod was built against newer Loom than your project. **Fix:** Upgrade Gradle to 9.1.2 (in `gradle-wrapper.properties`) AND bump `loom_version=1.14-SNAPSHOT`. Older Gradle won't accept Loom 1.14.

### "Could not find com.holybuckets.foundation:hbs_foundation-..." dependency
The foundation jar exists in `~/.m2/repository` but Gradle can't find it. **Fix:** Ensure `mavenLocal()` is in the repositories block (either in root build.gradle's `subprojects {}` or in the convention plugin). Then confirm the foundation project was published with `./gradlew publishToMavenLocal` (not just a jar copy — Gradle needs the `.pom` and `maven-metadata.xml` files).

### Mixin registered in both "mixins" and "client" arrays
If a mixin targets a client-only class (like `Minecraft.class`), it must only appear in the `"client"` array of the mixins JSON, not in `"mixins"` (which applies on both sides). Having it in both causes duplicate registration and can prevent the mixin from applying. **Fix:** Remove the mixin from the `"mixins"` array and keep it only in `"client"`.

### "ResourceLocation has no public constructor"
The `new ResourceLocation(ns, path)` constructor is gone. **Fix:** Use `HBUtil.LOC(ns, path)` or `ResourceLocation.fromNamespaceAndPath(ns, path)`.

### "Cannot find symbol DistExecutor"
NeoForge removed `DistExecutor`. **Fix:** Use a direct `FMLEnvironment.dist == Dist.CLIENT` check, or split client/server entry points by package.

### "Cannot find symbol method use(BlockState, ...)" on a Block subclass
Block's `use` was split. **Fix:** Override `useItemOn` (returns `ItemInteractionResult`) for item-dependent interactions or `useWithoutItem` (returns `InteractionResult`) for empty-hand interactions.

### "load(CompoundTag) cannot be applied" / "missing HolderLookup.Provider parameter"
The BE serialization methods now take a `HolderLookup.Provider` second parameter. **Fix:** Rename `load` → `loadAdditional` and add the parameter. Inside another method, get the provider via `be.getLevel().registryAccess()`.

### Menu opens but client receives no data / NullPointerException on `Data.pos()`
The menu's `BalmMenuFactory.getStreamCodec()` returned `null`. **Fix:** Return the real `StreamCodec` from the menu class (`MyMenu.STREAM_CODEC`).

### `StreamCodec.of(...)` won't compile with three arguments
`StreamCodec.of` only takes two arguments (encoder, decoder). **Fix:** Use `StreamCodec.composite(fieldCodec, getter, ..., constructor)` for record-style codecs.

---

## 13. Checklist

When updating a mod, work through these steps in order:

1. **`buildSrc/`** — copy from HBs_Foundation; audit `expandProps` for every property your resource templates reference.
2. **`gradle/wrapper/gradle-wrapper.properties`** — Gradle 8.12 (or 9.1.2 if hitting the Loom 1.14 mismatch).
3. **`gradle.properties`** — set all 1.21.1 versions per Section 3; add `credits`, `foundation_version_min`, NeoForge/NeoForm/Parchment properties; delete all `forge_*` properties; ensure `version` is NOT defined (use `mod_version` only).
4. **`settings.gradle`** — swap Forge repo for NeoForge repo; include `neoforge` subproject, drop `forge`.
5. **`build.gradle` (root)** — overwrite with template; add custom repositories from old file; remove processResources block; remove any hardcoded `JavaVersion.VERSION_17`.
6. **`Common/build.gradle`** — overwrite with template; carry over Balm and foundation dependencies from the old file; **add `version = "${mod_version}"` right after the `plugins {}` block** (the common template omits it, unlike fabric/neoforge — see "Common and NeoForge subprojects publish with version=unspecified" in §12). Also add `version = mod_version` to the root `subprojects {}` block as a fallback.
7. **`Fabric/build.gradle`** — overwrite with template; add mod-specific dependencies; lowercase artifact names.
8. **`forge/` → `neoforge/`** — rename folder, overwrite build.gradle from oreCluster reference, run `folderStreamEditRecurse.sh` to swap `minecraftforge` → `neoforged`, audit imports.
9. **Update mod metadata** — `mods.toml` → `neoforge.mods.toml`; `pack.mcmeta` pack_format=34; mixin config sanity check; platform helper imports.
10. **Java code migration** — ResourceLocation factories, ChunkStatus import path, Block#useItemOn/useWithoutItem, BlockBehaviour.Properties.ofFullCopy, BlockEntity loadAdditional/saveAdditional/getUpdateTag with HolderLookup.Provider, BaseEntityBlock codec() override, BlockEntity onDestroyed returning BlockState, CustomPacketPayload for every packet.
11. **Balm menus** — for every menu, add `Data` record + `STREAM_CODEC` (composite, not of); update `ModMenus` factory generic and `getStreamCodec()`; update BlockEntity menu provider to return typed `Data`; delete `writeToBufOpening`/`readBufOpening` overrides.
12. **Main mod class** — NeoForge `@Mod` constructor takes `IEventBus`; initialize Balm via `NeoForgeLoadContext`; inline client init into the same constructor under an `FMLEnvironment.dist == Dist.CLIENT` check.
13. **Mod event subscribers** — `@Mod.EventBusSubscriber` → `@EventBusSubscriber` (top-level).
14. **`./gradlew clean build`** — confirm compilation succeeds on common, fabric, neoforge.
15. **`./gradlew :fabric:runServer`** and **`./gradlew :neoforge:runServer`** — confirm both start without mixin errors.
