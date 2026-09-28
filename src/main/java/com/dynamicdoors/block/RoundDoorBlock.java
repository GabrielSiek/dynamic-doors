package com.dynamicdoors.block;

import com.dynamicdoors.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A 3x3 round door. Every one of the nine blocks is this block with its own {@link #PART}
 * (row * 3 + column, columns counted along the placer's left); the bottom middle part is the master
 * and holds the block entity that renders the whole door.
 */
public class RoundDoorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    /** 0-8: the 3x3 door; 9-17: the open panel's leaf blocks (see {@link #leafPos}). */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 17);
    public static final int MASTER = 1;
    public static final int LEAF_START = 9;

    private final DoorWood wood;

    public RoundDoorBlock(DoorWood wood, Properties properties) {
        super(properties);
        this.wood = wood;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false)
                .setValue(POWERED, false).setValue(HINGE, DoorHingeSide.LEFT).setValue(PART, MASTER));
    }

    public DoorWood wood() {
        return wood;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, POWERED, HINGE, PART);
    }

    // ---- multiblock layout ----

    public static BlockPos partPos(BlockPos master, Direction facing, int part) {
        return master.relative(facing.getCounterClockWise(), part % 3 - 1).above(part / 3);
    }

    public static boolean isLeaf(BlockState state) {
        return state.getValue(PART) >= LEAF_START;
    }

    /**
     * The open panel sticks out behind the hinge column, 3 blocks high and 3 deep. While the door is
     * open those spots (when free) hold invisible leaf parts with the panel's collision, so it is a
     * solid object that can be clicked and broken. Leaf {@code k}: row {@code k / 3}, {@code k % 3 + 1}
     * blocks behind the door.
     */
    public static BlockPos leafPos(BlockPos master, Direction facing, DoorHingeSide hinge, int k) {
        int hingeCol = hinge == DoorHingeSide.RIGHT ? 0 : 2;
        return partPos(master, facing, (k / 3) * 3 + hingeCol).relative(facing, k % 3 + 1);
    }

    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        Direction facing = state.getValue(FACING);
        if (part >= LEAF_START) {
            int k = part - LEAF_START;
            int hingeCol = state.getValue(HINGE) == DoorHingeSide.RIGHT ? 0 : 2;
            return pos.relative(facing, -(k % 3 + 1)).below(k / 3).relative(facing.getCounterClockWise(), 1 - hingeCol);
        }
        return pos.relative(facing.getCounterClockWise(), 1 - part % 3).below(part / 3);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction facing = ctx.getHorizontalDirection();
        if (pos.getY() + 2 >= level.getMaxBuildHeight()) return null;
        boolean powered = false;
        for (int part = 0; part < 9; part++) {
            BlockPos p = partPos(pos, facing, part);
            if (!level.getBlockState(p).canBeReplaced(ctx)) return null;
            powered |= level.hasNeighborSignal(p);
        }
        Direction left = facing.getCounterClockWise();
        Vec3 hit = ctx.getClickLocation();
        double towardLeft = (hit.x - pos.getX() - 0.5) * left.getStepX() + (hit.z - pos.getZ() - 0.5) * left.getStepZ();
        return defaultBlockState().setValue(FACING, facing)
                .setValue(HINGE, towardLeft > 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT)
                .setValue(OPEN, powered).setValue(POWERED, powered).setValue(PART, MASTER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        for (int part = 0; part < 9; part++) {
            if (part != MASTER) level.setBlock(partPos(pos, facing, part), state.setValue(PART, part), 3);
        }
        if (state.getValue(OPEN)) placeLeaves(level, pos, state);
    }

    private void placeLeaves(Level level, BlockPos master, BlockState state) {
        Direction facing = state.getValue(FACING);
        DoorHingeSide hinge = state.getValue(HINGE);
        for (int k = 0; k < 9; k++) {
            BlockPos p = leafPos(master, facing, hinge, k);
            // Never overwrite anything: where the panel swings into a wall or water it just has no collision.
            if (level.getBlockState(p).isAir()) {
                level.setBlock(p, state.setValue(PART, LEAF_START + k).setValue(OPEN, true), Block.UPDATE_ALL);
            }
        }
    }

    private void removeLeaves(Level level, BlockPos master, BlockState state) {
        Direction facing = state.getValue(FACING);
        DoorHingeSide hinge = state.getValue(HINGE);
        for (int k = 0; k < 9; k++) {
            BlockPos p = leafPos(master, facing, hinge, k);
            BlockState s = level.getBlockState(p);
            if (s.is(this) && s.getValue(PART) == LEAF_START + k && masterPos(p, s).equals(master)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** The master takes its leaves with it, however it is removed. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!newState.is(this) && state.getValue(PART) == MASTER) removeLeaves(level, pos, state);
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /** A part whose neighbour inside the door is gone breaks too; open/powered follow the neighbours. */
    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (isLeaf(state)) return state;
        Direction facing = state.getValue(FACING);
        Direction left = facing.getCounterClockWise();
        int part = state.getValue(PART);
        int col = part % 3;
        int row = part / 3;
        if (dir == left) col++;
        else if (dir == left.getOpposite()) col--;
        else if (dir == Direction.UP) row++;
        else if (dir == Direction.DOWN) row--;
        else return state;
        if (col < 0 || col > 2 || row < 0 || row > 2) return state;
        if (!neighbor.is(this) || neighbor.getValue(PART) != row * 3 + col || neighbor.getValue(FACING) != facing
                || neighbor.getValue(HINGE) != state.getValue(HINGE)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state.setValue(OPEN, neighbor.getValue(OPEN)).setValue(POWERED, neighbor.getValue(POWERED));
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Only the master drops the item; in creative remove it first so the cascade drops nothing.
        if (!level.isClientSide) {
            BlockPos master = masterPos(pos, state);
            BlockState masterState = level.getBlockState(master);
            if (!master.equals(pos) && masterState.is(this)) {
                if (player.isCreative()) {
                    level.setBlock(master, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    level.levelEvent(player, 2001, master, Block.getId(masterState));
                } else if (isLeaf(state)) {
                    // Leaves are not attached to the door's blocks, so break the door from here.
                    level.destroyBlock(master, true, player);
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    // ---- opening ----

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        BlockPos master = masterPos(pos, state);
        BlockState masterState = level.getBlockState(master);
        if (!masterState.is(this) || !toggleByHand(player, level, master, masterState)) return InteractionResult.PASS;
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Opens or closes the whole door as a player click would; false for doors that need redstone. */
    public boolean toggleByHand(Player player, Level level, BlockPos master, BlockState state) {
        if (!wood.type.canOpenByHand()) return false;
        setOpen(player, level, master, state, !state.getValue(OPEN), state.getValue(POWERED));
        return true;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                boolean isMoving) {
        if (level.isClientSide || isLeaf(state)) return;
        BlockPos master = masterPos(pos, state);
        BlockState masterState = level.getBlockState(master);
        if (!masterState.is(this)) return;
        Direction facing = state.getValue(FACING);
        boolean powered = false;
        for (int part = 0; part < 9 && !powered; part++) powered = level.hasNeighborSignal(partPos(master, facing, part));
        if (powered != masterState.getValue(POWERED)) setOpen(null, level, master, masterState, powered, powered);
    }

    private void setOpen(@Nullable Player player, Level level, BlockPos master, BlockState state, boolean open,
                         boolean powered) {
        boolean changed = open != state.getValue(OPEN);
        Direction facing = state.getValue(FACING);
        for (int part = 0; part < 9; part++) {
            BlockPos p = partPos(master, facing, part);
            BlockState s = level.getBlockState(p);
            if (s.is(this)) level.setBlock(p, s.setValue(OPEN, open).setValue(POWERED, powered), Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
        }
        if (changed) {
            if (open) placeLeaves(level, master, state);
            else removeLeaves(level, master, state);
            BlockPos center = master.above();
            level.playSound(player, center, open ? wood.type.doorOpen() : wood.type.doorClose(), SoundSource.BLOCKS,
                    1.0F, level.getRandom().nextFloat() * 0.1F + 0.8F);
            level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, center);
        }
    }

    // ---- shape & rendering ----

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape(state, false);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape(state, true);
    }

    private static VoxelShape shape(BlockState state, boolean collision) {
        int part = state.getValue(PART);
        if (part >= LEAF_START) return RoundDoorShapes.leaf(state.getValue(FACING), state.getValue(HINGE), part - LEAF_START);
        return RoundDoorShapes.get(state.getValue(FACING), part, state.getValue(OPEN), collision);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return type != PathComputationType.WATER && state.getValue(OPEN) && !isLeaf(state);
    }

    /** The master's block entity draws the whole door; no part goes into the chunk mesh. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state : state.rotate(mirror.getRotation(state.getValue(FACING)))
                .cycle(HINGE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == MASTER ? new RoundDoorBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide || type != ModBlocks.ROUND_DOOR.get()) return null;
        return (l, p, s, be) -> ((RoundDoorBlockEntity) be).clientTick(s);
    }
}
