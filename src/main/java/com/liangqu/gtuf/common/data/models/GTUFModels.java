package com.liangqu.gtuf.common.data.models;

import com.gregtechceu.gtceu.api.registry.registrate.MachineBuilder;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;
import com.liangqu.gtuf.client.renderer.machine.GTUFTieredPartRender;

import net.minecraft.resources.ResourceLocation;

/**
 * GTUF 机器模型工厂（公开 API 之一），供 Java / KubeJS 注册多方块机时选择控制器模型。
 */
public final class GTUFModels {

    private GTUFModels() {}

    /**
     * 可工作外壳模型 + 通用等级部件/控制器渲染器。
     *
     * <p>与 {@link GTMachineModels#createWorkableCasingMachineModel} 相同的控制器外壳模型，
     * 额外附加 {@link GTUFTieredPartRender}：结构成形后控制器自身与仓/总线都按
     * {@link com.liangqu.gtuf.api.machine.ITieredCasingMachine#getCasingState()} 渲染成
     * 结构实际使用的外壳方块，材质随外壳等级匹配（蒸汽机是青铜/脱氧钢两级，电力机可自行映射）。</p>
     *
     * <p>适用于任何实现 {@link com.liangqu.gtuf.api.machine.ITieredCasingMachine} 的
     * 多方块控制器（蒸汽/电力）；未实现该接口的控制器行为与
     * {@code createWorkableCasingMachineModel} 相同（渲染器输出空）。</p>
     */
    public static MachineBuilder.ModelInitializer createTieredMachineModel(
            ResourceLocation baseCasingTexture, ResourceLocation overlayDir) {
        return GTMachineModels.createWorkableCasingMachineModel(baseCasingTexture, overlayDir)
                .andThen(builder -> builder.addDynamicRenderer(() -> new GTUFTieredPartRender()));
    }

    /**
     * 蒸汽机版 {@link #createTieredMachineModel} 的兼容别名（历史名称）。
     */
    public static MachineBuilder.ModelInitializer createTieredSteamMachineModel(
            ResourceLocation baseCasingTexture, ResourceLocation overlayDir) {
        return createTieredMachineModel(baseCasingTexture, overlayDir);
    }
}
