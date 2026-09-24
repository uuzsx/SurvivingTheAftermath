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
        AttachmentType.builder(holder -> new AftermathCap((ServerLevel) holder)).serialize(new IAttachmentSerializer<CompoundTag, AftermathCap>() {
            public AftermathCap read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                var state = new AftermathCap((ServerLevel) holder); state.deserializeNBT(tag); return state;
            }
            public CompoundTag write(AftermathCap state, HolderLookup.Provider provider) { return state.serializeNBT(); }
        }).build());
    public static final Supplier<AttachmentType<AftermathStageCap>> STAGE_CAP = ATTACHMENTS.register("stage_cap", () ->
        AttachmentType.builder(AftermathStageCap::new).serialize(new IAttachmentSerializer<ListTag, AftermathStageCap>() {
            public AftermathStageCap read(IAttachmentHolder holder, ListTag tag, HolderLookup.Provider provider) {
                var state = new AftermathStageCap(); state.deserializeNBT(tag); return state;
            }
            public ListTag write(AftermathStageCap state, HolderLookup.Provider provider) { return state.serializeNBT(); }
        }).copyOnDeath().build());
}
