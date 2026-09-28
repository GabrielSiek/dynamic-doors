package com.dynamicdoors.client;

import com.dynamicdoors.block.DoorWood;
import com.dynamicdoors.block.RoundDoorBlock;
import com.dynamicdoors.block.RoundDoorBlockEntity;
import com.dynamicdoors.block.RoundDoorGeometry;
import com.dynamicdoors.client.anim.Easing;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.function.Function;

/** Draws the whole 3x3 door from the master part, with the panel swung by the open amount. */
public class RoundDoorRenderer implements BlockEntityRenderer<RoundDoorBlockEntity> {
    private static final ResourceLocation KNOB_TEXTURE = new ResourceLocation("dynamicdoors", "block/round_door_knob");

    public RoundDoorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RoundDoorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof RoundDoorBlock door)) return;
        DoorWood wood = door.wood();
        Function<ResourceLocation, TextureAtlasSprite> atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite frame = atlas.apply(wood.frameTexture);
        TextureAtlasSprite panel = atlas.apply(wood.panelTexture);
        TextureAtlasSprite knob = atlas.apply(KNOB_TEXTURE);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));

        Direction facing = state.getValue(RoundDoorBlock.FACING);
        pose.pushPose();
        // Door-local space: x along the placer's left, z along the facing, in pixels from the corner.
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-1.5, 0, -0.5);
        pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
        emit(RoundDoorMesh.FRAME, pose, vc, frame, panel, knob, light, overlay);

        // The hinge is on the panel's back edge; the far side swings away from the frame (+z).
        boolean rightHinge = state.getValue(RoundDoorBlock.HINGE) == DoorHingeSide.RIGHT;
        float pivotX = rightHinge ? 0 : RoundDoorGeometry.SIZE;
        float angle = 90 * Easing.swing(be.getOpenAmount(partialTick)) * (rightHinge ? -1 : 1);
        pose.translate(pivotX, 0, RoundDoorGeometry.DEPTH);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(-pivotX, 0, -RoundDoorGeometry.DEPTH);
        emit(rightHinge ? RoundDoorMesh.PANEL_RIGHT_HINGE : RoundDoorMesh.PANEL_LEFT_HINGE, pose, vc, frame, panel, knob, light, overlay);
        pose.popPose();
    }

    private static void emit(List<RoundDoorMesh.Quad> quads, PoseStack pose, VertexConsumer vc, TextureAtlasSprite frame,
                             TextureAtlasSprite panel, TextureAtlasSprite knob, int light, int overlay) {
        Matrix4f mat = pose.last().pose();
        Matrix3f normalMat = pose.last().normal();
        for (RoundDoorMesh.Quad q : quads) {
            TextureAtlasSprite sprite = switch (q.material()) {
                case FRAME, PANEL_EDGE -> frame;
                case PANEL_FACE -> panel;
                case KNOB -> knob;
            };
            Direction n = q.normal();
            float[] p = q.pos();
            float[] uv = q.uv();
            for (int i = 0; i < 4; i++) {
                vc.vertex(mat, p[i * 3], p[i * 3 + 1], p[i * 3 + 2])
                        .color(255, 255, 255, 255)
                        .uv(sprite.getU(uv[i * 2]), sprite.getV(uv[i * 2 + 1]))
                        .overlayCoords(overlay)
                        .uv2(light)
                        .normal(normalMat, n.getStepX(), n.getStepY(), n.getStepZ())
                        .endVertex();
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(RoundDoorBlockEntity be) {
        return true;
    }
}
