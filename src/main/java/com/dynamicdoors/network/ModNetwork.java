package com.dynamicdoors.network;

import com.dynamicdoors.DynamicDoors;
import com.dynamicdoors.block.RoundDoorBlock;
import com.dynamicdoors.block.RoundDoorGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class ModNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DynamicDoors.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(CloseOpenRoundDoor.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CloseOpenRoundDoor::encode)
                .decoder(CloseOpenRoundDoor::decode)
                .consumerMainThread(CloseOpenRoundDoor::handle)
                .add();
    }

    /** The player clicked the swung-open panel of the round door whose master is at {@code master}. */
    public record CloseOpenRoundDoor(BlockPos master) {
        void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(master);
        }

        static CloseOpenRoundDoor decode(FriendlyByteBuf buf) {
            return new CloseOpenRoundDoor(buf.readBlockPos());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().setPacketHandled(true);
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.isSpectator()) return;
            Level level = player.level();
            if (!level.isLoaded(master)) return;
            BlockState state = level.getBlockState(master);
            if (!(state.getBlock() instanceof RoundDoorBlock door) || state.getValue(RoundDoorBlock.PART) != RoundDoorBlock.MASTER
                    || !state.getValue(RoundDoorBlock.OPEN)) return;
            AABB panel = RoundDoorGeometry.openPanelBox(master, state.getValue(RoundDoorBlock.FACING),
                    state.getValue(RoundDoorBlock.HINGE));
            Vec3 eye = player.getEyePosition();
            double dx = Math.max(Math.max(panel.minX - eye.x, 0), eye.x - panel.maxX);
            double dy = Math.max(Math.max(panel.minY - eye.y, 0), eye.y - panel.maxY);
            double dz = Math.max(Math.max(panel.minZ - eye.z, 0), eye.z - panel.maxZ);
            double reach = player.getBlockReach() + 1;
            if (dx * dx + dy * dy + dz * dz > reach * reach) return;
            door.toggleByHand(player, level, master, state);
        }
    }
}
