package com.pancake.surviving_the_aftermath.common.raid;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.api.AftermathManager;
import com.pancake.surviving_the_aftermath.api.AftermathState;
import com.pancake.surviving_the_aftermath.api.IAftermath;
import com.pancake.surviving_the_aftermath.api.ITracker;
import com.pancake.surviving_the_aftermath.api.base.BaseAftermath;
import com.pancake.surviving_the_aftermath.api.module.IConditionModule;
import com.pancake.surviving_the_aftermath.api.module.IEntityInfoModule;
import com.pancake.surviving_the_aftermath.common.event.tracker.MobBattleTracker;
import com.pancake.surviving_the_aftermath.common.event.tracker.RaidMobBattleTracker;
import com.pancake.surviving_the_aftermath.common.init.ModAftermathModule;
import com.pancake.surviving_the_aftermath.common.module.condition.StructureConditionModule;
import com.pancake.surviving_the_aftermath.common.raid.api.IRaid;
import com.pancake.surviving_the_aftermath.common.raid.module.BaseRaidModule;
import com.pancake.surviving_the_aftermath.common.util.AftermathEventUtil;
import com.pancake.surviving_the_aftermath.common.util.CodecUtils;
import com.pancake.surviving_the_aftermath.common.util.RandomUtils;
import com.pancake.surviving_the_aftermath.common.util.StructureUtils;
import com.pancake.surviving_the_aftermath.common.util.SafeSpawn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;


public class BaseRaid extends BaseAftermath implements IRaid {
    public static final String IDENTIFIER = "raid";
    public static final Codec<BaseRaid> CODEC = RecordCodecBuilder.create(instance -> instance.group(
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
            Codec.list(ITracker.CODEC.get()).fieldOf("trackers").forGetter(BaseRaid::getTrackers)
    ).apply(instance, BaseRaid::new));

    protected Set<UUID> enemies = Sets.newLinkedHashSet();
    protected Set<BlockPos> spawnPos = Sets.newHashSet();
    protected int currentWave = -1;
    protected int totalEnemy = 0;
    private int readyTime;
    public int rewardTime;
    public BlockPos startPos;
    public BaseRaid(AftermathState state, BaseRaidModule module, Set<UUID> players, Float progressPercent, BlockPos startPos, Integer readyTime, Integer rewardTime,
                    Set<BlockPos> spawnPos, Set<UUID> enemies, Integer currentWave, Integer totalEnemy,List<ITracker> trackers) {
        super(state, module,players, progressPercent,trackers);
        this.readyTime = readyTime;
        this.rewardTime = rewardTime;
        this.startPos = startPos;
        this.spawnPos = new HashSet<>(spawnPos);
        this.enemies = new LinkedHashSet<>(enemies);
        this.currentWave = currentWave;
        this.totalEnemy = totalEnemy;
    }

    public BaseRaid(ServerLevel level,BlockPos startPos) {
        super(level);
        this.startPos = startPos;
    }
    public BaseRaid(BaseRaidModule module,ServerLevel level,BlockPos startPos) {
        super(module,level);
        this.startPos = startPos;
    }


    public BaseRaid() {
    }



    @Override
    protected void init() {
        readyTime = Math.max(0, getModule().getReadyTime());
        rewardTime = Math.max(0, getModule().getRewardTime());
        SetSpawnPos(this::defaultSetSpawnPos);
        if (spawnPos.isEmpty()) spawnPos.add(startPos);
        super.init();
    }

    @Override
    public void tick() {
        AftermathState before = state;
        super.tick();
        if (isEnd()) return;
        if (state == AftermathState.START) {
            if (!AftermathEventUtil.ready(this, players, level)) end();
            return;
        }
        if (state == AftermathState.ONGOING) {
            AftermathEventUtil.ongoing(this, players, level);
            if (isEnd() || players.isEmpty()) return;
            // Missing entities can be unloaded. Death/destruction events remove them.
            enemies.removeIf(id -> {
                Entity entity = level.getEntity(id);
                return entity != null && !entity.isAlive();
            });
            checkNextWave();
            spawnWave();
            EnemyTotalRatio();
            super.updateProgress();
        }
        boolean celebrationStarted = before == AftermathState.VICTORY;
        if (state == AftermathState.VICTORY) {
            celebrationStarted = true;
            if (!AftermathEventUtil.celebrating(this, players, level)) { end(); return; }
        }
        if (state == AftermathState.CELEBRATING) {
            if (!celebrationStarted && !AftermathEventUtil.celebrating(this, players, level)) {
                end();
                return;
            }
            if (rewardTime <= 0) { end(); return; }
            createRewards();
            rewardTime--;
            if (rewardTime <= 0) end();
        }
    }

    private void EnemyTotalRatio(){
        this.progressPercent = totalEnemy == 0 ? 0 : enemies.size() / (float) totalEnemy;
    }

    protected void spawnWave() {
        if (enemies.isEmpty() && state == AftermathState.ONGOING){
            getModule().getWaves().get(currentWave).forEach(this::spawnEntities);
        }
    }

    private void spawnEntities(IEntityInfoModule entityInfoModule) {
        if (players.isEmpty() || isEnd()) return;
        List<Optional<Entity>> arrayList = entityInfoModule.spawnEntity(level, startPos);
        for (Optional<Entity> lazyOptional : arrayList) {
            lazyOptional.ifPresent(entity -> {
                if (entity instanceof Mob mob && !isEnd()) {
                    setMobSpawn(level,mob);
                }
            });
        }
    }

    @Override
    public void createRewards() {
        BlockPos blockPos = spawnPos.isEmpty() ? startPos : RandomUtils.getRandomElement(spawnPos);
        Direction dir = Direction.Plane.HORIZONTAL.stream().filter(d -> level.isEmptyBlock(blockPos.relative(d))
                && !spawnPos.contains(blockPos.relative(d))).findFirst().orElse(Direction.UP);
        Vec3 vec = Vec3.atCenterOf(blockPos);
        getModule().getRewards().getWeightedList().getRandom(level.getRandom()).ifPresent(reward -> {
            ItemEntity itemEntity = new ItemEntity(level, vec.x, vec.y, vec.z, new ItemStack(reward),
                    dir.getStepX() * 0.2, 0.2, dir.getStepZ() * 0.2f);
            itemEntity.setInvulnerable(true);
            level.addFreshEntity(itemEntity);
        });
    }

    @Override
    protected void bindTrackers() {
        addTrackers(new MobBattleTracker().setUUID(uuid));
        addTrackers(new RaidMobBattleTracker().setUUID(uuid));
    }

    @Override
    public BlockPos getStartPos() {
        return startPos;
    }
    public void setMobSpawn(ServerLevel level, Mob mob) {
        Player target = randomPlayersUnderAttack();
        if (target == null) return;
        if (!SafeSpawn.placeMob(level, mob, spawnPos, startPos, getRadius())) {
            SurvivingTheAftermath.LOGGER.warn("No safe spawn space for {} in aftermath {}", mob.getType(), uuid);
            lose();
            return;
        }
        mob.setPersistenceRequired();
        mob.getBrain().setMemory(MemoryModuleType.ANGRY_AT, target.getUUID());
        mob.setTarget(target);
        // Test escape direction at the final spawn position, using the entire body.
        Direction dir = Direction.Plane.HORIZONTAL.stream()
                .filter(d -> level.noCollision(mob, mob.getBoundingBox().move(d.getStepX() * 0.5, 0, d.getStepZ() * 0.5)))
                .findFirst().orElse(null);
        if (dir != null) mob.setDeltaMovement(dir.getStepX() * 0.5, 0, dir.getStepZ() * 0.5);
        if (join(mob)) {
            insertTag(mob);
            if (!level.tryAddFreshEntityWithPassengers(mob)) {
                enemies.remove(mob.getUUID());
                totalEnemy--;
                lose();
            }
        }
    }
    public Player randomPlayersUnderAttack(){
        List<Player> targets = players.stream().map(level::getPlayerByUUID)
                .filter(Objects::nonNull).filter(player -> player.isAlive() && !player.isSpectator()).toList();
        return targets.isEmpty() ? null : targets.get(level.getRandom().nextInt(targets.size()));
    }

    public boolean join(Entity entity) {
        if (state == AftermathState.ONGOING &&
                Math.sqrt(entity.blockPosition().distSqr(startPos)) < getRadius() &&
                enemies.add(entity.getUUID())) {
            totalEnemy++;
            return true;
        }
        return false;
    }


    protected void checkNextWave(){
        if (enemies.isEmpty()){
            if(this.currentWave >= getModule().getWaves().size() - 1) {
                AftermathEventUtil.victory(this,players,level);
            } else {
                currentWave++;
                totalEnemy = 0;
                onWaveStarted();
            }
        }
    }

    /** Called once when advancing to a new wave, before its mobs are placed. */
    protected void onWaveStarted() {}

    @Override
    public boolean isCreate(Level level, BlockPos pos, @Nullable Player player) {
        boolean create = super.isCreate(level, pos, player);
        if (!create || !(module instanceof BaseRaidModule raidModule) || raidModule.getWaves() == null
                || raidModule.getWaves().isEmpty() || raidModule.getRewards() == null) return false;
        boolean noneMatch = AftermathManager.getInstance().getAftermathMap().values().stream()
                .filter(aftermath -> !aftermath.isEnd() && aftermath instanceof IRaid)
                .map(aftermath -> (IRaid) aftermath)
                .noneMatch(raid -> raid.getStartPos().distSqr(startPos) < Math.pow(raid.getRadius(), 2));
        return create && noneMatch;
    }
    public void SetSpawnPos(SpawnPosHandler handler) {
        handler.handleSpawnPos(this.level,this.startPos);
    }

    private void defaultSetSpawnPos(Level level,BlockPos startPos) {
        if (level instanceof  ServerLevel serverLevel){
            List<IConditionModule> conditions = getModule().getConditions();
            if (conditions == null){
                this.startPos = startPos;
                this.spawnPos.add(startPos);
                return;
            }
            Optional<StructureConditionModule> module = conditions.stream()
                    .filter(condition -> condition instanceof StructureConditionModule)
                    .map(condition -> (StructureConditionModule) condition)
                    .findFirst();
            if (module.isPresent()){
                StructureUtils.handleDataMarker(serverLevel, startPos, module.get().getIdentifier(), (serverLevel1, metadata, blockInfo, startPos1) -> {
                    this.startPos = startPos1;
                    BlockPos metaPos = blockInfo.pos();
                    setMobSpawnPos(serverLevel1,metadata,startPos1,metaPos);
                });
            }else {
                this.startPos = startPos;
                this.spawnPos.add(startPos);
            }
        }
    }

    public void setMobSpawnPos(ServerLevel serverLevel, String metadata, BlockPos startPos, BlockPos metaPos) {
        spawnPos.add(metaPos);
    }

    @Override
    public void updateProgress() {
        super.updateProgress();

        if (state == AftermathState.READY && !players.isEmpty()){
            ready();
        }
    }


    public void ready(){
        if (readyTime <= 0){
            AftermathEventUtil.ongoing(this,players,level);
            return;
        }
        this.progressPercent = 1 - (float) readyTime / getModule().getReadyTime();
        readyTime--;
    }

    @Override
    public Predicate<? super ServerPlayer> validPlayer() {
        Predicate<ServerPlayer> predicate = (Predicate<ServerPlayer>) super.validPlayer();
        return predicate.and(player -> Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(this.startPos))) < getRadius());
    }

    @Override
    public BaseRaidModule getModule() {
        return (BaseRaidModule) super.getModule();
    }

    @Override
    public Codec<? extends IAftermath> codec() {
        return CODEC;
    }

    @Override
    public IAftermath type() {
        return ModAftermathModule.BASE_RAID.get();
    }


    @Override
    public Identifier getRegistryName() {
        return SurvivingTheAftermath.asResource(IDENTIFIER);
    }

    @Override
    public void updateInsertTag() {
        super.updateInsertTag();
        this.enemies.forEach(uuid -> {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof LivingEntity livingEntity){
                insertTag(livingEntity);
            }
        });
    }

    @Override
    public void insertTag(LivingEntity entity){
        if (entity instanceof Mob mob) com.pancake.surviving_the_aftermath.common.util.RaidMobLoot.mark(mob);
        entity.getPersistentData().put(IDENTIFIER, StringTag.valueOf("enemies"));
        entity.getPersistentData().store("raid_uuid", net.minecraft.core.UUIDUtil.CODEC, this.uuid);

        if (entity instanceof Player player){
            player.getPersistentData().put(IDENTIFIER, StringTag.valueOf("players"));
            player.getPersistentData().store("raid_uuid", net.minecraft.core.UUIDUtil.CODEC, this.uuid);
        }
    }

    @Override
    public Identifier getBarsResource() {
        return null;
    }

    @Override
    public int[] getBarsOffset() {
        return null;
    }

    @Override
    public int getRadius() {
        return 50;
    }
    public int getReadyTime() {
        return readyTime;
    }

    public int getRewardTime() {
        return rewardTime;
    }

    public Set<BlockPos> getSpawnPos() {
        return spawnPos;
    }

    public void addSpawnPos(BlockPos pos) {
        spawnPos.add(pos);
    }

    @Override
    public Set<UUID> getEnemies() {
        return enemies;
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public int getTotalEnemy() {
        return totalEnemy;
    }

    @FunctionalInterface
    public interface SpawnPosHandler {
        void handleSpawnPos(Level level, BlockPos pos);
    }
}
