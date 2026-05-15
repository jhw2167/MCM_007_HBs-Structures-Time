package com.holybuckets.structures.command;

//Project imports

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.event.CommandRegistry;
import com.holybuckets.structures.LoggerProject;
import com.holybuckets.structures.core.ChunkRegenerator;
import com.holybuckets.structures.core.StructureConceptManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;

import java.util.List;

public class CommandList {

    public static final String CLASS_ID = "033";
    private static final String PREFIX = "hbStructures";

    public static void register() {
        //CommandRegistry.register(LocateClusters::noArgs);
        //CommandRegistry.register(LocateClusters::limitCount);
        //CommandRegistry.register(LocateClusters::limitCountSpecifyBlockType);
        CommandRegistry.register(SetGlobalStage::withStageArg);
        CommandRegistry.register(RegenerateChunk::noArgs);

        CommandRegistry.register(SetGlobalStage::withStageArg);
        CommandRegistry.register(RegenerateChunk::noArgs);
        CommandRegistry.register(GetDetails::noArgs);
        CommandRegistry.register(GetDetails::withChunkPos);
        CommandRegistry.register(Config::withConceptId);
        CommandRegistry.register(Config::withConceptIdAndShowStages);
        CommandRegistry.register(StageConfig::withConceptId);
        CommandRegistry.register(StageConfig::withConceptIdAndStage);
        CommandRegistry.register(Locate::withConceptId);
        CommandRegistry.register(StopUpgrades::noArgs);
        CommandRegistry.register(StopUpgrades::withChunkPos);
        CommandRegistry.register(ForceUpgrade::withChunkPos);
        CommandRegistry.register(ForceUpgrade::withChunkPosAndConfirm);
        CommandRegistry.register(ResumeUpgrades::noArgs);
        CommandRegistry.register(ResumeUpgrades::withChunkPos);
    }

    //1. Locate Clusters
    private static class LocateClusters
    {
        // Register the base command with no arguments
        private static LiteralArgumentBuilder<CommandSourceStack> noArgs() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("locateClusters")
                    .executes(context -> execute(context.getSource(), -1, null)) // Default case (no args)
                );

        }

        // Register command with count argument
        private static LiteralArgumentBuilder<CommandSourceStack> limitCount() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("locateClusters")
                    .then(Commands.argument("count", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int count = IntegerArgumentType.getInteger(context, "count");
                            return execute(context.getSource(), count, null);
                        })
                    )
            );
        }

        // Register command with both count and blockType OR just blockType
        private static LiteralArgumentBuilder<CommandSourceStack> limitCountSpecifyBlockType() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("locateClusters")
                    .then(Commands.argument("count", IntegerArgumentType.integer(1))
                        .then(Commands.argument("blockType", StringArgumentType.string())
                            .executes(context -> {
                                int count = IntegerArgumentType.getInteger(context, "count");
                                String blockType = StringArgumentType.getString(context, "blockType");
                                return execute(context.getSource(), count, blockType);
                            })
                        )
                    )
                    .then(Commands.argument("blockType", StringArgumentType.string())
                        .executes(context -> {
                            String blockType = StringArgumentType.getString(context, "blockType");
                            return execute(context.getSource(), -1, blockType);
                        })
                    )
            );
        }


        private static int execute(CommandSourceStack source, int count, String blockType)
        {
            LoggerProject.logDebug("010001", "Locate Clusters Command");
            return 0;
        }


    }
    //END COMMAND


    //2. Set Global Stage
    private static class SetGlobalStage
    {
        private static final int MIN_STAGE = 0;
        private static final int MAX_STAGE = 16;

        // Register command with stage argument
        private static LiteralArgumentBuilder<CommandSourceStack> withStageArg() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("setGlobalStage")
                    .then(Commands.argument("stage", IntegerArgumentType.integer())
                        .executes(context -> {
                            int stage = IntegerArgumentType.getInteger(context, "stage");
                            return execute(context.getSource(), stage);
                        })
                    )
                );
        }

        private static int execute(CommandSourceStack source, int stage)
        {
            LoggerProject.logDebug("010002", "Set Global Stage Command");

            if (stage < MIN_STAGE || stage > MAX_STAGE) {
                source.sendFailure(Component.literal(
                    "Invalid stage: " + stage + ". Stage must be between " + MIN_STAGE + " and " + MAX_STAGE + " (inclusive)."
                ));
                return 0;
            }

            StructureConceptManager.setGlobalStage(GeneralConfig.OVERWORLD, stage);
            source.sendSuccess(() -> Component.literal("Global stage set to " + stage + "."), true);
            return 1;
        }

    }
    //END COMMAND


    //3. Regenerate Chunk
    private static class RegenerateChunk
    {
        // Regenerates terrain for the chunk the player is standing in
        private static LiteralArgumentBuilder<CommandSourceStack> noArgs() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("regenerateChunk")
                    .executes(context -> execute(context.getSource()))
                );
        }

        private static int execute(CommandSourceStack source)
        {
            ServerPlayer player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.literal("This command must be run by a player."));
                return 0;
            }

            ServerLevel level = player.serverLevel();
            BlockPos playerPos = player.blockPosition();
            ChunkPos chunkPos = new ChunkPos(playerPos);

            source.sendSuccess(() -> Component.literal("Regenerating chunk at " + chunkPos + "..."), true);

            try {
                ProtoChunk chunk = ChunkRegenerator.createProtoChunk(level, chunkPos);
                boolean success = ChunkRegenerator.regenerateChunk(chunk, level, chunkPos);
                if (success) {
                    source.sendSuccess(() -> Component.literal("Chunk " + chunkPos + " regenerated."), true);
                    return 1;
                } else {
                    source.sendFailure(Component.literal("Chunk regeneration failed at " + chunkPos + "."));
                    return 0;
                }
            } catch (Exception e) {
                source.sendFailure(Component.literal("Error: " + e.getMessage()));
                LoggerProject.logError(CLASS_ID + "003", "Regenerate chunk command failed: " + e.getMessage());
                return 0;
            }
        }
    }
    //END COMMAND

// ============================================================
// ADD to register():
// ============================================================

    //4. Get Details
    private static class GetDetails
    {
        // No args — find nearest structure to player
        private static LiteralArgumentBuilder<CommandSourceStack> noArgs() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("getDetails")
                    .executes(context -> execute(context.getSource(), null))
                );
        }

        // With chunkPos string like "12,34"
        private static LiteralArgumentBuilder<CommandSourceStack> withChunkPos() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("getDetails")
                    .then(Commands.argument("chunkPos", StringArgumentType.string())
                        .executes(context -> {
                            String chunkPos = StringArgumentType.getString(context, "chunkPos");
                            return execute(context.getSource(), chunkPos);
                        })
                    )
                );
        }

        private static int execute(CommandSourceStack source, String chunkPos) {
            // chunkPos null => search for nearest structure
            source.sendSuccess(() -> Component.literal("[getDetails] Not yet implemented. chunkPos=" + chunkPos), false);
            return 1;
        }
    }
//END COMMAND


    //5. Config
    private static class Config
    {
        private static LiteralArgumentBuilder<CommandSourceStack> withConceptId() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("config")
                    .then(Commands.argument("structureConceptId", StringArgumentType.string())
                        .executes(context -> {
                            String conceptId = StringArgumentType.getString(context, "structureConceptId");
                            return execute(context.getSource(), conceptId, false);
                        })
                    )
                );
        }

        private static LiteralArgumentBuilder<CommandSourceStack> withConceptIdAndShowStages() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("config")
                    .then(Commands.argument("structureConceptId", StringArgumentType.string())
                        .then(Commands.argument("showAllStages", StringArgumentType.string())
                            .executes(context -> {
                                String conceptId = StringArgumentType.getString(context, "structureConceptId");
                                String showAll = StringArgumentType.getString(context, "showAllStages");
                                boolean doShow = showAll.equalsIgnoreCase("true");
                                return execute(context.getSource(), conceptId, doShow);
                            })
                        )
                    )
                );
        }

        private static int execute(CommandSourceStack source, String conceptId, boolean showAllStages) {
            source.sendSuccess(() -> Component.literal(
                "[config] Not yet implemented. conceptId=" + conceptId + " showAllStages=" + showAllStages), false);
            return 1;
        }
    }
//END COMMAND


    //6. Stage Config
    private static class StageConfig
    {
        // Concept id only — returns total stage count
        private static LiteralArgumentBuilder<CommandSourceStack> withConceptId() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("stageConfig")
                    .then(Commands.argument("structureConceptId", StringArgumentType.string())
                        .executes(context -> {
                            String conceptId = StringArgumentType.getString(context, "structureConceptId");
                            return execute(context.getSource(), conceptId, -1);
                        })
                    )
                );
        }

        // Concept id + stage number
        private static LiteralArgumentBuilder<CommandSourceStack> withConceptIdAndStage() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("stageConfig")
                    .then(Commands.argument("structureConceptId", StringArgumentType.string())
                        .then(Commands.argument("stageNo", IntegerArgumentType.integer(1))
                            .executes(context -> {
                                String conceptId = StringArgumentType.getString(context, "structureConceptId");
                                int stageNo = IntegerArgumentType.getInteger(context, "stageNo");
                                return execute(context.getSource(), conceptId, stageNo);
                            })
                        )
                    )
                );
        }

        private static int execute(CommandSourceStack source, String conceptId, int stageNo) {
            // stageNo == -1 => return total stage count
            source.sendSuccess(() -> Component.literal(
                "[stageConfig] Not yet implemented. conceptId=" + conceptId + " stageNo=" + stageNo), false);
            return 1;
        }
    }
//END COMMAND


    //7. Locate
    private static class Locate
    {
        private static LiteralArgumentBuilder<CommandSourceStack> withConceptId() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("locate")
                    .then(Commands.argument("structureConceptId", StringArgumentType.string())
                        .executes(context -> {
                            String conceptId = StringArgumentType.getString(context, "structureConceptId");
                            return execute(context.getSource(), conceptId);
                        })
                    )
                );
        }

        private static int execute(CommandSourceStack source, String conceptId) {
            source.sendSuccess(() -> Component.literal(
                "[locate] Not yet implemented. conceptId=" + conceptId), false);
            return 1;
        }
    }
//END COMMAND


    //8. Stop Upgrades
    private static class StopUpgrades
    {
        // No args — applies to nearest structure
        private static LiteralArgumentBuilder<CommandSourceStack> noArgs() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("stopUpgrades")
                    .executes(context -> execute(context.getSource(), null))
                );
        }

        private static LiteralArgumentBuilder<CommandSourceStack> withChunkPos() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("stopUpgrades")
                    .then(Commands.argument("chunkPos", StringArgumentType.string())
                        .executes(context -> {
                            String chunkPos = StringArgumentType.getString(context, "chunkPos");
                            return execute(context.getSource(), chunkPos);
                        })
                    )
                );
        }

        private static int execute(CommandSourceStack source, String chunkPos) {
            // chunkPos null => nearest structure
            source.sendSuccess(() -> Component.literal(
                "[stopUpgrades] Not yet implemented. chunkPos=" + chunkPos), false);
            return 1;
        }
    }
//END COMMAND


    //9. Force Upgrade
    private static class ForceUpgrade
    {
        // chunkPos only — warns player but does not upgrade
        private static LiteralArgumentBuilder<CommandSourceStack> withChunkPos() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("forceUpgrade")
                    .then(Commands.argument("chunkPos", StringArgumentType.string())
                        .executes(context -> {
                            String chunkPos = StringArgumentType.getString(context, "chunkPos");
                            return execute(context.getSource(), chunkPos, false);
                        })
                    )
                );
        }

        // chunkPos + boolean confirm — actually forces the upgrade
        private static LiteralArgumentBuilder<CommandSourceStack> withChunkPosAndConfirm() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("forceUpgrade")
                    .then(Commands.argument("chunkPos", StringArgumentType.string())
                        .then(Commands.argument("doUpgrade", StringArgumentType.string())
                            .executes(context -> {
                                String chunkPos = StringArgumentType.getString(context, "chunkPos");
                                String confirm = StringArgumentType.getString(context, "doUpgrade");
                                boolean doUpgrade = confirm.equalsIgnoreCase("true");
                                return execute(context.getSource(), chunkPos, doUpgrade);
                            })
                        )
                    )
                );
        }

        private static int execute(CommandSourceStack source, String chunkPos, boolean doUpgrade) {
            if (!doUpgrade) {
                source.sendSuccess(() -> Component.literal(
                    "[forceUpgrade] Structure at " + chunkPos + " selected. WARNING: area will be completely cleared. Run with 'true' to confirm."), false);
            } else {
                source.sendSuccess(() -> Component.literal(
                    "[forceUpgrade] Not yet implemented. chunkPos=" + chunkPos + " doUpgrade=true"), false);
            }
            return 1;
        }
    }
//END COMMAND


    //10. Resume Upgrades
    private static class ResumeUpgrades
    {
        // No args — applies to nearest structure
        private static LiteralArgumentBuilder<CommandSourceStack> noArgs() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("resumeUpgrades")
                    .executes(context -> execute(context.getSource(), null))
                );
        }

        private static LiteralArgumentBuilder<CommandSourceStack> withChunkPos() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("resumeUpgrades")
                    .then(Commands.argument("chunkPos", StringArgumentType.string())
                        .executes(context -> {
                            String chunkPos = StringArgumentType.getString(context, "chunkPos");
                            return execute(context.getSource(), chunkPos);
                        })
                    )
                );
        }

        private static int execute(CommandSourceStack source, String chunkPos) {
            // chunkPos null => nearest structure
            source.sendSuccess(() -> Component.literal(
                "[resumeUpgrades] Not yet implemented. chunkPos=" + chunkPos), false);
            return 1;
        }
    }
//END COMMAND




}
//END CLASS COMMANDLIST
