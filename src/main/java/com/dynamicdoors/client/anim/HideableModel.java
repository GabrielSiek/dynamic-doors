package com.dynamicdoors.client.anim;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Wraps the model of every door, trapdoor and fence gate. While the block is swinging the chunk mesh
 * gets no quads for it, and {@link DoorAnimator} draws it instead. Chunk building is also where the
 * animator first learns about each block.
 */
public class HideableModel extends BakedModelWrapper<BakedModel> {
    static final ModelProperty<Boolean> HIDDEN = new ModelProperty<>();

    public HideableModel(BakedModel original) {
        super(original);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        data = super.getModelData(level, pos, state, data);
        return DoorAnimator.onChunkCompile(pos.immutable(), state) ? data.derive().with(HIDDEN, true).build() : data;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        if (Boolean.TRUE.equals(data.get(HIDDEN))) return List.of();
        return super.getQuads(state, side, rand, data, renderType);
    }
}
