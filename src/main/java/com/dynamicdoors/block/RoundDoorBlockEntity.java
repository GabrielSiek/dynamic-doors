package com.dynamicdoors.block;

import com.dynamicdoors.config.ClientConfig;
import com.dynamicdoors.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Lives on the master part; keeps the client-side swing progress of the panel. */
public class RoundDoorBlockEntity extends BlockEntity {
    private float openAmount;
    private float prevOpenAmount;

    public RoundDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ROUND_DOOR.get(), pos, state);
        openAmount = prevOpenAmount = state.getValue(RoundDoorBlock.OPEN) ? 1 : 0;
    }

    void clientTick(BlockState state) {
        prevOpenAmount = openAmount;
        float target = state.getValue(RoundDoorBlock.OPEN) ? 1 : 0;
        float step = 1.0F / ClientConfig.get(ClientConfig.ROUND_DOOR_DURATION_TICKS);
        openAmount = target > openAmount ? Math.min(target, openAmount + step) : Math.max(target, openAmount - step);
    }

    /** 0 = closed, 1 = open, linear in time. */
    public float getOpenAmount(float partialTick) {
        return Mth.lerp(partialTick, prevOpenAmount, openAmount);
    }

    /** Covers the frame and the panel swung open two blocks past the footprint. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(4, 0, 4).expandTowards(0, 3, 0);
    }
}
