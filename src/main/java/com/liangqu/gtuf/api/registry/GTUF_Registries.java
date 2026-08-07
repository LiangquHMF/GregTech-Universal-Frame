package com.liangqu.gtuf.api.registry;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.liangqu.gtuf.GTUF_Core;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.world.item.CreativeModeTab;

public class GTUF_Registries {

    public static GTRegistrate REGISTRATE = GTRegistrate.create(GTUF_Core.MOD_ID);

    public static RegistryEntry<CreativeModeTab> GTUF_CREATIVE_TAB = null;

}
