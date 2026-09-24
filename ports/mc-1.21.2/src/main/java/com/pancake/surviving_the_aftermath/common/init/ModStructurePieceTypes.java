package com.pancake.surviving_the_aftermath.common.init;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.structure.BurntStructure;
import com.pancake.surviving_the_aftermath.common.structure.CityStructure;
import com.pancake.surviving_the_aftermath.common.structure.HouseOfSakura;
import com.pancake.surviving_the_aftermath.common.structure.NetherRaidStructure;
import com.pancake.surviving_the_aftermath.common.structure.expansion.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public class ModStructurePieceTypes {

	public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES = DeferredRegister.create(Registries.STRUCTURE_PIECE.location(), SurvivingTheAftermath.MOD_ID);
	public static final Supplier<StructurePieceType> HOUSE_OF_SAKURA = register("house_of_sakura", HouseOfSakura.Piece::new);
	public static final Supplier<StructurePieceType> NETHER_RAID = register("nether_invasion_portal", NetherRaidStructure.Piece::new);
	public static final Supplier<StructurePieceType> CITY = register("city", CityStructure.Piece::new);
	public static final Supplier<StructurePieceType> CAMP = register("camp", CampStructure.Piece::new);
	public static final Supplier<StructurePieceType> LOGS = register("logs", LogsStructure.Piece::new);
	public static final Supplier<StructurePieceType> TENT = register("tent", TentStructure.Piece::new);
	public static final Supplier<StructurePieceType> BRICK_WELL = register("brick_well", BrickWellStructure.Piece::new);
	public static final Supplier<StructurePieceType> COBBLESTONE_PILE = register("cobblestone_pile", CobblestonePileStructure.Piece::new);
	public static final Supplier<StructurePieceType> CONSTRUCTION_1 = register("construction1", (context, tag) -> new ConstructionStructure.Piece(context, tag, 1));
	public static final Supplier<StructurePieceType> CONSTRUCTION_2 = register("construction2", (context, tag) -> new ConstructionStructure.Piece(context, tag, 2));
	public static final Supplier<StructurePieceType> WAGON_CARGO_1 = register("wagon_cargo1", (context, tag) -> new WagonCargoStructure.Piece(context, tag, 1));
	public static final Supplier<StructurePieceType> WAGON_CARGO_2 = register("wagon_cargo2", (context, tag) -> new WagonCargoStructure.Piece(context, tag, 2));
	public static final Supplier<StructurePieceType> WAGON_CARGO_3 = register("wagon_cargo3", (context, tag) -> new WagonCargoStructure.Piece(context, tag, 3));
	public static final Supplier<StructurePieceType> WAGON_CARGO_4 = register("wagon_cargo4", (context, tag) -> new WagonCargoStructure.Piece(context, tag, 4));
	public static final Supplier<StructurePieceType> WAGON_CARGO_5 = register("wagon_cargo5", (context, tag) -> new WagonCargoStructure.Piece(context, tag, 5));
	public static final Supplier<StructurePieceType> WAGON_CARGO_6 = register("wagon_cargo6", (context, tag) -> new WagonCargoStructure.Piece(context, tag, 6));
	public static final Supplier<StructurePieceType> BURNT_1 = register("burnt_structure1", (context, tag) -> new BurntStructure.Piece(context, tag, 1));
	public static final Supplier<StructurePieceType> BURNT_2 = register("burnt_structure2", (context, tag) -> new BurntStructure.Piece(context, tag, 2));
	public static final Supplier<StructurePieceType> BURNT_3 = register("burnt_structure3", (context, tag) -> new BurntStructure.Piece(context, tag, 3));
	public static final Supplier<StructurePieceType> BURNT_4 = register("burnt_structure4", (context, tag) -> new BurntStructure.Piece(context, tag, 4));
	public static final Supplier<StructurePieceType> BURNT_5 = register("burnt_structure5", (context, tag) -> new BurntStructure.Piece(context, tag, 5));
	public static final Supplier<StructurePieceType> BURNT_6 = register("burnt_structure6", (context, tag) -> new BurntStructure.Piece(context, tag, 6));

	private static Supplier<StructurePieceType> register(String name, StructurePieceType pieceType) {
		return STRUCTURE_PIECE_TYPES.register(name, () -> pieceType);
	}

}