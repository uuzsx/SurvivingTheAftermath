package com.pancake.surviving_the_aftermath;

import com.pancake.surviving_the_aftermath.common.init.*;
import com.pancake.surviving_the_aftermath.common.structure.AbstractStructure;
import com.pancake.surviving_the_aftermath.common.util.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.*;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

@net.minecraftforge.gametest.GameTestHolder(SurvivingTheAftermath.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class CityBoundaryGameTests {
    private static void check(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
    private static long key(ChunkPos chunk) {
        return ((chunk.getMinBlockX()>>4)&0xffffffffL)|((long)(chunk.getMinBlockZ()>>4)<<32);
    }
    private static BoundingBox clip(net.minecraft.server.level.ServerLevel level,ChunkPos chunk) {
        return new BoundingBox(chunk.getMinBlockX(),level.getMinBuildHeight(),chunk.getMinBlockZ(),chunk.getMaxBlockX(),level.getMinBuildHeight()+level.getHeight()-1,chunk.getMaxBlockZ());
    }
    private static void remember(net.minecraft.server.level.ServerLevel level,Map<BlockPos,BlockState> states,
                                 int minX,int maxX,int minZ,int maxZ,int minY,int maxY) {
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=minY;y<=maxY;y++) {
            var pos=new BlockPos(x,y,z);states.put(pos,level.getBlockState(pos));
        }
    }

    @GameTest(template = "stability_empty", batch = "city_boundary", timeoutTicks = 600)
    public static void cityBoundaryKeepsWaterTreesAndBuildings(GameTestHelper h) {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(48000,90,48000));
        int x0=(origin.getX()>>4)*16,z0=(origin.getZ()>>4)*16,soil=origin.getY(),floor=soil+5;
        var footprint=new BoundingBox(x0,floor,z0,x0+15,floor+16,z0+15);
        var area=SurfaceStructurePlacement.cityTerrainBounds(footprint);
        for(int cx=(area.minX()>>4)-1;cx<=(area.maxX()>>4)+1;cx++)for(int cz=(area.minZ()>>4)-1;cz<=(area.maxZ()>>4)+1;cz++)
            level.getChunk(cx,cz).setAllReferences(new HashMap<>());
        for(int x=area.minX()-3;x<=area.maxX()+3;x++)for(int z=area.minZ()-3;z<=area.maxZ()+3;z++) {
            int top=Math.max(floor+20,level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z));
            // Cover the entire obstacle scan depth, including ore-bearing natural terrain below the fixture.
            for(int y=soil-24;y<=top;y++) level.setBlock(new BlockPos(x,y,z),
                    (y==soil?Blocks.GRASS_BLOCK:y<soil?Blocks.STONE:Blocks.AIR).defaultBlockState(),18);
        }
        // Shoreline crosses a chunk boundary, with both source and flowing water.
        for(int x=x0+18;x<=x0+27;x++)for(int z=z0+1;z<=z0+5;z++) {
            level.setBlock(new BlockPos(x,soil-2,z),Blocks.CLAY.defaultBlockState(),18);
            for(int y=soil-1;y<=soil;y++)level.setBlock(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState(),18);
        }
        level.setBlock(new BlockPos(x0+18,soil,z0+1),Blocks.WATER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL,3),18);
        // Tree beyond the wall; its crown crosses the old/new transition boundary.
        int treeX=x0+20,treeZ=z0+12;
        for(int y=soil+1;y<=soil+7;y++)level.setBlock(new BlockPos(treeX,y,treeZ),Blocks.BIRCH_LOG.defaultBlockState(),18);
        for(int x=treeX-3;x<=treeX+3;x++)for(int z=treeZ-2;z<=treeZ+2;z++)
            level.setBlock(new BlockPos(x,soil+8,z),Blocks.BIRCH_LEAVES.defaultBlockState(),18);
        // An unregistered building must also survive, including its empty interior and inventory.
        for(int x=x0+6;x<=x0+10;x++)for(int z=z0-5;z<=z0-2;z++) {
            level.setBlock(new BlockPos(x,soil+1,z),Blocks.OAK_PLANKS.defaultBlockState(),18);
            level.setBlock(new BlockPos(x,soil+5,z),Blocks.OAK_PLANKS.defaultBlockState(),18);
        }
        var chest=new BlockPos(x0+8,soil+2,z0-3);
        level.setBlock(chest,Blocks.CHEST.defaultBlockState(),18);
        ((ChestBlockEntity)level.getBlockEntity(chest)).setItem(0,new ItemStack(Items.DIAMOND,7));
        var cave=new BlockPos(x0-3,soil-6,z0+7);level.setBlock(cave,Blocks.AIR.defaultBlockState(),18);
        Map<BlockPos,BlockState> before=new HashMap<>();
        remember(level,before,x0+16,x0+27,z0-1,z0+7,soil-4,soil+2);
        remember(level,before,treeX-3,treeX+3,treeZ-2,treeZ+2,soil-3,soil+9);
        remember(level,before,x0+6,x0+10,z0-5,z0-2,soil-2,soil+7);
        remember(level,before,x0-12,x0-8,z0,z0+15,soil-8,floor+2);
        var chunks=new java.util.ArrayList<ChunkPos>();
        for(int cx=area.minX()>>4;cx<=area.maxX()>>4;cx++)for(int cz=area.minZ()>>4;cz<=area.maxZ()>>4;cz++)chunks.add(new ChunkPos(cx,cz));
        java.util.Collections.shuffle(chunks,new java.util.Random(109));
        for(var chunk:chunks)SurfaceStructurePlacement.gradeCity(level,footprint,clip(level,chunk),floor);
        for(var entry:before.entrySet())check(level.getBlockState(entry.getKey()).equals(entry.getValue()),"City changed protected boundary at "+entry.getKey());
        check(((ChestBlockEntity)level.getBlockEntity(chest)).getItem(0).getCount()==7,"City changed adjacent chest contents");
        check(level.getBlockState(cave).isAir(),"City filled a cave outside its footprint");
        check(level.getBlockState(new BlockPos(x0-3,soil+2,z0+7)).is(Blocks.GRASS_BLOCK),"Bare ground did not receive a shallow transition");
        System.out.println("CITY BOUNDARY CHECK: water, shore, tree, unregistered building and chest preserved; "+before.size()+" blocks checked; shuffled chunks");
        h.succeed();
    }

    @GameTest(template = "stability_empty", batch = "city_boundary", timeoutTicks = 600)
    public static void cityTemplateAirKeepsOverhangingTree(GameTestHelper h) {
        var level=h.getLevel();var manager=level.getStructureManager();
        var origin=h.absolutePos(new BlockPos(49000,90,49000));
        var piece=new AbstractStructure.Piece(ModStructurePieceTypes.CITY.get(),manager,SurvivingTheAftermath.asResource("city"),origin,Rotation.NONE);
        var footprint=piece.template().getBoundingBox(piece.placeSettings(),origin);
        var leaf=piece.template().filterBlocks(origin,piece.placeSettings(),Blocks.AIR).stream()
                .map(i->i.pos()).filter(p->p.getX()==footprint.maxX()-1 && p.getY()>origin.getY()+10).findFirst().orElseThrow();
        var log=new BlockPos(footprint.maxX()+1,leaf.getY(),leaf.getZ());
        for(int cx=(leaf.getX()>>4)-1;cx<=(log.getX()>>4)+1;cx++)for(int cz=(leaf.getZ()>>4)-1;cz<=(leaf.getZ()>>4)+1;cz++)level.getChunk(cx,cz);
        level.setBlock(new BlockPos(log.getX(),origin.getY(),log.getZ()),Blocks.GRASS_BLOCK.defaultBlockState(),18);
        for(int y=origin.getY()+1;y<=log.getY();y++)level.setBlock(new BlockPos(log.getX(),y,log.getZ()),Blocks.OAK_LOG.defaultBlockState(),18);
        level.setBlock(log.west(),Blocks.OAK_LEAVES.defaultBlockState(),18);
        level.setBlock(leaf,Blocks.OAK_LEAVES.defaultBlockState(),18);
        var chunk=new ChunkPos(leaf.getX()>>4,leaf.getZ()>>4);
        var saved=StructurePieceSerializationContext.fromLevel(level);
        piece=new AbstractStructure.Piece(ModStructurePieceTypes.CITY.get(),saved,piece.createTag(saved));
        piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(5),clip(level,chunk),chunk,origin);
        check(level.getBlockState(leaf).is(Blocks.OAK_LEAVES),"Template air cut a live crown hanging over city boundary");
        check(level.getBlockState(log).is(Blocks.OAK_LOG),"City cut external tree trunk");
        System.out.println("CITY CANOPY AIR CHECK: live overhanging crown preserved through actual template placement and piece reload");
        h.succeed();
    }

    @GameTest(template = "stability_empty", batch = "city_boundary", timeoutTicks = 600)
    public static void cityCleanupKeepsLiveCrownsAndRemovesOrphans(GameTestHelper h) {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(51000,90,51000));
        var piece=new AbstractStructure.Piece(ModStructurePieceTypes.CITY.get(),level.getStructureManager(),SurvivingTheAftermath.asResource("city"),origin,Rotation.NONE);
        var footprint=piece.template().getBoundingBox(piece.placeSettings(),origin);
        var live=new BlockPos(footprint.maxX()-1,footprint.maxY()+3,footprint.minZ()+20);
        var log=live.east(3);var orphan=live.south(18).east(6);var internal=live.west(15);
        var forced=new java.util.HashSet<ChunkPos>();
        for(var pos:List.of(live,orphan,internal,origin))for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            var chunk=new ChunkPos((pos.getX()>>4)+dx,(pos.getZ()>>4)+dz);
            if(forced.add(chunk)){level.setChunkForced(chunk.getMinBlockX()>>4,chunk.getMinBlockZ()>>4,true);level.getChunk(chunk.getMinBlockX()>>4,chunk.getMinBlockZ()>>4).setAllReferences(new HashMap<>());}
        }
        var city=level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ModStructures.CITY).value();
        var startChunk=new ChunkPos(origin.getX()>>4,origin.getZ()>>4);
        level.getChunk(startChunk.getMinBlockX()>>4,startChunk.getMinBlockZ()>>4).setStartForStructure(city,new StructureStart(city,startChunk,0,new PiecesContainer(List.of(piece))));
        // Forge's test world can contain solid natural terrain at this height. Establish an
        // actually unsupported orphan, instead of mistaking a log rooted on granite for one.
        for(int x=orphan.getX()-8;x<=orphan.getX()+8;x++)for(int z=orphan.getZ()-8;z<=orphan.getZ()+8;z++)
            for(int y=orphan.getY()-8;y<=orphan.getY()+8;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),18);
        var leaves=Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT,false).setValue(BlockStateProperties.DISTANCE,1);
        level.setBlock(new BlockPos(log.getX(),origin.getY(),log.getZ()),Blocks.GRASS_BLOCK.defaultBlockState(),18);
        for(int y=origin.getY()+1;y<=log.getY();y++)level.setBlock(new BlockPos(log.getX(),y,log.getZ()),Blocks.OAK_LOG.defaultBlockState(),18);
        for(int dx=0;dx<3;dx++)level.setBlock(live.east(dx),leaves,18);
        level.setBlock(orphan,leaves,18);level.setBlock(orphan.below(),Blocks.OAK_LOG.defaultBlockState(),18);level.setBlock(internal,leaves,18);
        level.setBlock(internal.below(),Blocks.OAK_LOG.defaultBlockState(),18);
        var touched=new java.util.HashSet<ChunkPos>();
        for(var pos:List.of(live,orphan,internal)) {
            var chunk=new ChunkPos(pos.getX()>>4,pos.getZ()>>4);touched.add(chunk);
            level.getChunk(chunk.getMinBlockX()>>4,chunk.getMinBlockZ()>>4).addReferenceForStructure(city,key(startChunk));
            CityVegetationData.get(level).setCleaned(key(chunk),false);CityVegetationCleanup.queue(level,chunk,true);
        }
        h.startSequence().thenWaitUntil(()->{
            for(var chunk:touched)check(CityVegetationData.get(level).isCleaned(key(chunk)),"Boundary foliage cleanup has not run");
        }).thenExecute(()->{
            try {
                for(int dx=0;dx<3;dx++)check(level.getBlockState(live.east(dx)).is(Blocks.OAK_LEAVES),"Cleanup cut external live tree crown");
                check(level.getBlockState(log).is(Blocks.OAK_LOG),"Cleanup changed live tree trunk");
                check(level.getBlockState(orphan).isAir(),"Disconnected leaf outside city remained floating");
                check(level.getBlockState(internal).isAir(),"City's own wooden beam incorrectly protected orphan leaves");
                System.out.println("CITY CANOPY CLEANUP CHECK: external live crown retained, internal and external orphan leaves removed");
            } finally {for(var chunk:forced)level.setChunkForced(chunk.getMinBlockX()>>4,chunk.getMinBlockZ()>>4,false);}
        }).thenSucceed();
    }
}
