package com.dynamicdoors;

import com.dynamicdoors.client.ClientSetup;
import com.dynamicdoors.config.ClientConfig;
import com.dynamicdoors.network.ModNetwork;
import com.dynamicdoors.registry.ModBlocks;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(DynamicDoors.MODID)
public class DynamicDoors {
    public static final String MODID = "dynamicdoors";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DynamicDoors() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.ITEMS.register(modBus);
        ModBlocks.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ModBlocks::addToTabs);

        ModNetwork.register();

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));
    }
}
