package com.dynamicdoors.dev;

import com.dynamicdoors.DynamicDoors;
import com.dynamicdoors.block.DoorWood;
import com.dynamicdoors.block.RoundDoorBlock;
import com.dynamicdoors.block.RoundDoorGeometry;
import com.dynamicdoors.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Dev-only GameTests for the round door multiblock (run with {@code ./gradlew runGameTestServer}). */
@GameTestHolder(DynamicDoors.MODID)
@PrefixGameTestTemplate(false)
public final class DynamicDoorsGameTests {
    private static final String TEMPLATE = "empty";

    private DynamicDoorsGameTests() {
    }

    private static BlockPos place(GameTestHelper helper, DoorWood wood, Direction facing) {
        ServerLevel level = helper.getLevel();
        BlockPos master = helper.absolutePos(new BlockPos(3, 2, 3));
        RoundDoorBlock block = ModBlocks.ROUND_DOORS.get(wood).get();
        BlockState state = block.defaultBlockState().setValue(RoundDoorBlock.FACING, facing);
        level.setBlock(master, state, 3);
        block.setPlacedBy(level, master, state, null, ItemStack.EMPTY);
        return master;
    }

    private static void checkAll(GameTestHelper helper, BlockPos master, Direction facing, boolean open) {
        for (int part = 0; part < 9; part++) {
            BlockPos p = RoundDoorBlock.partPos(master, facing, part);
            BlockState s = helper.getLevel().getBlockState(p);
            if (!(s.getBlock() instanceof RoundDoorBlock)) helper.fail("part " + part + " missing at " + p);
            if (s.getValue(RoundDoorBlock.PART) != part) helper.fail("part " + part + " has index " + s.getValue(RoundDoorBlock.PART));
            if (s.getValue(RoundDoorBlock.OPEN) != open) helper.fail("part " + part + " open=" + !open);
            if (!RoundDoorBlock.masterPos(p, s).equals(master)) helper.fail("part " + part + " points to the wrong master");
        }
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    @GameTest(template = TEMPLATE)
    public static void placeToggleBreak(GameTestHelper helper) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            ServerLevel level = helper.getLevel();
            BlockPos master = place(helper, DoorWood.OAK, facing);
            checkAll(helper, master, facing, false);

            // Centre part: closed = panel, open = inside the round hole, no collision.
            BlockPos center = RoundDoorBlock.partPos(master, facing, 4);
            if (level.getBlockState(center).getCollisionShape(level, center).isEmpty()) helper.fail("closed centre has no collision");

            Player player = helper.makeMockPlayer();
            BlockPos corner = RoundDoorBlock.partPos(master, facing, 8);
            level.getBlockState(corner).use(level, player, InteractionHand.MAIN_HAND, hit(corner));
            checkAll(helper, master, facing, true);
            if (!level.getBlockState(center).getCollisionShape(level, center).isEmpty()) helper.fail("open centre still collides");
            if (level.getBlockState(corner).getCollisionShape(level, corner).isEmpty()) helper.fail("frame corner lost its collision");

            level.getBlockState(master).use(level, player, InteractionHand.MAIN_HAND, hit(master));
            checkAll(helper, master, facing, false);

            // Breaking any part removes the whole door and drops exactly one item.
            level.destroyBlock(corner, true);
            for (int part = 0; part < 9; part++) {
                if (!level.getBlockState(RoundDoorBlock.partPos(master, facing, part)).isAir()) helper.fail("part " + part + " survived");
            }
            AABB area = new AABB(master).inflate(4);
            int drops = level.getEntitiesOfClass(ItemEntity.class, area).stream().mapToInt(e -> e.getItem().getCount()).sum();
            if (drops != 1) helper.fail(facing + ": dropped " + drops + " items");
            level.getEntitiesOfClass(ItemEntity.class, area).forEach(e -> e.discard());
        }
        helper.succeed();
    }

    /** The open panel is solid: leaf blocks fill its space, never overwrite blocks, and go with the door. */
    @GameTest(template = TEMPLATE)
    public static void openPanelIsSolid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (DoorHingeSide hinge : DoorHingeSide.values()) {
                BlockPos master = helper.absolutePos(new BlockPos(3, 2, 3));
                RoundDoorBlock block = ModBlocks.ROUND_DOORS.get(DoorWood.BIRCH).get();
                BlockState state = block.defaultBlockState().setValue(RoundDoorBlock.FACING, facing).setValue(RoundDoorBlock.HINGE, hinge);
                level.setBlock(master, state, 3);
                block.setPlacedBy(level, master, state, null, ItemStack.EMPTY);
                BlockPos wall = RoundDoorBlock.leafPos(master, facing, hinge, 4);
                level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);

                level.getBlockState(master).use(level, player, InteractionHand.MAIN_HAND, hit(master));
                AABB panel = RoundDoorGeometry.openPanelBox(master, facing, hinge).inflate(1e-4);
                for (int k = 0; k < 9; k++) {
                    BlockPos p = RoundDoorBlock.leafPos(master, facing, hinge, k);
                    BlockState s = level.getBlockState(p);
                    if (k == 4) {
                        if (!s.is(Blocks.STONE)) helper.fail("leaf overwrote a block");
                        continue;
                    }
                    if (!s.is(block) || s.getValue(RoundDoorBlock.PART) != RoundDoorBlock.LEAF_START + k) {
                        helper.fail(facing + "/" + hinge + ": leaf " + k + " missing");
                    }
                    var shape = s.getCollisionShape(level, p);
                    if (shape.isEmpty()) helper.fail(facing + "/" + hinge + ": leaf " + k + " has no collision");
                    if (!panel.contains(shape.bounds().move(p).getCenter())) helper.fail(facing + "/" + hinge + ": leaf " + k + " is off the panel");
                    if (!RoundDoorBlock.masterPos(p, s).equals(master)) helper.fail("leaf " + k + " points to the wrong master");
                }
                // Clicking a leaf closes the door and clears the leaves.
                BlockPos leaf = RoundDoorBlock.leafPos(master, facing, hinge, 1);
                level.getBlockState(leaf).use(level, player, InteractionHand.MAIN_HAND, hit(leaf));
                checkAll(helper, master, facing, false);
                for (int k = 0; k < 9; k++) {
                    if (k != 4 && !level.getBlockState(RoundDoorBlock.leafPos(master, facing, hinge, k)).isAir()) {
                        helper.fail("leaf " + k + " left behind after closing");
                    }
                }
                // Breaking the open door clears the leaves too, with one drop.
                level.getBlockState(master).use(level, player, InteractionHand.MAIN_HAND, hit(master));
                level.destroyBlock(RoundDoorBlock.partPos(master, facing, 6), true);
                for (int k = 0; k < 9; k++) {
                    if (k != 4 && !level.getBlockState(RoundDoorBlock.leafPos(master, facing, hinge, k)).isAir()) {
                        helper.fail("leaf " + k + " left behind after breaking");
                    }
                }
                AABB area = new AABB(master).inflate(5);
                int drops = level.getEntitiesOfClass(ItemEntity.class, area).stream().mapToInt(e -> e.getItem().getCount()).sum();
                if (drops != 1) helper.fail(facing + "/" + hinge + ": dropped " + drops + " items");
                level.getEntitiesOfClass(ItemEntity.class, area).forEach(e -> e.discard());
                level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void redstoneOpensIron(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Direction facing = Direction.EAST;
        BlockPos master = place(helper, DoorWood.IRON, facing);
        Player player = helper.makeMockPlayer();
        level.getBlockState(master).use(level, player, InteractionHand.MAIN_HAND, hit(master));
        checkAll(helper, master, facing, false);

        BlockPos torch = RoundDoorBlock.partPos(master, facing, 7).above();
        level.setBlock(torch, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        checkAll(helper, master, facing, true);
        level.setBlock(torch, Blocks.AIR.defaultBlockState(), 3);
        checkAll(helper, master, facing, false);
        helper.succeed();
    }

    /** The clickable open panel lies against the hinge column, just behind the door. */
    @GameTest(template = TEMPLATE)
    public static void openPanelBoxBehindHinge(GameTestHelper helper) {
        BlockPos master = new BlockPos(100, 64, 100);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (DoorHingeSide hinge : DoorHingeSide.values()) {
                AABB panel = RoundDoorGeometry.openPanelBox(master, facing, hinge);
                int hingePart = hinge == DoorHingeSide.LEFT ? 5 : 3;
                BlockPos hingeBlock = RoundDoorBlock.partPos(master, facing, hingePart);
                for (int part = 0; part < 9; part++) {
                    if (panel.deflate(1e-4).intersects(new AABB(RoundDoorBlock.partPos(master, facing, part)))) {
                        helper.fail(facing + "/" + hinge + ": open panel overlaps part " + part);
                    }
                }
                for (int d = 1; d <= 3; d++) {
                    if (!panel.intersects(new AABB(hingeBlock.relative(facing, d)))) {
                        helper.fail(facing + "/" + hinge + ": open panel misses the block " + d + " behind the hinge");
                    }
                }
                if (panel.intersects(new AABB(RoundDoorBlock.partPos(master, facing, 4).relative(facing, 1)))) {
                    helper.fail(facing + "/" + hinge + ": open panel is not on the hinge side");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void shapesCoverTheFrame(GameTestHelper helper) {
        RoundDoorBlock block = ModBlocks.ROUND_DOORS.get(DoorWood.SPRUCE).get();
        for (int part = 0; part < 9; part++) {
            BlockState s = block.defaultBlockState().setValue(RoundDoorBlock.PART, part).setValue(RoundDoorBlock.OPEN, true);
            boolean empty = s.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).isEmpty();
            // Open: every part but the centre still has a piece of frame.
            if (empty != (part == 4)) helper.fail("open part " + part + (empty ? " has no" : " has a") + " shape");
        }
        helper.succeed();
    }
}
