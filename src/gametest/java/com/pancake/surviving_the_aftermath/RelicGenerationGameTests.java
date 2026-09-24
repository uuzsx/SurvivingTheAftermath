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
public final class RelicGenerationGameTests {
    private static void check(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
    @GameTest(template = "stability_empty", batch = "relic_generation", timeoutTicks = 600)
    public static void relicDealerRegistryAndTexture(GameTestHelper h) throws Exception {
        var id=net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(ModVillagers.RELIC_DEALER.get());
        check(SurvivingTheAftermath.asResource("relic_dealer").equals(id), "Relic profession is not registered");
        String resource="/assets/"+id.getNamespace()+"/textures/entity/villager/profession/"+id.getPath()+".png";
        try(var input=RelicGenerationGameTests.class.getResourceAsStream(resource)) {
            check(input!=null,"Missing texture at the renderer's profession path: "+resource);
            var png=javax.imageio.ImageIO.read(input);
            check(png!=null && png.getWidth()==64 && png.getHeight()==64,"Invalid villager profession PNG");
            int visible=0;
            for(int x=0;x<64;x++)for(int y=0;y<64;y++)if((png.getRGB(x,y)>>>24)>0)visible++;
            check(visible>100,"Profession texture is empty/transparent");
            System.out.println("RELIC REGISTRY/TEXTURE CHECK: id="+id+", PNG=64x64, visible_pixels="+visible);
        }
        h.succeed();
    }

    @GameTest(template = "stability_empty", batch = "relic_generation", timeoutTicks = 600)
    public static void naturalCityCreatesRelicDealers(GameTestHelper h) {
        var level=h.getLevel();var lookup=level.registryAccess();var manager=level.getStructureManager();
        var noise=lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var source=MultiNoiseBiomeSource.createFromPreset(lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        var generator=new NoiseBasedChunkGenerator(source,noise);long seed=0L;
        var randomState=RandomState.create(noise.value(),lookup.lookupOrThrow(Registries.NOISE),seed);
        var city=lookup.lookupOrThrow(Registries.STRUCTURE).getOrThrow(ModStructures.CITY).value();
        var placement=(RandomSpreadStructurePlacement)lookup.lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(ModStructureSets.CITY_SET).value().placement();
        ChunkPos candidate=null;Structure.GenerationStub stub=null;
        search: for(int radius=0;radius<=4;radius++)for(int rx=-radius;rx<=radius;rx++)for(int rz=-radius;rz<=radius;rz++) {
            if(Math.max(Math.abs(rx),Math.abs(rz))!=radius)continue;
            var proposed=placement.getPotentialStructureChunk(seed,rx*placement.spacing(),rz*placement.spacing());
        var context=new Structure.GenerationContext(lookup,generator,source,randomState,manager,seed,proposed,level,b->b.is(ModTags.HAS_CITY));
            var found=city.findValidGenerationPoint(context);
            if(found.isPresent()){candidate=proposed;stub=found.get();break search;}
        }
        check(stub!=null,"No suitable dry city site found in the generation survey");
        var pieces=stub.getPiecesBuilder().build();
        check(pieces.pieces().size()==1 && pieces.pieces().get(0) instanceof com.pancake.surviving_the_aftermath.common.structure.CityStructure.Piece,
                "Fresh natural city uses generic piece and skips villager population");
        var start=new StructureStart(city,candidate,0,pieces);
        var piece=(AbstractStructure.Piece)pieces.pieces().get(0);
        var area=start.getBoundingBox();int floor=piece.templatePosition().getY();
        var entityArea=net.minecraft.world.phys.AABB.of(area).inflate(2);
        var chunks=new java.util.ArrayList<ChunkPos>();
        try {
        for(int cx=area.minX()>>4;cx<=area.maxX()>>4;cx++)for(int cz=area.minZ()>>4;cz<=area.maxZ()>>4;cz++) {
            level.setChunkForced(cx,cz,true);level.getChunk(cx,cz).setAllReferences(new java.util.HashMap<>());
            chunks.add(new ChunkPos(cx,cz));
        }
        // The headless test world is flat. Supply ground at the real-noise chosen city height.
        // Selection, piece creation, chunk placement and population use production code;
        // no test code spawns villagers or assigns a profession.
        for(int x=area.minX();x<=area.maxX();x++)for(int z=area.minZ();z<=area.maxZ();z++) {
            for(int y=floor-4;y<=area.maxY()+2;y++)level.setBlock(new BlockPos(x,y,z),
                    (y<=floor?Blocks.STONE:Blocks.AIR).defaultBlockState(),18);
        }
        java.util.List<net.minecraft.world.entity.npc.Villager> residents=java.util.List.of();
        java.util.List<net.minecraft.world.entity.npc.Villager> dealers=java.util.List.of();
        // Draw independent chunk seeds instead of nearby seeds with correlated PRNG prefixes.
        var populationSeeds=RandomSource.create(0x6A09E667F3BCC909L);
        int sample=0;
        for(;sample<8;sample++) {
            level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,entityArea).forEach(net.minecraft.world.entity.Entity::discard);
            for(var chunk:chunks) {
                var writable=new BoundingBox(chunk.getMinBlockX(),level.getMinBuildHeight(),chunk.getMinBlockZ(),chunk.getMaxBlockX(),level.getMinBuildHeight()+level.getHeight()-1,chunk.getMaxBlockZ());
                long populationSeed=populationSeeds.nextLong();
                start.placeInChunk(level,level.structureManager(),generator,RandomSource.create(populationSeed),writable,chunk);
            }
            residents=level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,entityArea);
            dealers=residents.stream().filter(v->v.getVillagerData().getProfession()==ModVillagers.RELIC_DEALER.get()).toList();
            System.out.println("RELIC POPULATION SAMPLE: sample="+sample+", residents="+residents.size()+", relic_dealers="+dealers.size());
            check(dealers.stream().filter(d -> d.getTags().contains("aftermath_city_dealer")).count() == 1, "Each newly generated city must contain exactly one designated relic dealer");
            check(sample == 0, "Designated merchant required a random population retry");
            System.out.println("CORE CITY MERCHANT CHECK: exactly one designated trader in the first natural city population");
            if(!dealers.isEmpty())break;
        }
        check(!residents.isEmpty(),"Natural city placement created no residents");
        check(!dealers.isEmpty(),"No relic dealer appeared in eight deterministic population samples");
        for(var dealer:dealers) {
            check(dealer.getVillagerXp()>0,"Generated dealer would lose its profession");
            check(level.noCollision(dealer),"Generated dealer intersects a city block");
            check(dealer.getOffers().stream().anyMatch(o->o.getBaseCostA().is(ModItems.NETHER_CORE.get()) && o.getResult().is(net.minecraft.world.item.Items.ENCHANTED_BOOK)),
                    "Generated dealer has no core-to-book trade");
        }
        var saved=StructurePieceSerializationContext.fromLevel(level);
        var loaded=StructureStart.loadStaticStart(saved,start.createTag(saved,candidate),seed);
        check(loaded!=null && loaded.getPieces().get(0) instanceof com.pancake.surviving_the_aftermath.common.structure.CityStructure.Piece,
                "City piece type lost across save/reload");
        System.out.println("RELIC CITY GENERATION CHECK: fresh_piece="+piece.getClass().getSimpleName()+", candidate="+candidate+", sample="+sample
                +", residents="+residents.size()+", relic_dealers="+dealers.size()+", chunks="+chunks.size());
        var retained=dealers;var cleanup=residents;
        h.runAfterDelay(240,()->{
            try {
                for(var dealer:retained)check(dealer.isAlive() && dealer.getVillagerData().getProfession()==ModVillagers.RELIC_DEALER.get(),
                        "Naturally generated dealer died or lost its profession while ticking");
                System.out.println("RELIC RETENTION CHECK: "+retained.size()+" generated dealers kept profession for 240 ticks and offered valid trades");
                h.succeed();
            } finally {
                cleanup.forEach(net.minecraft.world.entity.Entity::discard);
                for(var chunk:chunks)level.setChunkForced(chunk.getMinBlockX()>>4,chunk.getMinBlockZ()>>4,false);
            }
        });
        } catch (RuntimeException | Error failure) {
            level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,entityArea).forEach(net.minecraft.world.entity.Entity::discard);
            for(var chunk:chunks)level.setChunkForced(chunk.getMinBlockX()>>4,chunk.getMinBlockZ()>>4,false);
            throw failure;
        }
    }
}
