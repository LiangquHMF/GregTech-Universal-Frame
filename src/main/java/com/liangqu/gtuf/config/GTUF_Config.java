package com.liangqu.gtuf.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import com.liangqu.gtuf.GTUF_Core;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

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
 * <li><b>玻璃等级映射</b>（{@code [glass]} 组）：方块注册名 → 玻璃等级（= 电压等级）的
 * 完整映射，供 {@code GTUF_PatternPredicates.GlassTier} 谓词与玻璃 tooltip 使用。
 * 开放给整合包作者：可新增其他模组的玻璃方块、改默认玻璃等级，或删掉某条目使其不再算玻璃。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = GTUF_Core.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GTUF_Config {

    private static final Logger LOGGER = LogUtils.getLogger();

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

    // ---------------------------------------------------------------
    // 玻璃等级映射（glass tier）
    // ---------------------------------------------------------------

    /** 内置默认玻璃等级映射（九压标准：玻璃等级 = 电压等级，ULV=1/LV=2/…/UV=9）。 */
    public static final List<String> DEFAULT_GLASS_TIERS = List.of(
            "minecraft:glass=1",
            "minecraft:tinted_glass=2",
            "gtceu:tempered_glass=3",
            "gtceu:cleanroom_glass=4",
            "gtceu:laminated_glass=5",
            "gtceu:fusion_glass=7");

    /** config 玻璃等级条目（格式 {@code "注册名=等级"}）。 */
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> GLASS_TIERS_CONFIG;

    /**
     * 当前生效的玻璃等级映射（config 加载/重载时原地刷新，外部 {@code GLASS_TIERS} 引用恒可见最新值）。
     * 字段初始化即填入内置默认值，保证 config 加载前的读取（如机器注册期）也拿到完整默认映射。
     */
    private static final Map<String, Integer> glassTiers = parseGlassTiers(DEFAULT_GLASS_TIERS);

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

        BUILDER.comment(" 玻璃等级映射（glass tier）")
                .push("glass");
        GLASS_TIERS_CONFIG = BUILDER
                .comment("玻璃等级映射：\"注册名=等级\" 列表。等级为电压等级（九压标准 ULV=1/LV=2/…/UV=9）。",
                        "默认值 = GTUF 内置六个玻璃；整合包可新增其他模组的玻璃方块",
                        "（如 \"chisel:glass=1\"），或修改/删除条目改变或禁用某玻璃。",
                        "条目按注册名匹配（\"modid:block\"），解析失败的条目会被跳过并在日志记录。",
                        "改等级工具提示里的电压名越界（>9）时会降级为不显示而非崩溃。")
                .defineListAllowEmpty("glassTiers", DEFAULT_GLASS_TIERS,
                        element -> element instanceof String s && s.contains("="));
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

    /**
     * 当前玻璃等级映射（方块注册名 → 等级）。返回的 map 是运行期共享实例，
     * config 重载时在内部原地刷新，持有该引用者（如 {@code GTUF_PatternPredicates.GLASS_TIERS}）
     * 无需重新获取即可看到最新值。
     */
    public static Map<String, Integer> getGlassTiers() {
        return glassTiers;
    }

    /** 查询某方块注册名的玻璃等级；未纳入映射返回 null。 */
    @Nullable
    public static Integer getGlassTier(String blockId) {
        return glassTiers.get(blockId);
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
            glassTiers.clear();
            glassTiers.putAll(parseGlassTiers(GLASS_TIERS_CONFIG.get()));
        }
    }

    /**
     * 解析 {@code glassTiers} 配置条目（"{@code 注册名=等级}"）为映射。
     * 格式错误的条目逐个跳过并记录日志——单个条目写错不会让整份映射丢失。
     */
    private static Map<String, Integer> parseGlassTiers(List<? extends String> entries) {
        Map<String, Integer> parsed = new LinkedHashMap<>();
        for (String entry : entries) {
            String trimmed = entry.trim();
            int eq = trimmed.lastIndexOf('=');
            if (eq <= 0 || eq == trimmed.length() - 1) {
                LOGGER.warn("[GTUF] 玻璃等级条目格式错误（应为 \"注册名=等级\"），已跳过: {}", entry);
                continue;
            }
            String blockId = trimmed.substring(0, eq).trim();
            int tier;
            try {
                tier = Integer.parseInt(trimmed.substring(eq + 1).trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("[GTUF] 玻璃等级条目等级非整数，已跳过: {}", entry);
                continue;
            }
            if (tier < 1) {
                LOGGER.warn("[GTUF] 玻璃等级必须 >= 1，已跳过: {}", entry);
                continue;
            }
            parsed.put(blockId, tier);
        }
        return parsed;
    }
}
