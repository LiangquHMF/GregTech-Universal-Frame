package com.liangqu.gtuf.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;
import com.gregtechceu.gtceu.common.machine.multiblock.part.SteamHatchPartMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.liangqu.gtuf.api.registry.GTUF_CreativeModeTabs;
import com.liangqu.gtuf.common.machine.multiblock.part.EnhancedFluidHatchPartMachine;
import com.liangqu.gtuf.common.machine.multiblock.part.EnhancedParallelHatchPartMachine;
import com.liangqu.gtuf.common.machine.multiblock.part.IndustrialSteamHatchPartMachine;
import net.minecraft.network.chat.Component;

import static com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.*;
import static com.liangqu.gtuf.api.registry.GTUF_Registries.REGISTRATE;

/**
 * GTUF 公开机器注册入口（框架级），仿原生 {@code GTMachines}。
 *
 * <p>本类只包含<b>框架级</b>可复用机器/部件注册：工业蒸汽仓、增强并行仓、增强流体仓。
 * 这些注册是公开 API 的一部分，打包发布时保留。测试专用机器（多方块测试机等）
 * 在 {@code GTUF_Machine_Test} 中，不随公开发布打包。
 *
 * <p>框架代码（部件类、机器类、本注册类）禁止引用测试类，避免打包剔除测试内容后断链。
 * 依赖方向：{@code GTUF_Machine_Test} → {@code GTUF_Machines}（测试可依赖框架，反之不可）。</p>
 */
public class GTUF_Machines {

    static {
        REGISTRATE.creativeModeTab(() -> GTUF_CreativeModeTabs.GTUF_TEST);
    }

    /**
     * 增强蒸汽输入仓的容量（mB）。这里是唯一的配置点——调整该值即可改变容量，
     * 注册 lambda 与 tooltip 都引用它，保证显示与实际一致。
     */
    public static final int INDUSTRIAL_STEAM_HATCH_CAPACITY = SteamHatchPartMachine.INITIAL_TANK_CAPACITY * 16;

    /**
     * 增强蒸汽输入仓：仿原生蒸汽仓（STEAM_HATCH），仅接受蒸汽。
     * 容量为原生（64,000 mB）的 64 倍（4,096,000 mB），通过调整
     * {@link #INDUSTRIAL_STEAM_HATCH_CAPACITY} 可自定义（须为原生 64,000 的整数倍）。
     * 与工业蒸汽机搭配时触发 MV 配方与高效转换（0.25 mB/EU）。
     */
    public static final MachineDefinition INDUSTRIAL_STEAM_HATCH = REGISTRATE
            .machine("industrial_steam_input_hatch",
                    holder -> new IndustrialSteamHatchPartMachine(holder, INDUSTRIAL_STEAM_HATCH_CAPACITY))
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.STEAM)
            .overlaySteamHullModel("steam_hatch")
            .tooltips(Component.translatable("gtceu.universal.tooltip.fluid_storage_capacity",
                            INDUSTRIAL_STEAM_HATCH_CAPACITY),
                    Component.translatable("gtceu.machine.steam.steam_hatch.tooltip"))
            .allowCoverOnFront(true)
            .register();

    /**
     * 增强型并行控制仓（LV~UV 八级）：并行上限 = 2^(tier-1 + max(0, tier-IV))，
     * 即 LV=1, MV=2, HV=4, EV=8, IV=16, LuV=64, ZPM=256, UV=1024。
     * 前期每级 ×2 保证低等级可用，后期（LuV 起）每级 ×4 追平原生增幅（原生 UV=256，本机为 4 倍）。
     * 机器端通过 {@code GTRecipeModifiers.PARALLEL_HATCH} 读取当前并行数放大配方。
     */
    public static final MachineDefinition[] ENHANCED_PARALLEL_HATCH = GTMachineUtils.registerTieredMachines(
            REGISTRATE, "enhanced_parallel_hatch", EnhancedParallelHatchPartMachine::new,
            (tier, builder) -> builder.rotationState(RotationState.ALL)
                    .abilities(PartAbility.PARALLEL_HATCH)
                    .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
                    .model(GTMachineModels.createWorkableTieredHullMachineModel(
                            GTCEu.id("block/machines/parallel_hatch_mk" + ((tier + 1) / 2))))
                    .tooltips(Component.translatable("gtuf.machine.enhanced_parallel_hatch.tooltip",
                            EnhancedParallelHatchPartMachine.getParallelLimit(tier)))
                    .register(),
            GTValues.LV, GTValues.MV, GTValues.HV, GTValues.EV, GTValues.IV, GTValues.LuV, GTValues.ZPM,
            GTValues.UV);

    /**
     * 增强型流体输入仓（ULV~UHV，highTier 开启则含 UEV+）：容量 = 8000 * 4^tier，每级 ×4 无封顶。
     * tooltip 显示 float 理论容量；实际 tank 容量 int 钳制。仿原生 GTMachines.FLUID_IMPORT_HATCH。
     */
    public static final MachineDefinition[] ENHANCED_FLUID_IMPORT_HATCH = registerEnhancedFluidHatches(
            "enhanced_fluid_input_hatch", "Enhanced Input Hatch", IO.IN, IMPORT_FLUIDS);

    /**
     * 增强型流体输出仓（ULV~UHV，highTier 开启则含 UEV+）：结构同输入仓，方向为输出。
     */
    public static final MachineDefinition[] ENHANCED_FLUID_EXPORT_HATCH = registerEnhancedFluidHatches(
            "enhanced_fluid_output_hatch", "Enhanced Output Hatch", IO.OUT, EXPORT_FLUIDS);

    /** 增强型流体仓统一注册工厂：仿原生 registerFluidHatches 的 1x 分支（slots=1）。 */
    private static MachineDefinition[] registerEnhancedFluidHatches(String name, String displayName, IO io,
                                                                    PartAbility... abilities) {
        String ioOverlay = io == IO.OUT ? "overlay_pipe_out_emissive" : "overlay_pipe_in_emissive";
        return GTMachineUtils.registerTieredMachines(REGISTRATE, name,
                (holder, tier) -> new EnhancedFluidHatchPartMachine(holder, tier, io,
                        EnhancedFluidHatchPartMachine.INITIAL_TANK_CAPACITY, 1),
                (tier, builder) -> builder
                        .langValue(GTValues.VNF[tier] + " " + displayName)
                        .rotationState(RotationState.ALL)
                        .colorOverlayTieredHullModel(ioOverlay, null, GTMachineModels.OVERLAY_FLUID_HATCH_TEX)
                        .abilities(abilities)
                        .tooltips(Component.translatable("gtuf.machine.enhanced_fluid_hatch.tooltip"),
                                Component.translatable("gtceu.universal.tooltip.fluid_storage_capacity",
                                        FormattingUtil.formatNumbers(
                                                EnhancedFluidHatchPartMachine.getEnhancedCapacity(tier))))
                        .allowCoverOnFront(true)
                        .register(),
                GTMachineUtils.ALL_TIERS);
    }

    public static void init() {}
}
