package com.dynamicdoors.client;

import com.dynamicdoors.client.anim.DoorAnimator;
import com.dynamicdoors.registry.ModBlocks;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ClientSetup {
    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(ModBlocks.ROUND_DOOR.get(), RoundDoorRenderer::new));
        modBus.addListener(DoorAnimator::onModifyBakingResult);

        MinecraftForge.EVENT_BUS.addListener(DoorAnimator::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(DoorAnimator::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(DoorAnimator::onChunkUnload);
        MinecraftForge.EVENT_BUS.addListener(DoorAnimator::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(RoundDoorClicks::onInteract);
    }
}
