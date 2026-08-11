package com.liangqu.gtuf.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import com.liangqu.gtuf.GTUF_Core;

/**
 * GTUF 配置（config/gtuf-common.toml）。
 *
 * <p>
 * 配方平衡项分三类：
 * </p>
 * <ul>
 * <li><b>并行能耗倍率</b>（{@code parallelEutMultiplier}）：并行放大时把 EUt 乘上并行数
 * （能耗随并行线性增长、单件蒸汽/EU 成本恒定），供整合包作者整体调节并行带来的能耗开销——
 * 例如调低让并行更省汽、或设为 0 让并行不增耗能。</li>
 * <li><b>可增强电力机公式倍率</b>（{@code [enhanceableElectric]} 组）：并行倍率、框架能耗
 * 步长、管道时长步长，控制 {@code EnhanceableElectricMachine} 的增强曲线。</li>
 * <li><b>可增强蒸汽机公式倍率</b>（{@code [enhanceableSteam]} 组）：并行倍率、框架速度
 * 步长，控制 {@code EnhanceableSteamMachine} 的增强曲线。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = GTUF_Core.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GTUF_Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /** 并行能耗倍率：实际 EUt = 配方单次 EUt × 并行数 × 该倍率。默认 1.0（能耗随并行线性增长）。 */
    private static final ForgeConfigSpec.DoubleValue PARALLEL_EUT_MULTIPLIER = BUILDER
            .comment("并行能耗倍率（Parallel EUt Multiplier）",
                    "实际 EUt = 配方单次 EUt × 并行数 × 该倍率。",
                    "默认 1.0：能耗随并行数线性增长，单件蒸汽/EU 成本恒定，并行只换吞吐。",
                    "设为 0.0 可让并行不增耗能（不推荐，等于免费并行）。")
            .defineInRange("parallelEutMultiplier", 1.0, 0.0, Double.MAX_VALUE);

    // ---------------------------------------------------------------
    // 可增强电力多方块机（EnhanceableElectricMachine）公式倍率
    // ---------------------------------------------------------------

    /** 电力机并行倍率（指数底）：最大并行数 = 初始并行数 × 该值^(外壳等级-1)。默认 2.0。 */
    private static final ForgeConfigSpec.DoubleValue ELECTRIC_PARALLEL_BASE;
    /** 电力机框架能耗步长：能耗倍率 = 1 - (框架等级-1) × 该值。默认 0.05。 */
    private static final ForgeConfigSpec.DoubleValue ELECTRIC_FRAME_ENERGY_STEP;
    /** 电力机管道时长步长：时长倍率 = 1 - (管道等级-1) × 该值。默认 0.1。 */
    private static final ForgeConfigSpec.DoubleValue ELECTRIC_PIPE_SPEED_STEP;

    // ---------------------------------------------------------------
    // 可增强蒸汽多方块机（EnhanceableSteamMachine）公式倍率
    // ---------------------------------------------------------------

    /** 蒸汽机并行倍率（指数底）：最大并行数 = 初始并行数 × 该值^(外壳等级-1)。默认 4.0。 */
    private static final ForgeConfigSpec.DoubleValue STEAM_PARALLEL_BASE;
    /** 蒸汽机框架速度步长：实际时长 = 原始时长^(1 - (框架等级-1) × 该值)。默认 0.1。 */
    private static final ForgeConfigSpec.DoubleValue STEAM_FRAME_SPEED_STEP;

    static {
        BUILDER.comment(" EnhanceableElectricMachine 的公式倍率")
                .push("enhanceableElectric");
        ELECTRIC_PARALLEL_BASE = BUILDER
                .comment("并行倍率（指数底）：最大并行数 = 初始并行数 × 该值^(外壳等级-1)。",
                        "默认 2.0：钢=初始×1、铝=初始×2、不锈钢=初始×4、钛=初始×8、钨钢=初始×16。")
                .defineInRange("parallelBase", 2.0, 1.0, 64.0);
        ELECTRIC_FRAME_ENERGY_STEP = BUILDER
                .comment("框架能耗步长：能耗倍率 = 1 - (框架等级-1) × 该值（下限 0）。",
                        "默认 0.05：钢=100%、铝=95%、不锈钢=90%、钛=85%、钨钢=80%。")
                .defineInRange("frameEnergyStep", 0.05, 0.0, 1.0);
        ELECTRIC_PIPE_SPEED_STEP = BUILDER
                .comment("管道时长步长：时长倍率 = 1 - (管道等级-1) × 该值（下限 0）。",
                        "默认 0.1：青铜=100%、钢=90%、钛=70%、钨钢=60%。")
                .defineInRange("pipeSpeedStep", 0.1, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.comment(" EnhanceableSteamMachine 的公式倍率")
                .push("enhanceableSteam");
        STEAM_PARALLEL_BASE = BUILDER
                .comment("并行倍率（指数底）：最大并行数 = 初始并行数 × 该值^(外壳等级-1)。",
                        "默认 4.0：青铜=初始×1、脱氧钢=初始×4。")
                .defineInRange("parallelBase", 4.0, 1.0, 64.0);
        STEAM_FRAME_SPEED_STEP = BUILDER
                .comment("框架速度步长：实际时长 = 原始时长^(1 - (框架等级-1) × 该值)。",
                        "默认 0.1：青铜（等级1）=原始时长、钢（等级2）=原始时长^0.9。")
                .defineInRange("frameSpeedStep", 0.1, 0.0, 1.0);
        BUILDER.pop();
    }

    private static final ForgeConfigSpec SPEC = BUILDER.build();

    private static double parallelEutMultiplier = 1.0;
    private static double electricParallelBase = 2.0;
    private static double electricFrameEnergyStep = 0.05;
    private static double electricPipeSpeedStep = 0.1;
    private static double steamParallelBase = 4.0;
    private static double steamFrameSpeedStep = 0.1;

    private GTUF_Config() {}

    /** 当前并行能耗倍率（供配方处理逻辑读取）。 */
    public static double getParallelEutMultiplier() {
        return parallelEutMultiplier;
    }

    /** 电力机并行倍率（指数底）。 */
    public static double getElectricParallelBase() {
        return electricParallelBase;
    }

    /** 电力机框架能耗步长。 */
    public static double getElectricFrameEnergyStep() {
        return electricFrameEnergyStep;
    }

    /** 电力机管道时长步长。 */
    public static double getElectricPipeSpeedStep() {
        return electricPipeSpeedStep;
    }

    /** 蒸汽机并行倍率（指数底）。 */
    public static double getSteamParallelBase() {
        return steamParallelBase;
    }

    /** 蒸汽机框架速度步长。 */
    public static double getSteamFrameSpeedStep() {
        return steamFrameSpeedStep;
    }

    /** 配置规格，由 {@code GTUF_Core} 构造期注册。 */
    public static ForgeConfigSpec spec() {
        return SPEC;
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ModConfigEvent 涵盖初次加载与运行中重载，两者都需刷新缓存值。
        if (event.getConfig().getSpec() == SPEC) {
            parallelEutMultiplier = PARALLEL_EUT_MULTIPLIER.get();
            electricParallelBase = ELECTRIC_PARALLEL_BASE.get();
            electricFrameEnergyStep = ELECTRIC_FRAME_ENERGY_STEP.get();
            electricPipeSpeedStep = ELECTRIC_PIPE_SPEED_STEP.get();
            steamParallelBase = STEAM_PARALLEL_BASE.get();
            steamFrameSpeedStep = STEAM_FRAME_SPEED_STEP.get();
        }
    }
}
