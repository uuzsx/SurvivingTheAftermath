package com.pancake.surviving_the_aftermath.common.data.pack;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.api.AftermathManager;
import com.pancake.surviving_the_aftermath.api.module.IAftermathModule;
import com.pancake.surviving_the_aftermath.common.util.AftermathEventUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;


import java.util.Map;

public class AftermathModuleLoader extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    public static final Multimap<Identifier, IAftermathModule> AFTERMATH_MODULE_MAP = ArrayListMultimap.create();

    public AftermathModuleLoader() {
        super(com.mojang.serialization.Codec.PASSTHROUGH.xmap(d -> d.convert(JsonOps.INSTANCE).getValue(), j -> new com.mojang.serialization.Dynamic<>(JsonOps.INSTANCE, j)), net.minecraft.resources.FileToIdConverter.json("aftermath"));
    }
    @Override
    protected void apply(Map<Identifier, JsonElement> jsonElementMap, @NotNull ResourceManager manager, @NotNull ProfilerFiller filler) {
        AFTERMATH_MODULE_MAP.clear();
        jsonElementMap.forEach((resourceLocation, jsonElement) -> {
            JsonObject asJsonObject = jsonElement.getAsJsonObject();

            IAftermathModule.CODEC.get()
                    .parse(JsonOps.INSTANCE, asJsonObject)
                    .resultOrPartial(SurvivingTheAftermath.LOGGER::error)
                    .ifPresent(aftermathModule -> {
                        String string = asJsonObject.get("aftermath_module").getAsString();
                        AFTERMATH_MODULE_MAP.put(Identifier.tryParse(string), aftermathModule);
                    });
        });


        AFTERMATH_MODULE_MAP.asMap().forEach(AftermathEventUtil::modify);

        AftermathManager.getInstance().fillAftermathModuleMap(AFTERMATH_MODULE_MAP);

    }
}