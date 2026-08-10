package com.liangqu.gtuf.common.data.models;

import com.gregtechceu.gtceu.api.registry.registrate.MachineBuilder;
import com.gregtechceu.gtceu.common.block.BoilerFireboxType;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;

import net.minecraft.resources.ResourceLocation;

import com.liangqu.gtuf.client.renderer.machine.GTUFTieredBoilerPartRender;
import com.liangqu.gtuf.client.renderer.machine.GTUFTieredPartRender;

/**
 * GTUF 机器模型工厂（公开 API 之一），供 Java / KubeJS 注册多方块机时选择控制器模型。
 */
public final class GTUFModels {

    private GTUFModels() {}

    /**
     * 可工作外壳模型 + 通用等级部件/控制器渲染器。
     *
     * <p>
     * 与 {@link GTMachineModels#createWorkableCasingMachineModel} 相同的控制器外壳模型，
     * 额外附加 {@link GTUFTieredPartRender}：结构成形后控制器自身与仓/总线都按
     * {@link com.liangqu.gtuf.api.machine.ITieredCasingMachine#getCasingState()} 渲染成
     * 结构实际使用的外壳方块，材质随外壳等级匹配（蒸汽机是青铜/脱氧钢两级，电力机可自行映射）。
     * </p>
     *
     * <p>
     * 适用于任何实现 {@link com.liangqu.gtuf.api.machine.ITieredCasingMachine} 的
     * 多方块控制器（蒸汽/电力）；未实现该接口的控制器行为与
     * {@code createWorkableCasingMachineModel} 相同（渲染器输出空）。
     * </p>
     */
    public static MachineBuilder.ModelInitializer createTieredMachineModel(
                                                                           ResourceLocation baseCasingTexture,
                                                                           ResourceLocation overlayDir) {
        return GTMachineModels.createWorkableCasingMachineModel(baseCasingTexture, overlayDir)
                .andThen(builder -> builder.addDynamicRenderer(() -> new GTUFTieredPartRender()));
    }

    /**
     * 可工作分级外壳模型 + 仓室可替换纹理（供线程仓/增强并行仓等"仓室"使用）。
     *
     * <p>
     * 与 {@link GTMachineModels#createWorkableTieredHullMachineModel} 相同的可工作分级
     * 外壳模型，但额外把底座 bottom/top/side 注册为<b>可替换纹理</b>
     * （{@code addReplaceableTextures("bottom","top","side")}）。GTM 的部件外观替换链路
     * （{@code MachineModel#replacePartBaseModel} → {@code renderPartOverrides}）依赖
     * replaceableTextures 生成 blank 覆盖表，把仓室自身 base quads 换成空白贴图后，再叠加
     * 成型后的外壳 quads；缺它则 base 未清空，与外壳共面叠加（z-fight）产生材质闪烁。
     * </p>
     *
     * <p>
     * 仓室类需配合各部件类 override 的
     * {@code replacePartModelWhenFormed()}（按 {@code isFormed()} 判定）才能覆盖
     * GTM 7.3.0 的 IS_FORMED 渲染属性门控，两版本行为一致。
     * </p>
     */
    public static MachineBuilder.ModelInitializer createTieredHullMachineModel(ResourceLocation overlayDir) {
        return GTMachineModels.createWorkableTieredHullMachineModel(overlayDir)
                .andThen(builder -> builder.addReplaceableTextures("bottom", "top", "side"));
    }

    /**
     * 带燃烧室的等级外壳模型：用 {@link GTUFTieredBoilerPartRender}（而非
     * {@link GTUFTieredPartRender}）替换部件渲染，单一 {@code IControllerModelRenderer}
     * 合并"等级外壳 + 燃烧室行"两件事。
     *
     * <p>
     * 适用于底部有燃烧室行的多方块机（如合金炉）：燃烧室行上的部件（蒸汽仓）渲染成
     * 燃烧室方块、与相邻燃烧室无缝融合；控制器与其余仓室仍按外壳等级渲染成实际外壳方块。
     * </p>
     *
     * <p>
     * <b>不要在此之上再叠加任何 {@code IControllerModelRenderer}</b>（包括
     * {@link #createTieredMachineModel} 的 {@link GTUFTieredPartRender} 或 GTM
     * {@code BoilerMultiPartRender}）——GTM 的 {@code renderPartOverrides} 会把所有
     * {@code IControllerModelRenderer} 的 quads 累积进同一个列表，两个渲染器叠加会让部件
     * 重复渲染（连接缝/z-fighting），且模型 JSON 里 type 冲突。
     * </p>
     */
    public static MachineBuilder.ModelInitializer createTieredBoilerMachineModel(
                                                                                 ResourceLocation baseCasingTexture,
                                                                                 ResourceLocation overlayDir,
                                                                                 BoilerFireboxType fireboxType) {
        return GTMachineModels.createWorkableCasingMachineModel(baseCasingTexture, overlayDir)
                .andThen(builder -> builder.addDynamicRenderer(() -> new GTUFTieredBoilerPartRender(fireboxType)));
    }

    /**
     * 蒸汽机版 {@link #createTieredMachineModel} 的兼容别名（历史名称）。
     */
    public static MachineBuilder.ModelInitializer createTieredSteamMachineModel(
                                                                                ResourceLocation baseCasingTexture,
                                                                                ResourceLocation overlayDir) {
        return createTieredMachineModel(baseCasingTexture, overlayDir);
    }
}
