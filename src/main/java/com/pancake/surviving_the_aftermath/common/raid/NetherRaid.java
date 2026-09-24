package com.pancake.surviving_the_aftermath.common.raid;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pancake.surviving_the_aftermath.api.AftermathState;
import com.pancake.surviving_the_aftermath.api.IAftermath;
import com.pancake.surviving_the_aftermath.api.ITracker;
import com.pancake.surviving_the_aftermath.common.accessor.PortalShapeAccessor;
import com.pancake.surviving_the_aftermath.common.event.tracker.RaidPlayerBattleTracker;
import com.pancake.surviving_the_aftermath.common.init.ModAftermathModule;
import com.pancake.surviving_the_aftermath.common.init.ModStructures;
import com.pancake.surviving_the_aftermath.common.raid.module.BaseRaidModule;
import com.pancake.surviving_the_aftermath.common.structure.NetherRaidStructure;
import com.pancake.surviving_the_aftermath.common.util.CodecUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.portal.PortalShape;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;

public class NetherRaid extends BaseRaid {
    public static final ResourceLocation BARS_RESOURCE = new ResourceLocation("surviving_the_aftermath:textures/gui/nether_raid_bars.png");
    public static final String IDENTIFIER = "nether_raid";
    public static final Codec<NetherRaid> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AftermathState.CODEC.fieldOf("state").forGetter(BaseRaid::getState),
            BaseRaidModule.CODEC.fieldOf("module").forGetter(BaseRaid::getModule),
            CodecUtils.setOf(CodecUtils.UUID_CODEC).fieldOf("players").forGetter(BaseRaid::getPlayers),
            Codec.FLOAT.fieldOf("progressPercent").forGetter(BaseRaid::getProgressPercent),
            BlockPos.CODEC.fieldOf("startPos").forGetter(BaseRaid::getStartPos),
            Codec.INT.fieldOf("readyTime").forGetter(BaseRaid::getReadyTime),
            Codec.INT.fieldOf("rewardTime").forGetter(BaseRaid::getRewardTime),
            CodecUtils.setOf(BlockPos.CODEC).fieldOf("spawnPos").forGetter(BaseRaid::getSpawnPos),
            CodecUtils.setOf(CodecUtils.UUID_CODEC).fieldOf("enemies").forGetter(BaseRaid::getEnemies),
            Codec.INT.fieldOf("currentWave").forGetter(BaseRaid::getCurrentWave),
            Codec.INT.fieldOf("totalEnemy").forGetter(BaseRaid::getTotalEnemy),
            Codec.list(ITracker.CODEC.get()).fieldOf("trackers").forGetter(NetherRaid::getTrackers),
            CodecUtils.setOf(BlockPos.CODEC).optionalFieldOf("portal_blocks", Set.of()).forGetter(NetherRaid::getPortalBlocks)
    ).apply(instance, NetherRaid::new));

    public NetherRaid(AftermathState state, BaseRaidModule module, Set<UUID> players, Float progressPercent, BlockPos startPos, Integer readyTime, Integer rewardTime,
                      Set<BlockPos> spawnPos, Set<UUID> enemies, Integer currentWave, Integer totalEnemy,List<ITracker> trackers) {
        super(state, module, players, progressPercent, startPos, readyTime, rewardTime, spawnPos, enemies, currentWave, totalEnemy,trackers);
        // Old saves used the portal plane as the mob spawn positions.
        this.portalBlocks.addAll(spawnPos);
    }
    public NetherRaid(AftermathState state, BaseRaidModule module, Set<UUID> players, Float progressPercent, BlockPos startPos, Integer readyTime, Integer rewardTime,
                      Set<BlockPos> spawnPos, Set<UUID> enemies, Integer currentWave, Integer totalEnemy, List<ITracker> trackers, Set<BlockPos> portalBlocks) {
        this(state, module, players, progressPercent, startPos, readyTime, rewardTime, spawnPos, enemies, currentWave, totalEnemy, trackers);
        if (!portalBlocks.isEmpty()) {
            this.portalBlocks.clear();
            this.portalBlocks.addAll(portalBlocks);
        }
    }
    private PortalShape portalShape;
    private final Set<BlockPos> portalBlocks = new HashSet<>();

    public Set<BlockPos> getPortalBlocks() { return Collections.unmodifiableSet(portalBlocks); }


    public NetherRaid(ServerLevel level, BlockPos startPos) {
        super(level, startPos);
    }

    public NetherRaid() {
    }

    @Override
    protected void init() {
        setDir(this.level,this.startPos);
        if (portalShape == null) { state = AftermathState.END; return; }
        PortalShapeAccessor shape = (PortalShapeAccessor) portalShape;
        BlockPos bottomLeft = shape.survivingTheAftermath$getBottomLeft();
        BlockPos topRight = bottomLeft.above(shape.survivingTheAftermath$getHeight() - 1)
                .relative(shape.survivingTheAftermath$getRightDir(), shape.survivingTheAftermath$getWidth() - 1);
        BlockPos.betweenClosed(bottomLeft, topRight).forEach(pos -> portalBlocks.add(pos.immutable()));
        super.init();
    }

    @Override
    public void setMobSpawnPos(ServerLevel serverLevel, String metadata, BlockPos startPos, BlockPos pos) {
        if (metadata.equals("spawnPos")){
            spawnPos.addAll(portalBlocks);
        }
    }

    private void setDir(ServerLevel serverLevel,BlockPos pos){
        portalShape = PortalShape.findPortalShape(serverLevel, pos, PortalShape::isValid, Direction.Axis.X).orElse(null);
    }

    @Override
    public void end() {
        if (isEnd()) return;
        // Close only this encounter's portal; keep its frame and all rewards.
        for (BlockPos pos : portalBlocks) {
            if (level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) level.removeBlock(pos, false);
        }
        super.end();
    }

    @Override
    protected void bindTrackers() {
        super.bindTrackers();
        addTrackers(new RaidPlayerBattleTracker().setUUID(uuid));
    }

    @Override
    public void setMobSpawn(ServerLevel level, Mob mob) {
        if (mob instanceof AbstractPiglin piglin) {
            piglin.setImmuneToZombification(true);
        }
        if (mob instanceof Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
        }
        if (mob instanceof Slime slime) {
            slime.finalizeSpawn(level, level.getCurrentDifficultyAt(slime.blockPosition()), MobSpawnType.EVENT, null, null);
        }
        super.setMobSpawn(level, mob);
    }

    @Override
    protected void onWaveStarted() {
        updateStructure();
        level.playSound(null, startPos, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).get(),
                SoundSource.NEUTRAL, 3.0F, 1.0F);
    }



    protected void updateStructure() {
        StructureStart start = this.level.structureManager().getStructureAt(startPos, Objects.requireNonNull(this.level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ModStructures.NETHER_RAID)));
        if (start != StructureStart.INVALID_START) {
            Optional<StructureTemplate> template = this.level.getStructureManager().get(NetherRaidStructure.STRUCTURE_TRANSFORMED);
            template.ifPresent(t -> {
                if (start.getPieces().get(0) instanceof TemplateStructurePiece piece) {
                    BlockPos pos = piece.templatePosition();
                    StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(piece.getRotation()).setMirror(Mirror.NONE)
                            .addProcessor(new BlockIgnoreProcessor(ImmutableList.of(Blocks.AIR, Blocks.STRUCTURE_BLOCK, Blocks.NETHER_PORTAL, Blocks.OBSIDIAN)))
                            .addProcessor(new StructureProcessor() {

                                @Override
                                @NotNull
                                protected StructureProcessorType<?> getType() {
                                    return null;
                                }

                                @Override
                                public StructureTemplate.StructureBlockInfo process(@NotNull LevelReader levelReader,
                                                                                    @NotNull BlockPos p_74141_,
                                                                                    @NotNull BlockPos p_74142_,
                                                                                    @NotNull StructureTemplate.StructureBlockInfo blockInfo,
                                                                                    @NotNull StructureTemplate.StructureBlockInfo relativeBlockInfo,
                                                                                    @NotNull StructurePlaceSettings p_74145_,
                                                                                    @Nullable StructureTemplate template) {
                                    if (levelReader.getBlockState(relativeBlockInfo.pos()).is(Blocks.OBSIDIAN)
                                            || levelReader.getBlockState(relativeBlockInfo.pos()).is(Blocks.NETHER_PORTAL)
                                            || level.random.nextFloat() < 0.9) {
                                        return new StructureTemplate.StructureBlockInfo(relativeBlockInfo.pos(),
                                                levelReader.getBlockState(relativeBlockInfo.pos()), relativeBlockInfo.nbt());
                                    } else {
                                        return relativeBlockInfo;
                                    }
                                }
                            });
                    t.placeInWorld(this.level, pos, pos, settings, this.level.random, 2);
                }
            });
        }
    }

    @Override
    public void insertTag(LivingEntity entity) {
        super.insertTag(entity);
        entity.getPersistentData().put(IDENTIFIER, StringTag.valueOf("enemies"));
    }

    @Override
    public ResourceLocation getBarsResource() {
        return BARS_RESOURCE;
    }

    @Override
    public int[] getBarsOffset() {
        return new int[]{192,23,182,4,5,4};
    }

    @Override
    public Codec<? extends IAftermath> codec() {
        return CODEC;
    }

    @Override
    public IAftermath type() {
        return ModAftermathModule.NETHER_RAID.get();
    }
}
