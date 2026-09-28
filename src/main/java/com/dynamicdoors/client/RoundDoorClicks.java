package com.dynamicdoors.client;

import com.dynamicdoors.block.RoundDoorBlock;
import com.dynamicdoors.block.RoundDoorGeometry;
import com.dynamicdoors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;

import java.util.Optional;

/**
 * An open round door's panel sticks out past its blocks, so vanilla picking can't hit it. On
 * right-click, ray-test the open panels nearby; if one is closer than whatever vanilla picked, ask
 * the server to close that door.
 */
public final class RoundDoorClicks {
    private RoundDoorClicks() {
    }

    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || player.isSpectator()) return;

        double reach = player.getBlockReach();
        Vec3 eye = player.getEyePosition(1);
        Vec3 end = eye.add(player.getViewVector(1).scale(reach));
        double best = mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS
                ? eye.distanceTo(mc.hitResult.getLocation()) : reach;
        BlockPos hitMaster = null;

        // The panel reaches up to 4 blocks from its master.
        int r = (int) Math.ceil(reach) + 4;
        BlockPos center = BlockPos.containing(eye);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof RoundDoorBlock) || state.getValue(RoundDoorBlock.PART) != RoundDoorBlock.MASTER
                    || !state.getValue(RoundDoorBlock.OPEN)) continue;
            Optional<Vec3> hit = RoundDoorGeometry.openPanelBox(pos, state.getValue(RoundDoorBlock.FACING),
                    state.getValue(RoundDoorBlock.HINGE)).clip(eye, end);
            if (hit.isPresent() && eye.distanceTo(hit.get()) < best) {
                best = eye.distanceTo(hit.get());
                hitMaster = pos.immutable();
            }
        }
        if (hitMaster == null) return;
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.CloseOpenRoundDoor(hitMaster));
        event.setSwingHand(true);
        event.setCanceled(true);
    }
}
