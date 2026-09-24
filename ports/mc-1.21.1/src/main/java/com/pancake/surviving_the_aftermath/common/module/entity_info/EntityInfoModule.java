package com.pancake.surviving_the_aftermath.common.module.entity_info;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pancake.surviving_the_aftermath.api.module.IAmountModule;
import com.pancake.surviving_the_aftermath.api.module.IEntityInfoModule;
import com.pancake.surviving_the_aftermath.common.init.ModAftermathModule;
import com.pancake.surviving_the_aftermath.common.util.RegistryUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import java.util.Optional;

import java.util.List;

public class EntityInfoModule implements IEntityInfoModule {
    public static final String IDENTIFIER = "entity_info";
    public static final Codec<EntityInfoModule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity_type").forGetter(EntityInfoModule::getEntityType),
            IAmountModule.CODEC.get().fieldOf("amount_module").forGetter(EntityInfoModule::getAmountModule)

    ).apply(instance, EntityInfoModule::new));
    protected EntityType<?> entityType;
    protected IAmountModule amountModule;

    public EntityInfoModule(EntityType<?> entityType, IAmountModule amountModule) {
        this.entityType = entityType;
        this.amountModule = amountModule;
    }

    public EntityInfoModule() {
    }

    @Override
    public List<Optional<Entity>> spawnEntity(Level level) {
        return spawnEntity(level, net.minecraft.core.BlockPos.ZERO);
    }

    @Override
    public List<Optional<Entity>> spawnEntity(Level level, net.minecraft.core.BlockPos origin) {
        List<Optional<Entity>> arrayList = Lists.newArrayList();
        int amount = amountModule.getSpawnAmount();
        for (int i = 0; i < amount; i++) {
            Entity entity = entityType.create(level);
            if (entity instanceof net.minecraft.world.entity.Mob mob && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                mob.moveTo(net.minecraft.world.phys.Vec3.atCenterOf(origin));
                net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob, serverLevel,
                        serverLevel.getCurrentDifficultyAt(origin), net.minecraft.world.entity.MobSpawnType.EVENT, null);
                if (mob.isSpawnCancelled()) {
                    mob.discard();
                    arrayList.add(Optional.empty());
                    continue;
                }
                // Dungeon piglins are combatants. Vanilla can otherwise create unarmed babies.
                if (mob instanceof net.minecraft.world.entity.monster.piglin.Piglin piglin) {
                    piglin.setBaby(false);
                    if (piglin.getMainHandItem().isEmpty()) {
                        piglin.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                                new net.minecraft.world.item.ItemStack(serverLevel.getRandom().nextBoolean()
                                        ? net.minecraft.world.item.Items.GOLDEN_SWORD : net.minecraft.world.item.Items.CROSSBOW));
                    }
                }
            }
            arrayList.add(entity == null ? Optional.empty() : Optional.ofNullable(entity));
        }
        return arrayList;
    }
    public EntityType<?> getEntityType() {
        return entityType;
    }

    public IAmountModule getAmountModule() {
        return amountModule;
    }

    @Override
    public Codec<? extends IEntityInfoModule> codec() {
        return CODEC;
    }

    @Override
    public IEntityInfoModule type() {
        return ModAftermathModule.ENTITY_INFO.get();
    }

    public static class Builder {
        protected final EntityType<?> entityType;
        protected IAmountModule amountModule;

        public Builder(String entityType) {
            this.entityType = RegistryUtil.getEntityTypeFromRegistryName(entityType);
        }

        public Builder(EntityType<?> entityType) {
            this.entityType = entityType;
        }

        public Builder amountModule(IAmountModule amountModule) {
            this.amountModule = amountModule;
            return this;
        }
        public EntityInfoModule build() {
            return new EntityInfoModule(entityType, amountModule);
        }
    }
}
