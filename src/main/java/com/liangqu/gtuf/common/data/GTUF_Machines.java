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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.*;
import static com.liangqu.gtuf.api.registry.GTUF_Registries.REGISTRATE;

/**
 * GTUF 公开机器注册入口（框架级），仿原生 {@code GTMachines}。
 *
 * <p>框架定位：公开发布 jar 只提供"方法和接口"，<b>不预注册任何机器实例</b>。
 * 增强流体仓、增强并行仓、工业蒸汽仓均由整合包作者通过本类的<b>公开注册工厂</b>
 * （Java/KubeJS/CrT 调用）按需注册。因此本类没有静态注册字段，只有工厂与配对表。
 *
 * <p>测试专用机器在 {@code GTUF_Machine_Test}（testmod 源码集），不随公开发布打包。
 * 框架代码（部件类、机器类、本注册类）禁止引用测试类，避免打包剔除测试内容后断链。
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
     * 增强型流体仓运行时配对表：swapIO 按 (tier, IO) 查反向定义。
     * 由 {@link #registerEnhancedFluidHatches} 填充；原生 GTM KJS 自定义注册的仓
     * 可用 {@link #trackEnhancedFluidHatch} 手动关联，否则 swapIO 返回 false。
     */
    private static final Map<Integer, EnumMap<IO, MachineDefinition>> ENHANCED_FLUID_HATCHES = new HashMap<>();

    /** 增强并行仓默认注册档位：LV~UV 八级。 */
    private static final int[] DEFAULT_PARALLEL_HATCH_TIERS = new int[] {
            GTValues.LV, GTValues.MV, GTValues.HV, GTValues.EV, GTValues.IV, GTValues.LuV, GTValues.ZPM, GTValues.UV
    };

    /**
     * 注册增强型流体仓（容量 = 8000 * 4^tier，每级 ×4 无封顶；缺省档位 ULV~UHV，highTier 开启含 UEV+）。
     * tooltip 显示 float 理论容量；实际 tank 容量 int 钳制。仿原生 GTMachines.FLUID_IMPORT_HATCH。
     *
     * <p>可通过 {@code tiers} 只注册指定档位（如 {@code new int[]{GTValues.LV, GTValues.EV}}），
     * 缺省为 {@link GTMachineUtils#ALL_TIERS}。部件能力由 {@code io} 推导（IN→IMPORT_FLUIDS，
     * OUT→EXPORT_FLUIDS）。注册后自动填充 {@link #ENHANCED_FLUID_HATCHES} 配对表（仅已注册档位），
     * 供 swapIO 反查。</p>
     *
     * @param name        注册名（registerTieredMachines 会加 {@code VN[tier].toLowerCase() + "_"} 前缀）
     * @param displayName 显示名（前接 {@code GTValues.VNF[tier]}）
     * @param io          仓方向（IO.IN=输入 / IO.OUT=输出）
     * @param tiers       要注册的档位（tier 值），缺省注册全部档位
     * @return 按 tier 索引的 MachineDefinition 数组（tier → definition，未注册档位为 null）
     */
    public static MachineDefinition[] registerEnhancedFluidHatches(String name, String displayName, IO io,
                                                                   int... tiers) {
        if (tiers.length == 0) {
            tiers = GTMachineUtils.ALL_TIERS;
        }
        PartAbility ability = io == IO.IN ? IMPORT_FLUIDS : EXPORT_FLUIDS;
        String ioOverlay = io == IO.OUT ? "overlay_pipe_out_emissive" : "overlay_pipe_in_emissive";
        MachineDefinition[] defs = GTMachineUtils.registerTieredMachines(REGISTRATE, name,
                (holder, tier) -> new EnhancedFluidHatchPartMachine(holder, tier, io,
                        EnhancedFluidHatchPartMachine.INITIAL_TANK_CAPACITY, 1),
                (tier, builder) -> builder
                        .langValue(GTValues.VNF[tier] + " " + displayName)
                        .rotationState(RotationState.ALL)
                        .colorOverlayTieredHullModel(ioOverlay, null, GTMachineModels.OVERLAY_FLUID_HATCH_TEX)
                        .abilities(ability)
                        .tooltips(Component.translatable("gtuf.machine.enhanced_fluid_hatch.tooltip"),
                                Component.translatable("gtceu.universal.tooltip.fluid_storage_capacity",
                                        FormattingUtil.formatNumbers(
                                                EnhancedFluidHatchPartMachine.getEnhancedCapacity(tier))))
                        .allowCoverOnFront(true)
                        .register(),
                tiers);
        for (int tier : tiers) {
            trackEnhancedFluidHatch(tier, io, defs[tier]);
        }
        return defs;
    }

    /**
     * 注册增强型并行控制仓（缺省 LV~UV 八级）：并行上限 = 2^(tier-1 + max(0, tier-IV))，
     * 即 LV=1, MV=2, HV=4, EV=8, IV=16, LuV=64, ZPM=256, UV=1024。
     * 前期每级 ×2 保证低等级可用，后期（LuV 起）每级 ×4 追平原生增幅（原生 UV=256，本机为 4 倍）。
     * 机器端通过 {@code GTRecipeModifiers.PARALLEL_HATCH} 读取当前并行数放大配方。
     *
     * <p>可通过 {@code tiers} 只注册指定档位（如 {@code new int[]{GTValues.HV, GTValues.EV}}），
     * 仅支持 LV~UV（1~8）——并行仓模型纹理与公式仅覆盖该区间。</p>
     *
     * @param name        注册名（registerTieredMachines 会加 {@code VN[tier].toLowerCase() + "_"} 前缀）
     * @param displayName 显示名（前接 {@code GTValues.VNF[tier]}）
     * @param tiers       要注册的档位（tier 值），缺省注册 LV~UV 八级
     * @return 按 tier 索引的 MachineDefinition 数组（tier → definition，未注册档位为 null）
     */
    public static MachineDefinition[] registerEnhancedParallelHatches(String name, String displayName,
                                                                      int... tiers) {
        if (tiers.length == 0) {
            tiers = DEFAULT_PARALLEL_HATCH_TIERS;
        }
        for (int tier : tiers) {
            if (tier < GTValues.LV || tier > GTValues.UV) {
                throw new IllegalArgumentException(
                        "增强并行仓仅支持 LV~UV（tier " + GTValues.LV + "~" + GTValues.UV + "），收到 " + tier);
            }
        }
        return GTMachineUtils.registerTieredMachines(
                REGISTRATE, name, EnhancedParallelHatchPartMachine::new,
                (tier, builder) -> builder.langValue(GTValues.VNF[tier] + " " + displayName)
                        .rotationState(RotationState.ALL)
                        .abilities(PartAbility.PARALLEL_HATCH)
                        .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
                        .model(GTMachineModels.createWorkableTieredHullMachineModel(
                                GTCEu.id("block/machines/parallel_hatch_mk" + ((tier + 1) / 2))))
                        .tooltips(Component.translatable("gtuf.machine.enhanced_parallel_hatch.tooltip",
                                EnhancedParallelHatchPartMachine.getParallelLimit(tier)))
                        .register(),
                tiers);
    }

    /**
     * 注册增强蒸汽输入仓：仿原生蒸汽仓（STEAM_HATCH），仅接受蒸汽。
     * 容量为原生（64,000 mB）的 64 倍（4,096,000 mB），通过 capacity 参数自定义
     * （须为原生 64,000 的整数倍）。与工业蒸汽机搭配时触发 MV 配方与高效转换（0.25 mB/EU）。
     *
     * @param name     机器注册名（完整 id，如 "industrial_steam_input_hatch"）
     * @param capacity 蒸汽容量（mB）
     * @return 注册完成的 MachineDefinition
     */
    public static MachineDefinition registerIndustrialSteamHatch(String name, int capacity) {
        return REGISTRATE
                .machine(name, holder -> new IndustrialSteamHatchPartMachine(holder, capacity))
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.STEAM)
                .overlaySteamHullModel("steam_hatch")
                .tooltips(Component.translatable("gtceu.universal.tooltip.fluid_storage_capacity", capacity),
                        Component.translatable("gtceu.machine.steam.steam_hatch.tooltip"))
                .allowCoverOnFront(true)
                .register();
    }

    /** 关联增强流体仓配对表：swapIO 反查 (tier, IO) → 对应方向的 MachineDefinition。 */
    public static void trackEnhancedFluidHatch(int tier, IO io, MachineDefinition def) {
        ENHANCED_FLUID_HATCHES.computeIfAbsent(tier, k -> new EnumMap<>(IO.class)).put(io, def);
    }

    /**
     * 查询增强流体仓配对表：返回 (tier, io) 对应的 MachineDefinition，
     * 未关联时返回 null（此时 swapIO 不可用）。
     */
    public static MachineDefinition getEnhancedFluidHatch(int tier, IO io) {
        EnumMap<IO, MachineDefinition> byIo = ENHANCED_FLUID_HATCHES.get(tier);
        return byIo == null ? null : byIo.get(io);
    }

    public static void init() {}
}
