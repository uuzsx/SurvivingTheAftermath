package com.pancake.surviving_the_aftermath.common.mixin;

import com.pancake.surviving_the_aftermath.common.structure.CityStructure;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reserve room for the whole crown before natural trees grow; saplings are unaffected. */
@Mixin(TreeFeature.class)
public abstract class CityTreeFeatureMixin {
    @Inject(method="place",at=@At("HEAD"),cancellable=true)
    private void aftermath$reserveCity(WorldGenLevel level,ChunkGenerator generator,RandomSource random,BlockPos pos,CallbackInfoReturnable<Boolean> result) {
        if (!(level instanceof WorldGenRegion region)) return;
        var manager = region.getLevel().structureManager().forWorldGenRegion(region);
        for(var start:manager.startsForStructure(pos.getX()>>4,pos.getZ()>>4,s->s instanceof CityStructure)) {
            for(var piece:start.getPieces()) {
                if(!(piece instanceof TemplateStructurePiece template))continue;
                var box=template.template().getBoundingBox(template.placeSettings(),template.templatePosition());
                if(box.intersects(pos.getX()-8,pos.getZ()-8,pos.getX()+8,pos.getZ()+8)) {
                    result.setReturnValue(false);return;
                }
            }
        }
    }
}
