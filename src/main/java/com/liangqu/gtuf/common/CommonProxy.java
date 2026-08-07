package com.liangqu.gtuf.common;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialRegistryEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.PostMaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.registry.MaterialRegistry;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.liangqu.gtuf.GTUF_Core;
import com.liangqu.gtuf.api.registry.GTUF_CreativeModeTabs;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import static com.liangqu.gtuf.api.registry.GTUF_Registries.REGISTRATE;

public class CommonProxy {

    public CommonProxy(){
        CommonProxy.init();
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        REGISTRATE.registerEventListeners(eventBus);
        eventBus.addGenericListener(GTRecipeType.class, this::registerRecipeTypes);
        eventBus.addGenericListener(MachineDefinition.class, this::registerMachines);
        eventBus.addListener(this::commonSetup);
        eventBus.addListener(this::addMaterialRegistries);
        eventBus.addListener(this::registerMaterials);
        eventBus.addListener(this::modifyMaterials);
    }

    private static void init() {
        GTUF_CreativeModeTabs.init();
        REGISTRATE.registerRegistrate();

    }

    public void registerMachines(GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {

    }

    private void registerRecipeTypes(GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {

    }
    public void registerMaterials(MaterialEvent event) {
    }

    private void modifyMaterials(PostMaterialEvent event) {}

    public static MaterialRegistry MATERIAL_REGISTRY;

    private void addMaterialRegistries(MaterialRegistryEvent event) {
        GTCEuAPI.materialManager.createRegistry(GTUF_Core.MOD_ID);
    }
    public void registerMaterialRegistry(MaterialRegistryEvent event) {
        MATERIAL_REGISTRY = GTCEuAPI.materialManager.createRegistry(GTUF_Core.MOD_ID);
    }
    private void commonSetup(final FMLCommonSetupEvent event) {}

}
