package com.pancake.surviving_the_aftermath.common.init;
import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.capability.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.attachment.*;
import net.neoforged.neoforge.registries.*;
import java.util.function.Supplier;

public final class ModCapability {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SurvivingTheAftermath.MOD_ID);
    public static final Supplier<AttachmentType<AftermathCap>> AFTERMATH_CAP = ATTACHMENTS.register("aftermath_cap", () ->
        AttachmentType.builder(holder -> new AftermathCap((ServerLevel) holder)).serialize(new IAttachmentSerializer<AftermathCap>() {
            public AftermathCap read(IAttachmentHolder holder, net.minecraft.world.level.storage.ValueInput input) {
                var state = new AftermathCap((ServerLevel) holder); state.deserializeNBT(input.read("state", CompoundTag.CODEC).orElseGet(CompoundTag::new)); return state;
            }
            public boolean write(AftermathCap state, net.minecraft.world.level.storage.ValueOutput output) { output.store("state", CompoundTag.CODEC, state.serializeNBT()); return true; }
        }).build());
    public static final Supplier<AttachmentType<AftermathStageCap>> STAGE_CAP = ATTACHMENTS.register("stage_cap", () ->
        AttachmentType.builder(AftermathStageCap::new).serialize(new IAttachmentSerializer<AftermathStageCap>() {
            public AftermathStageCap read(IAttachmentHolder holder, net.minecraft.world.level.storage.ValueInput input) {
                var state = new AftermathStageCap(); state.deserializeNBT(input.read("state", CompoundTag.CODEC).orElseGet(CompoundTag::new).getListOrEmpty("stages")); return state;
            }
            public boolean write(AftermathStageCap state, net.minecraft.world.level.storage.ValueOutput output) { var tag = new CompoundTag(); tag.put("stages", state.serializeNBT()); output.store("state", CompoundTag.CODEC, tag); return true; }
        }).copyOnDeath().build());
}
