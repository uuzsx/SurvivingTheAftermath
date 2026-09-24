package com.pancake.surviving_the_aftermath.common.data.datagen;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper helper) {
        super(output, SurvivingTheAftermath.MOD_ID, helper);
    }

    @Override
    protected void registerModels() {
        singleTexture("diamond_flint_and_steel", mcLoc("item/handheld"), "layer0", modLoc("item/diamond_flint_and_steel"));
    }
}
