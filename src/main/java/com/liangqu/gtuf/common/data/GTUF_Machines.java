package com.liangqu.gtuf.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.registry.registrate.MachineBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.MultiblockMachineBuilder;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;
import com.gregtechceu.gtceu.common.machine.multiblock.part.SteamHatchPartMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.liangqu.gtuf.api.machine.multiblock.GTUF_PartAbility;
import com.liangqu.gtuf.api.registry.GTUF_CreativeModeTabs;
import com.liangqu.gtuf.common.data.models.GTUFModels;
import com.liangqu.gtuf.common.machine.multiblock.electric.EnhancedCoilElectricMachine;
import com.liangqu.gtuf.common.machine.multiblock.electric.PressureMultiblockMachine;
import com.liangqu.gtuf.common.machine.multiblock.part.EnergySavingHatchPartMachine;
import com.liangqu.gtuf.common.machine.multiblock.part.EnhancedFluidHatchPartMachine;
import com.liangqu.gtuf.common.machine.multiblock.part.EnhancedParallelHatchPartMachine;
import com.liangqu.gtuf.common.machine.multiblock.part.IndustrialSteamHatchPartMachine;
import com.liangqu.gtuf.common.machine.multiblock.part.PressureHatchPartMachine;
import com.liangqu.gtuf.common.machine.multiblock.part.ThreadHatchPartMachine;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.*;
import static com.liangqu.gtuf.api.registry.GTUF_Registries.REGISTRATE;

/**
 * GTUF 公开机器注册入口（框架级），仿原生 {@code GTMachines}。
 *
 * <p>
 * 框架定位：公开发布 jar 只提供"方法和接口"，<b>不预注册任何机器实例</b>。
 * 增强流体仓、增强并行仓、工业蒸汽仓均由整合包作者通过本类的<b>公开注册工厂</b>
 * （Java/KubeJS/CrT 调用）按需注册。因此本类没有静态注册字段，只有工厂与配对表。
 *
 * <p>
 * 测试专用机器在 {@code GTUF_Machine_Test}（testmod 源码集），不随公开发布打包。
 * 框架代码（部件类、机器类、本注册类）禁止引用测试类，避免打包剔除测试内容后断链。
 * 依赖方向：{@code GTUF_Machine_Test} → {@code GTUF_Machines}（测试可依赖框架，反之不可）。
 * </p>
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

    /** 线程仓默认注册档位：UV~MAX 七级（仿 GTOcore {@code THREAD_HATCH} 注册 tiers）。 */
    private static final int[] DEFAULT_THREAD_HATCH_TIERS = new int[] {
            GTValues.UV, GTValues.UHV, GTValues.UEV, GTValues.UIV,
            GTValues.UXV, GTValues.OpV, GTValues.MAX
    };

    /**
     * 注册增强型流体仓（容量 = 8000 * 4^tier，每级 ×4 无封顶；缺省档位 ULV~UHV，highTier 开启含 UEV+）。
     * tooltip 显示 float 理论容量；实际 tank 容量 int 钳制。仿原生 GTMachines.FLUID_IMPORT_HATCH。
     *
     * <p>
     * 可通过 {@code tiers} 只注册指定档位（如 {@code new int[]{GTValues.LV, GTValues.EV}}），
     * 缺省为 {@link GTMachineUtils#ALL_TIERS}。部件能力由 {@code io} 推导（IN→IMPORT_FLUIDS，
     * OUT→EXPORT_FLUIDS）。注册后自动填充 {@link #ENHANCED_FLUID_HATCHES} 配对表（仅已注册档位），
     * 供 swapIO 反查。
     * </p>
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
        // 注意：GTUF 的 REGISTRATE modid 是 "gtuf"，String 重载会把 overlay 纹理拼成
        // gtuf:block/overlay/machine/...（不存在）。这里必须显式用 GTCEu.id 指向 GTM 的纹理。
        ResourceLocation overlayTex = GTCEu.id("block/overlay/machine/" + ioOverlay);
        ResourceLocation emissiveTex = GTCEu.id("block/overlay/machine/" + GTMachineModels.OVERLAY_FLUID_HATCH_TEX);
        MachineDefinition[] defs = GTMachineUtils.registerTieredMachines(REGISTRATE, name,
                (holder, tier) -> new EnhancedFluidHatchPartMachine(holder, tier, io,
                        EnhancedFluidHatchPartMachine.INITIAL_TANK_CAPACITY, 1),
                (tier, builder) -> builder
                        .langValue(GTValues.VNF[tier] + " " + displayName)
                        .rotationState(RotationState.ALL)
                        .colorOverlayTieredHullModel(overlayTex, null, emissiveTex)
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
     * <p>
     * 可通过 {@code tiers} 只注册指定档位（如 {@code new int[]{GTValues.HV, GTValues.EV}}），
     * 仅支持 LV~UV（1~8）——并行仓模型纹理与公式仅覆盖该区间。
     * </p>
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
                        // 用 GTUF 工厂：多注册 bottom/top/side 可替换纹理，成型后 blank 掉仓室自身
                        // 材质避免与外壳 z-fight 闪烁（7.1.4），配合 replacePartModelWhenFormed 覆盖
                        // 7.3.0 的 IS_FORMED 门控。
                        .model(GTUFModels.createTieredHullMachineModel(
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
                .overlaySteamHullModel(GTCEu.id("block/machine/part/steam_hatch"))
                .tooltips(Component.translatable("gtceu.universal.tooltip.fluid_storage_capacity", capacity),
                        Component.translatable("gtceu.machine.steam.steam_hatch.tooltip"))
                .allowCoverOnFront(true)
                .register();
    }

    /**
     * 线圈增强电力多方块机注册入口（框架级工厂）：返回<b>未注册</b>的 {@link MachineBuilder}，
     * 由调用方继续链式设置 {@code recipeType(s)}、{@code pattern}（需含线圈位
     * {@code Predicates.heatingCoils()}，保证 "CoilType" 写入 MatchContext）、{@code model}
     * 后再 {@code register()}。框架只提供配方逻辑，结构形状由整合包自定义
     * （同 {@link #colorOverlayHull} 的 KJS 友好 builder 返回模式）。
     *
     * <p>
     * 四个创建期参数对应 {@code EnhancedCoilElectricMachine} 的四个特性：
     * 初始并行数、线圈提速（≤0 关闭）、线圈额外并行（≤0 关闭）、线圈能耗减免（≤0 关闭），
     * 另有过时钟方式（true 完美 / false 非完美）。
     * </p>
     *
     * @param name             注册名（完整 id，如 "enhanced_coil_furnace"）
     * @param baseParallel     初始并行数（≥1，恒启用）
     * @param speedStep        线圈提速步长（≤0 关闭）
     * @param parallelPerLevel 线圈额外并行参数（≤0 关闭）
     * @param energyStep       线圈能耗减免步长（≤0 关闭）
     * @param perfectOC        过时钟方式（true 完美 / false 非完美）
     * @return 已配置好构造器与配方修改器的 MultiblockMachineBuilder（未 register）
     */
    @SuppressWarnings("rawtypes")
    public static MultiblockMachineBuilder coilEnhanceableElectricMachine(
                                                                          String name,
                                                                          int baseParallel,
                                                                          double speedStep,
                                                                          double parallelPerLevel,
                                                                          double energyStep,
                                                                          boolean perfectOC) {
        return REGISTRATE.multiblock(name, holder -> new EnhancedCoilElectricMachine(
                holder, baseParallel, speedStep, parallelPerLevel, energyStep, perfectOC))
                .rotationState(RotationState.ALL)
                .recipeModifier(EnhancedCoilElectricMachine::recipeModifier, true)
                .addOutputLimit(ItemRecipeCapability.CAP, 1);
    }

    /**
     * 注册线程仓（缺省 UV~MAX 七级）：线程数上限 = 2^(tier-LuV)，
     * 即 UV=4, UHV=8, UEV=16, UIV=32, UXV=64, OpV=128, MAX=256。GUI 中可调 1~上限，默认 1。
     * 安装该仓室的多方块通过 {@link com.liangqu.gtuf.api.machine.IThreadModifierMachine} 读取
     * 当前线程数，可同时处理多类配方（配合多线程控制器
     * {@code com.liangqu.gtuf.common.machine.multiblock.electric.MultiThreadElectricMachine}）。
     *
     * <p>
     * 公式 {@code 2^(tier-LuV)} 与 GUI 配置模型来源：GTOcore
     * {@code ThreadHatchPartMachine}（{@code super(holder, tier, 1, 1L << (tier - LuV))}，
     * 下限 1、上限 2^(tier-LuV)）。
     * </p>
     *
     * <p>
     * 可通过 {@code tiers} 只注册指定档位（如 {@code new int[]{GTValues.UV, GTValues.MAX}}），
     * 缺省为 {@link #DEFAULT_THREAD_HATCH_TIERS}（UV~MAX 七级）。
     * </p>
     *
     * @param name        注册名（registerTieredMachines 会加 {@code VN[tier].toLowerCase() + "_"} 前缀）
     * @param displayName 显示名（前接 {@code GTValues.VNF[tier]}）
     * @param tiers       要注册的档位（tier 值），缺省注册 UV~MAX 七级
     * @return 按 tier 索引的 MachineDefinition 数组（tier → definition，未注册档位为 null）
     */
    public static MachineDefinition[] registerThreadHatches(String name, String displayName, int... tiers) {
        if (tiers.length == 0) {
            tiers = DEFAULT_THREAD_HATCH_TIERS;
        }
        for (int tier : tiers) {
            if (tier < GTValues.LuV) {
                throw new IllegalArgumentException(
                        "线程仓仅支持 LuV~MAX（tier " + GTValues.LuV + "~" + GTValues.MAX + "），收到 " + tier);
            }
        }
        return GTMachineUtils.registerTieredMachines(
                REGISTRATE, name, ThreadHatchPartMachine::new,
                (tier, builder) -> builder.langValue(GTValues.VNF[tier] + " " + displayName)
                        .rotationState(RotationState.ALL)
                        .abilities(GTUF_PartAbility.THREAD_HATCH)
                        .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
                        // 用 GTUF 工厂：多注册 bottom/top/side 可替换纹理，成型后 blank 掉仓室自身
                        // 材质避免与外壳 z-fight 闪烁（7.1.4），配合 replacePartModelWhenFormed 覆盖
                        // 7.3.0 的 IS_FORMED 门控。
                        .model(GTUFModels.createTieredHullMachineModel(
                                GTCEu.id("block/machines/parallel_hatch_mk4")))
                        .tooltips(Component.translatable("gtuf.machine.thread_hatch.tooltip",
                                1 << (tier - GTValues.LuV)))
                        .register(),
                tiers);
    }

    /** 压力仓缺省档位：LV~HV 三级。 */
    public static final int[] DEFAULT_PRESSURE_HATCH_TIERS = new int[] { GTValues.LV, GTValues.MV, GTValues.HV };

    /**
     * 注册压力仓（缺省 LV~HV 三级）：压力机的 IO 终端，<b>不存储压力</b>，把压力机腔体
     * 接入压力管道网络。传导与破裂判定由 {@code PressureMultiblockMachine#pressureTick}
     * 经 {@code PressureHatchPartMachine#getConnectedNets()} 驱动。档位仅决定机型档位展示
     * 与传导语义，不改变功能。
     *
     * <p>
     * 可通过 {@code tiers} 只注册指定档位（如 {@code new int[]{GTValues.LV, GTValues.HV}}）。
     * </p>
     *
     * @param name        注册名（registerTieredMachines 会加 {@code VN[tier].toLowerCase() + "_"} 前缀）
     * @param displayName 显示名（前接 {@code GTValues.VNF[tier]}）
     * @param tiers       要注册的档位（tier 值），缺省 LV~HV 三级
     * @return 按 tier 索引的 MachineDefinition 数组（tier → definition，未注册档位为 null）
     */
    public static MachineDefinition[] registerPressureHatches(String name, String displayName, int... tiers) {
        if (tiers.length == 0) {
            tiers = DEFAULT_PRESSURE_HATCH_TIERS;
        }
        return GTMachineUtils.registerTieredMachines(
                REGISTRATE, name, PressureHatchPartMachine::new,
                (tier, builder) -> builder.langValue(GTValues.VNF[tier] + " " + displayName)
                        .rotationState(RotationState.ALL)
                        .abilities(GTUF_PartAbility.PRESSURE)
                        .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
                        .model(GTUFModels.createTieredHullMachineModel(
                                GTCEu.id("block/machines/parallel_hatch_mk4")))
                        .tooltips(Component.translatable("gtuf.machine.pressure_hatch.tooltip"))
                        .register(),
                tiers);
    }

    /** 节能仓缺省档位：LV~UV 八级。 */
    public static final int[] DEFAULT_ENERGY_SAVING_HATCH_TIERS = new int[] {
            GTValues.LV, GTValues.MV, GTValues.HV, GTValues.EV, GTValues.IV, GTValues.LuV, GTValues.ZPM, GTValues.UV
    };

    /**
     * 注册节能仓（缺省 LV~UV 八级）：安装该仓室的多方块额外获得一个由仓室档位决定的能耗减免
     * 倍率。减免公式（config {@code [energySaving]} 可调）：
     * {@code 倍率 = max(minMultiplier, (100 - 5 × 档位差 × extraMultiplier) / 100)}，
     * 档位差从 LV 起算 1。多仓共存取最优（减免最大者）。减免由
     * {@code GTUFEnergySavingRecipeLogicMixin} 作用于配方最终 EUt，对任意电力多方块
     * （含 GTM 原生机器）生效。
     *
     * <p>
     * 可通过 {@code tiers} 只注册指定档位（如 {@code new int[]{GTValues.HV, GTValues.IV}}），
     * 档位须 ≥ LV。
     * </p>
     *
     * @param name        注册名（registerTieredMachines 会加 {@code VN[tier].toLowerCase() + "_"} 前缀）
     * @param displayName 显示名（前接 {@code GTValues.VNF[tier]}）
     * @param tiers       要注册的档位（tier 值），缺省 LV~UV 八级
     * @return 按 tier 索引的 MachineDefinition 数组（tier → definition，未注册档位为 null）
     */
    public static MachineDefinition[] registerEnergySavingHatches(String name, String displayName, int... tiers) {
        if (tiers.length == 0) {
            tiers = DEFAULT_ENERGY_SAVING_HATCH_TIERS;
        }
        for (int tier : tiers) {
            if (tier < GTValues.LV) {
                throw new IllegalArgumentException(
                        "节能仓仅支持 LV 及以上（tier " + GTValues.LV + "~" + GTValues.MAX + "），收到 " + tier);
            }
        }
        return GTMachineUtils.registerTieredMachines(
                REGISTRATE, name, EnergySavingHatchPartMachine::new,
                (tier, builder) -> builder.langValue(GTValues.VNF[tier] + " " + displayName)
                        .rotationState(RotationState.ALL)
                        .abilities(GTUF_PartAbility.ENERGY_SAVING)
                        .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
                        // 用 GTUF 工厂：多注册 bottom/top/side 可替换纹理，成型后 blank 掉仓室自身
                        // 材质避免与外壳 z-fight 闪烁（7.1.4），配合 replacePartModelWhenFormed 覆盖
                        // 7.3.0 的 IS_FORMED 门控。
                        .model(GTUFModels.createTieredHullMachineModel(
                                GTCEu.id("block/machines/parallel_hatch_mk4")))
                        .tooltips(Component.translatable("gtuf.machine.energy_saving_hatch.tooltip",
                                (int) Math.round((1.0 - EnergySavingHatchPartMachine.getEnergyMultiplier(tier)) * 100)))
                        .register(),
                tiers);
    }

    /**
     * 压力多方块机工厂（仿 {@link #coilEnhanceableElectricMachine}）：返回已挂载
     * {@code recipeModifier}（玻璃等级超限停机等待回区间）与输出上限的 builder，
     * 调用方链式补 {@code recipeType/pattern/model/appearanceBlock} 后 {@code register()}。
     * 腔压/外壳回归速率/玻璃上下限公式见 {@code PressureMultiblockMachine}。
     *
     * @param name 注册名
     * @return 多方块 builder（未 register）
     */
    @SuppressWarnings("rawtypes")
    public static MultiblockMachineBuilder registerPressureMultiblock(String name) {
        return REGISTRATE.multiblock(name, PressureMultiblockMachine::new)
                .rotationState(RotationState.ALL)
                .recipeModifier(PressureMultiblockMachine::recipeModifier, true)
                .addOutputLimit(ItemRecipeCapability.CAP, 1);
    }

    /**
     * KJS 辅助：给分级机器挂"彩色挡板外壳 + 管道 overlay"模型，供 KubeJS 脚本在
     * {@code GTCEuStartupEvents.registry('gtceu:machine', ...)} 的 'custom' 分级 builder 的
     * {@code .definition((tier, builder) => ...)} 回调里调用。
     *
     * <p>
     * 为什么需要这个方法：{@code MachineBuilder.colorOverlayTieredHullModel} 的三参 String 重载
     * 中间那个可选的 pipeOverlay 参数原生是 null（GTM 流体/物品仓都传 null），但 KubeJS 的 Rhino
     * 引擎无法给该重载传 null——null 会同时匹配 {@code (String,String,String)} 与
     * {@code (ResourceLocation,...)} 两个重载，抛 {@code EvaluatorException: ambiguous} 直接崩启动。
     * 此方法在 Java 侧把 null 包装掉，JS 侧只需传 overlay 与 emissive 两个纹理名，无歧义。
     * </p>
     *
     * <p>
     * 用法（脚本内）：{@code GTUF_Machines.colorOverlayHull(builder,
     * 'overlay_pipe_in_emissive', 'overlay_fluid_hatch')}。overlay 是管道纹理（KJS builder 的
     * REGISTRATE modid 是 gtceu，纹理名会在 gtceu:block/overlay/machine/ 下解析），emissive 是
     * 发光层（如流体仓的 overlay_fluid_hatch）。
     * </p>
     *
     * @param builder  原生 MachineBuilder（'custom' 分级 builder 的 definition 回调参数）
     * @param overlay  主 overlay 纹理名，如 overlay_pipe_in_emissive / overlay_pipe_out_emissive
     * @param emissive 发光 overlay 纹理名，如 overlay_fluid_hatch（可为 null，同 Java 语义）
     * @return 原 builder，可继续链式调用
     */
    @SuppressWarnings("rawtypes")
    public static MachineBuilder colorOverlayHull(MachineBuilder builder, String overlay, String emissive) {
        return builder.colorOverlayTieredHullModel(overlay, null, emissive);
    }

    /**
     * KJS 辅助：给机器挂原生蒸汽仓外壳模型（steam_hatch），供 KubeJS 脚本在
     * {@code GTCEuStartupEvents.registry('gtceu:machine', ...)} 的 'custom' 分级 builder 的
     * {@code .definition((tier, builder) => ...)} 回调里调用。
     *
     * <p>
     * 为什么需要这个方法：{@code MachineBuilder.overlaySteamHullModel} 有 {@code (String)} 与
     * {@code (ResourceLocation)} 两个重载。KubeJS 给 {@code ResourceLocation} 注册了 TypeWrapper
     * （{@code CharSequence → RL}），把 JS 字符串到 String 的转换权重与到 RL 的权重拉平；而 Rhino
     * 对 NativeJavaObject（{@code GTCEu.id(...)} 的返回值）到 String 的转换权重也拉到相同——实测
     * <b>无论传 JS 字符串还是 {@code GTCEu.id(...)} 的 RL 对象都会抛 {@code EvaluatorException: ambiguous}</b>
     * 崩启动。此方法在 Java 侧直接调 {@code (ResourceLocation)} 重载，避开 Rhino 重载解析。
     * </p>
     *
     * <p>
     * 用法（脚本内）：{@code GTUF_Machines.overlaySteamHull(builder, 'steam_hatch')}。
     * 底层等价于 {@code builder.overlaySteamHullModel(GTCEu.id("block/machine/part/steam_hatch"))}，
     * 与 {@link #registerIndustrialSteamHatch} 的 proven 写法一致。
     * </p>
     *
     * @param builder        原生 MachineBuilder（'custom' 分级 builder 的 definition 回调参数）
     * @param overlayTexName 蒸汽外壳纹理名，如 steam_hatch（会拼成 block/machine/part/steam_hatch）
     * @return 原 builder，可继续链式调用
     */
    @SuppressWarnings("rawtypes")
    public static MachineBuilder overlaySteamHull(MachineBuilder builder, String overlayTexName) {
        return builder.overlaySteamHullModel(GTCEu.id("block/machine/part/" + overlayTexName));
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
