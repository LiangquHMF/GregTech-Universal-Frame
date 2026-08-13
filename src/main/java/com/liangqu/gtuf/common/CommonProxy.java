package com.liangqu.gtuf.common;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialRegistryEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.PostMaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.registry.MaterialRegistry;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.liangqu.gtuf.GTUF_Core;
import com.liangqu.gtuf.api.data.material.GTUF_MaterialPropertyKeys;
import com.liangqu.gtuf.api.data.material.PressurePipeProperties;
import com.liangqu.gtuf.api.registry.GTUF_CreativeModeTabs;
import com.liangqu.gtuf.common.data.GTUF_Machines;
import com.liangqu.gtuf.common.data.GTUF_PressureBlocks;
import com.liangqu.gtuf.common.data.GTUF_RecipeConditions;
import com.liangqu.gtuf.config.GTUF_Config;

import java.util.Map;

import static com.liangqu.gtuf.api.registry.GTUF_Registries.REGISTRATE;

public class CommonProxy {

    public CommonProxy() {
        CommonProxy.init();
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        REGISTRATE.registerEventListeners(eventBus);
        eventBus.addGenericListener(GTRecipeType.class, this::registerRecipeTypes);
        eventBus.addGenericListener(MachineDefinition.class, this::registerMachines);
        eventBus.addGenericListener(RecipeConditionType.class, this::registerRecipeConditions);
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
        GTUF_Machines.init();
        // 测试机器由 testmod 源码集的 GTUF_TestMachines（@Mod.EventBusSubscriber）在 dev 运行时注册。
        // 公开发布 jar 不含 testmod，故此处无需（也不应）引用测试类。
    }

    private void registerRecipeTypes(GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {}

    public void registerRecipeConditions(GTCEuAPI.RegisterEvent<java.lang.String, RecipeConditionType<?>> event) {
        GTUF_RecipeConditions.init(event);
    }

    public void registerMaterials(MaterialEvent event) {}

    /**
     * 材质后置事件：为 config {@code [pressure].pressurePipeTolerances} 列出的 GTM 材质挂
     * {@code PRESSURE_PIPE} 属性，并生成压力管道方块（必须在材质冻结前、方块 RegisterEvent 前）。
     */
    private void modifyMaterials(PostMaterialEvent event) {
        MaterialRegistry registry = GTCEuAPI.materialManager.getRegistry(GTCEu.MOD_ID);
        for (Map.Entry<String, PressurePipeProperties> entry : GTUF_Config.getPressurePipeTolerances().entrySet()) {
            Material material = registry.get(entry.getKey());
            if (material != null) {
                material.setProperty(GTUF_MaterialPropertyKeys.PRESSURE_PIPE, entry.getValue());
            } else {
                GTCEu.LOGGER.warn("GTUF pressure pipe material '{}' not found in GTCEu registry, skipped.",
                        entry.getKey());
            }
        }
        GTUF_PressureBlocks.generate();
    }

    public static MaterialRegistry MATERIAL_REGISTRY;

    private void addMaterialRegistries(MaterialRegistryEvent event) {
        GTCEuAPI.materialManager.createRegistry(GTUF_Core.MOD_ID);
    }

    public void registerMaterialRegistry(MaterialRegistryEvent event) {
        MATERIAL_REGISTRY = GTCEuAPI.materialManager.createRegistry(GTUF_Core.MOD_ID);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {}
}
