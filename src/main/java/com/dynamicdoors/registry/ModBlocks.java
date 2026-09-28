package com.dynamicdoors.registry;

import com.dynamicdoors.DynamicDoors;
import com.dynamicdoors.block.DoorWood;
import com.dynamicdoors.block.RoundDoorBlock;
import com.dynamicdoors.block.RoundDoorBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, DynamicDoors.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DynamicDoors.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, DynamicDoors.MODID);

    public static final Map<DoorWood, RegistryObject<RoundDoorBlock>> ROUND_DOORS = new EnumMap<>(DoorWood.class);

    static {
        for (DoorWood wood : DoorWood.values()) {
            RegistryObject<RoundDoorBlock> block = BLOCKS.register(wood.blockId(), () -> new RoundDoorBlock(wood,
                    BlockBehaviour.Properties.copy(wood.vanillaDoor).pushReaction(PushReaction.BLOCK)));
            ROUND_DOORS.put(wood, block);
            ITEMS.register(wood.blockId(), () -> new BlockItem(block.get(), new Item.Properties()));
        }
    }

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<RoundDoorBlockEntity>> ROUND_DOOR = BLOCK_ENTITIES.register(
            "round_door", () -> BlockEntityType.Builder.of(RoundDoorBlockEntity::new,
                    ROUND_DOORS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    private ModBlocks() {
    }

    public static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            for (RegistryObject<RoundDoorBlock> door : ROUND_DOORS.values()) event.accept(door);
        } else if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ROUND_DOORS.get(DoorWood.IRON));
        }
    }
}
