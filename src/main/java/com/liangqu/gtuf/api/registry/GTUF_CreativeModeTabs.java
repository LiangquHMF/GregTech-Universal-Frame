package com.liangqu.gtuf.api.registry;

import com.gregtechceu.gtceu.common.data.GTCreativeModeTabs;
import com.liangqu.gtuf.GTUF_Core;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.world.item.CreativeModeTab;

import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;

public class GTUF_CreativeModeTabs {

    public static final RegistryEntry<CreativeModeTab> GTUF_TEST = GTUF_Registries.REGISTRATE
            .defaultCreativeTab("item", builder -> builder
                    .displayItems(
                            new GTCreativeModeTabs.RegistrateDisplayItemsGenerator("item", GTUF_Registries.REGISTRATE))
                    .title(REGISTRATE.addLang("itemGroup", GTUF_Core.id("item"), GTUF_Core.MOD_NAME + " | Item"))
                    //.icon(GTFE_Bees.ULV_ELECTRIC_MOTOR::asStack)
                    .build())
            .register();


    public static void init() {}
}
