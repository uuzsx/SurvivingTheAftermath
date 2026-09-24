package com.pancake.surviving_the_aftermath.common.util;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

import java.util.Objects;

public class RegistryUtil {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static EntityType<?> getEntityTypeFromRegistryName(String registryName) {
        EntityType<?> entityType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.tryParse(registryName));
        if (entityType == null) {
            LOGGER.error("Entity with registry name {} does not exist!", registryName);
        }
        return entityType;
    }
    public static Identifier getRegistryNameFromEntityType(EntityType<?> entityType) {
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
    }


    public static Block getBlockFromRegistryName(String registryName) {
        Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.tryParse(registryName));
        if (block == null) {
            LOGGER.error("Block with registry name {} does not exist!", registryName);
        }
        return block;
    }

    public static Identifier getRegistryNameFromBlock(Block block) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
    }

    public static Item getItemFromRegistryName(String registryName) {
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.tryParse(registryName));
        if (item == null) {
            LOGGER.error("Item with registry name {} does not exist!", registryName);
        }
        return item;
    }

    public static Identifier getRegistryNameFromItem(Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
    }

    public static ResourceKey<Structure> keyStructure(String name) {
        return ResourceKey.create(Registries.STRUCTURE, Objects.requireNonNull(Identifier.tryParse(name)));
    }

//    public static Iterable<Block> getKnownBlocks() {
//        return ModBlocks.BLOCKS.getEntries().stream().map(RegistryObject::get)::iterator;
//    }
//
//    public static Iterable<Item> getKnownItems() {
//        return ModItems.ITEMS.getEntries().stream().map(RegistryObject::get)::iterator;
//    }

}
