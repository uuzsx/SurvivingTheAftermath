package com.pancake.surviving_the_aftermath;

import com.google.gson.GsonBuilder;
import com.pancake.surviving_the_aftermath.common.init.ModStructures;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in real dedicated-server worldgen benchmark; excluded from the production JAR. */
@EventBusSubscriber(modid = SurvivingTheAftermath.MOD_ID)
public final class CityPerformanceAudit {
    private static final boolean ENABLED = Boolean.getBoolean("aftermath.cityAudit");
    private static final List<Map<String,Object>> measurements = new ArrayList<>();
    private static final List<ChunkPos> forced = new ArrayList<>();
    private static MinecraftServer server;
    private static int tick, stage, stress;
    private static final int STRESS = Integer.getInteger("aftermath.cityAuditStress",0);
    private static BlockPos located;
    private static net.minecraft.world.level.levelgen.structure.StructureStart cityStart;

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!ENABLED) return;
        server = event.getServer();
        record("world_started",ManagementFactory.getRuntimeMXBean().getUptime(),Map.of("pid",ProcessHandle.current().pid(),"seed",server.overworld().getSeed()));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || server == null || ++tick < 40) return;
        tick = 0;
        try {
            if (stage < 3) {
                int run = stage++;
                var source = server.createCommandSourceStack().withPosition(new Vec3(0,80,0)).withSource(new CommandSource() {
                    public boolean acceptsSuccess(){return true;}
                    public boolean acceptsFailure(){return true;}
                    public boolean shouldInformAdmins(){return false;}
                    public void sendSystemMessage(Component message) {
                        System.out.println("CITY_AUDIT_COMMAND " + message.getString());
                        coordinates(message);
                    }
                });
                long start=System.nanoTime();
                server.getCommands().performPrefixedCommand(source,"locate structure surviving_the_aftermath:city");
                long millis=(System.nanoTime()-start)/1_000_000;
                record(run==0?"locate_cold":"locate_repeat_"+run,millis,Map.of("target",String.valueOf(located)));
                if(located==null)throw new IllegalStateException("Actual /locate did not find a city");
            } else if(stage==3) {
                stage++;
                var level=server.overworld();
                var city=level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ModStructures.CITY).value();
                long start=System.nanoTime();
                cityStart=level.getChunk(located.getX()>>4,located.getZ()>>4).getStartForStructure(city);
                if(cityStart==null || !cityStart.isValid())throw new IllegalStateException("Located start is missing");
                var box=cityStart.getBoundingBox();
                for(int x=(box.minX()>>4)-1;x<=(box.maxX()>>4)+1;x++)for(int z=(box.minZ()>>4)-1;z<=(box.maxZ()>>4)+1;z++) {
                    level.setChunkForced(x,z,true);level.getChunk(x,z);forced.add(new ChunkPos(x,z));
                }
                record("generate_city_and_neighbors",(System.nanoTime()-start)/1_000_000,Map.of("chunks",forced.size()));
                tick=-160; // Let neighboring decorations and deferred cleanup complete.
            } else if(stage==4) {
                stage++;
                var level=server.overworld();
                var piece=(TemplateStructurePiece)cityStart.getPieces().get(0);
                var box=piece.template().getBoundingBox(piece.placeSettings(),piece.templatePosition());
                int ground=piece.templatePosition().getY(),leaves=0,missing=0,columns=0;
                var data=new ArrayList<Map<String,Object>>();var cursor=new BlockPos.MutableBlockPos();
                for(int x=box.minX();x<=box.maxX();x++)for(int z=box.minZ();z<=box.maxZ();z++) {
                    int columnLeaves=0,voids=0;
                    for(int y=ground+1;y<=ground+40;y++)if(level.getBlockState(cursor.set(x,y,z)).is(BlockTags.LEAVES))columnLeaves++;
                    for(int y=ground-1;y>=ground-8;y--)if(level.getBlockState(cursor.set(x,y,z)).isAir())voids++;
                    leaves+=columnLeaves;missing+=voids;if(voids>0)columns++;
                    data.add(Map.of("x",x,"z",z,"leaves",columnLeaves,"foundation_air",voids));
                }
                record("city_voxel_audit",0,Map.of("interior_leaves",leaves,"foundation_air",missing,"unsupported_columns",columns,"origin",piece.templatePosition().toShortString()));
                Files.writeString(Path.of("city-columns.json"),new GsonBuilder().create().toJson(data));
                for(var chunk:forced)level.setChunkForced(chunk.getMinBlockX()>>4,chunk.getMinBlockZ()>>4,false);
                tick=-160;
            } else if(stress<STRESS) {
                int index=stress++;
                var source=server.createCommandSourceStack().withPosition(new Vec3(12000*(index+1),80,8000*(index+1)));
                long start=System.nanoTime();
                server.getCommands().performPrefixedCommand(source,"locate structure surviving_the_aftermath:city");
                record("locate_new_area_"+index,(System.nanoTime()-start)/1_000_000,Map.of());
                if(stress==STRESS)tick=-560;
            } else {
                stage++;
                record("after_release",0,Map.of());
                server.halt(false);server=null;
            }
        } catch(Throwable error) {
            error.printStackTrace();
            record("failed",0,Map.of("error",error.toString()));
            server.halt(false);server=null;
        }
    }

    private static void coordinates(Component message) {
        if(message.getContents() instanceof TranslatableContents translated) {
            Object[] args=translated.getArgs();
            if(translated.getKey().equals("chat.coordinates") && args.length==3 && args[0] instanceof Number x && args[2] instanceof Number z)
                located=new BlockPos(x.intValue(),80,z.intValue());
            for(Object arg:args)if(arg instanceof Component child)coordinates(child);
        }
        message.getSiblings().forEach(CityPerformanceAudit::coordinates);
    }

    private static void record(String name,long millis,Map<String,Object> extra) {
        long before=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        System.gc();
        var heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        var row=new LinkedHashMap<String,Object>();row.put("stage",name);row.put("elapsed_ms",millis);
        row.put("heap_before_gc",before);row.put("heap_after_gc",heap.getUsed());row.put("heap_max",heap.getMax());
        row.put("gc_ms",ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionTime())).sum());
        row.putAll(extra);measurements.add(row);
        System.out.println("CITY_AUDIT "+new GsonBuilder().create().toJson(row));
        try {Files.writeString(Path.of("city-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(measurements));}
        catch(Exception e){throw new RuntimeException(e);}
    }
}
