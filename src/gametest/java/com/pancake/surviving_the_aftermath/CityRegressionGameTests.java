package com.pancake.surviving_the_aftermath;

import com.pancake.surviving_the_aftermath.common.init.*;
import com.pancake.surviving_the_aftermath.common.structure.AbstractStructure;
import com.pancake.surviving_the_aftermath.common.util.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;

public final class CityRegressionGameTests {
    private static void check(boolean value,String message) {
        if(!value)throw new GameTestAssertException(Component.literal(message),0);
    }

    public static void terrainSamplerMatchesVanilla(GameTestHelper h) {
        var level=h.getLevel();var lookup=level.registryAccess();
        var noise=lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var source=MultiNoiseBiomeSource.createFromPreset(lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        var generator=new NoiseBasedChunkGenerator(source,noise);
        int checked=0,wet=0;
        for(long seed:new long[]{0,42,20260924}) {
            var random=RandomState.create(lookup,NoiseGeneratorSettings.OVERWORLD,seed);
            var context=new Structure.GenerationContext(lookup,generator,source,random,level.getStructureManager(),seed,new ChunkPos(0,0),level,b->true);
            var sampler=new SurfaceHeightSampler(context);
            var points=new ArrayList<BlockPos>();
            for(int[] chunk:new int[][]{{0,0},{-1,-1},{8,-16},{50,40},{120,-83},{-159,-83}})
                for(int i=0;i<12;i++)points.add(new BlockPos(chunk[0]*16+(i*7&15),0,chunk[1]*16+(i*11&15)));
            Collections.shuffle(points,new Random(seed));
            for(var pos:points) {
                int x=pos.getX(),z=pos.getZ();
                int top=generator.getFirstOccupiedHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,random);
                int floor=generator.getFirstOccupiedHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,random);
                int expected=top==floor?top:SurfaceStructurePlacement.INVALID_GROUND;
                check(sampler.applyAsInt(x,z)==expected,"Terrain sampler differs from vanilla at "+pos+", seed="+seed);
                check(sampler.applyAsInt(x,z)==expected,"Cached height changed");
                checked++;if(top!=floor)wet++;
            }
        }
        check(wet>0,"Dry/wet height comparison did not include water");
        System.out.println("CITY SAMPLER CHECK: "+checked+" vanilla height pairs, wet="+wet+", 3 seeds, negative coordinates and shuffled cache reads");
        h.succeed();
    }

    public static void undergroundStructuresDoNotDisableCitySupport(GameTestHelper h) {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(55000,100,55000));
        var pos=new BlockPos((origin.getX()>>4)*16,origin.getY(),(origin.getZ()>>4)*16);
        var chunk=new ChunkPos(pos.getX()>>4,pos.getZ()>>4);
        var loaded=level.getChunk(chunk.getMinBlockX()>>4,chunk.getMinBlockZ()>>4);
        var starts=new HashMap<>(loaded.getAllStarts());var refs=new HashMap<>(loaded.getAllReferences());
        try {
            loaded.setAllReferences(new HashMap<>());
            var mine=level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.MINESHAFT).value();
            // Saved underground start with a shallow roof overlapping the same XZ columns.
            var underground=new AbstractStructure.Piece(ModStructurePieceTypes.CONSTRUCTION_1.get(),level.getStructureManager(),
                    SurvivingTheAftermath.asResource("construction1"),pos.below(12),Rotation.NONE);
            loaded.setStartForStructure(mine,new StructureStart(mine,chunk,0,new PiecesContainer(List.of(underground))));
            loaded.addReferenceForStructure(mine,(chunk.getMinBlockX()>>4 & 0xffffffffL)|((long)(chunk.getMinBlockZ()>>4)<<32));
            var protectedBoxes=CityStructureAvoidance.protectedBuildings(level.structureManager(),chunk,pos.getY());
            check(protectedBoxes.isEmpty(),"Underground start protected entire city columns above it");
            var footprint=new BoundingBox(pos.getX()-32,pos.getY(),pos.getZ()-32,pos.getX()+47,pos.getY()+18,pos.getZ()+47);
            var clip=new BoundingBox(pos.getX(),level.getMinY(),pos.getZ(),pos.getX()+15,level.getMinY()+level.getHeight()-1,pos.getZ()+15);
            for(int x=pos.getX();x<=pos.getX()+15;x++)for(int z=pos.getZ();z<=pos.getZ()+15;z++) {
                for(int y=pos.getY()-36;y<=pos.getY()+24;y++)level.setBlock(new BlockPos(x,y,z),
                        (y<=pos.getY()-20?Blocks.STONE:y==pos.getY()-1?Blocks.DIRT:Blocks.AIR).defaultBlockState(),18);
                level.setBlock(new BlockPos(x,pos.getY()+5,z),Blocks.OAK_LEAVES.defaultBlockState(),18);
            }
            SurfaceStructurePlacement.gradeCity(level,footprint,clip,pos.getY(),protectedBoxes);
            int checked=0;
            for(int x=pos.getX();x<=pos.getX()+15;x++)for(int z=pos.getZ();z<=pos.getZ()+15;z++) {
                for(int y=pos.getY()-1;y>=pos.getY()-19;y--) {
                    check(!level.getBlockState(new BlockPos(x,y,z)).isAir(),"Foundation hole above underground structure");checked++;
                }
                check(level.getBlockState(new BlockPos(x,pos.getY()+5,z)).isAir(),"Underground structure kept natural leaves above city");
            }
            System.out.println("CITY UNDERGROUND CHECK: "+checked+" supported blocks and 256 cleared foliage columns above saved mineshaft");
        } finally { loaded.setAllStarts(starts);loaded.setAllReferences(refs); }
        h.succeed();
    }
}
