package com.dynamicdoors.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** One round door per vanilla door. Textures are the vanilla ones, referenced (not copied). */
public enum DoorWood {
    OAK("oak", BlockSetType.OAK, Blocks.OAK_DOOR, "oak_planks"),
    SPRUCE("spruce", BlockSetType.SPRUCE, Blocks.SPRUCE_DOOR, "spruce_planks"),
    BIRCH("birch", BlockSetType.BIRCH, Blocks.BIRCH_DOOR, "birch_planks"),
    JUNGLE("jungle", BlockSetType.JUNGLE, Blocks.JUNGLE_DOOR, "jungle_planks"),
    ACACIA("acacia", BlockSetType.ACACIA, Blocks.ACACIA_DOOR, "acacia_planks"),
    DARK_OAK("dark_oak", BlockSetType.DARK_OAK, Blocks.DARK_OAK_DOOR, "dark_oak_planks"),
    MANGROVE("mangrove", BlockSetType.MANGROVE, Blocks.MANGROVE_DOOR, "mangrove_planks"),
    CHERRY("cherry", BlockSetType.CHERRY, Blocks.CHERRY_DOOR, "cherry_planks"),
    BAMBOO("bamboo", BlockSetType.BAMBOO, Blocks.BAMBOO_DOOR, "bamboo_planks"),
    CRIMSON("crimson", BlockSetType.CRIMSON, Blocks.CRIMSON_DOOR, "crimson_planks"),
    WARPED("warped", BlockSetType.WARPED, Blocks.WARPED_DOOR, "warped_planks"),
    IRON("iron", BlockSetType.IRON, Blocks.IRON_DOOR, "iron_block");

    public final String name;
    public final BlockSetType type;
    public final Block vanillaDoor;
    /** Frame and panel edges. */
    public final ResourceLocation frameTexture;
    /** One 48x48 picture over each face of the panel (drawn by tools/GenTextures.java). */
    public final ResourceLocation panelTexture;

    DoorWood(String name, BlockSetType type, Block vanillaDoor, String frameTexture) {
        this.name = name;
        this.type = type;
        this.vanillaDoor = vanillaDoor;
        this.frameTexture = new ResourceLocation("minecraft", "block/" + frameTexture);
        this.panelTexture = new ResourceLocation("dynamicdoors", "block/" + name + "_round_door");
    }

    public String blockId() {
        return name + "_round_door";
    }
}
