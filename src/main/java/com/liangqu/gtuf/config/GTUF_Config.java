package com.liangqu.gtuf.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import com.liangqu.gtuf.GTUF_Core;
import com.liangqu.gtuf.api.data.material.PressurePipeProperties;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * <li><b>压力系统</b>（{@code [pressure]} 组）：大气压、腔压回归/传导速率、玻璃压力上下限公式
 * 参数、压力管道容限（"材质名=maxKpa:minKpa"）。控制 {@code PressureMultiblockMachine} 的
 * 腔压自变化曲线与玻璃等级对应的腔压上下限，以及各材质的压力管道能承受的压力量程。</li>
 * <li><b>节能仓</b>（{@code [energySaving]} 组）：减免额外倍率与能耗倍率下限，控制
 * {@code EnergySavingHatchPartMachine} 按档位给出的能耗减免曲线。</li>
 * <li><b>仓室与原版多方块结构兼容</b>（{@code [structureCompat]} 组）：线程仓/节能仓能否
 * 装入 GTM 原版电力多方块结构位（autoAbilities mixin 注入）的开关。</li>
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
    // 压力系统（Pressure）
    // ---------------------------------------------------------------

    /** 标准大气压（kPa）。 */
    private static final ForgeConfigSpec.DoubleValue PRESSURE_ATMOSPHERIC;
    /** 腔压向大气回归的基准速率（每 tick 比例），除以外壳等级。 */
    private static final ForgeConfigSpec.DoubleValue PRESSURE_RELAX_BASE_RATE_KPA;
    /** 玻璃下限基准（kPa）：min = max(0.1, base - (tier-1)*step)。 */
    private static final ForgeConfigSpec.DoubleValue PRESSURE_GLASS_MIN_BASE;
    /** 玻璃下限步长（kPa）：每级玻璃等级放宽下限的量。 */
    private static final ForgeConfigSpec.DoubleValue PRESSURE_GLASS_MIN_STEP;
    /** 玻璃上限基准（kPa）：max = base + (tier-1)*step。 */
    private static final ForgeConfigSpec.DoubleValue PRESSURE_GLASS_MAX_BASE;
    /** 玻璃上限步长（kPa）：每级玻璃等级放宽上限的量。 */
    private static final ForgeConfigSpec.DoubleValue PRESSURE_GLASS_MAX_STEP;
    /** 腔压向组均值均衡的速率（每 4 tick 比例）。 */
    private static final ForgeConfigSpec.DoubleValue PRESSURE_CONDUCTION_RATE;
    /** 压力管道容限条目（格式 {@code "材质名=maxKpa:minKpa"}）。 */
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> PRESSURE_PIPE_TOLERANCES_CONFIG;

    // ---------------------------------------------------------------
    // 节能仓（Energy Saving Hatch）
    // ---------------------------------------------------------------

    /** 节能仓减免额外倍率：能耗倍率 = max(minMultiplier, (100 - 5 × 档位差 × 该值) / 100)。 */
    private static final ForgeConfigSpec.DoubleValue ENERGY_SAVING_EXTRA_MULTIPLIER;
    /** 节能仓能耗倍率下限（防减免到 0/负）。 */
    private static final ForgeConfigSpec.DoubleValue ENERGY_SAVING_MIN_MULTIPLIER;

    // ---------------------------------------------------------------
    // 仓室与原版多方块结构兼容（structureCompat）
    // ---------------------------------------------------------------

    /** 线程仓装入 GTM 原版电力多方块结构位（autoAbilities mixin 注入）。 */
    private static final ForgeConfigSpec.BooleanValue THREAD_HATCH_VANILLA_INJECT;
    /** 节能仓装入 GTM 原版电力多方块结构位（autoAbilities mixin 注入）。 */
    private static final ForgeConfigSpec.BooleanValue ENERGY_SAVING_HATCH_VANILLA_INJECT;

    /**
     * 内置默认压力管道容限（"材质名=maxKpa:minKpa"，单位 kPa）。
     * bronze/steel/titanium/tungstensteel 与 GTM 材质注册名一致（小写）。
     */
    public static final List<String> DEFAULT_PRESSURE_PIPE_TOLERANCES = List.of(
            "bronze=3000:10",
            "steel=6000:10",
            "titanium=12000:5",
            "tungstensteel=24000:5");

    /**
     * 当前生效的压力管道容限（材质名 → {@link PressurePipeProperties}），
     * config 加载/重载时原地刷新。字段初始化即填入内置默认值，保证 config 加载前的读取拿到默认映射。
     */
    private static final Map<String, PressurePipeProperties> pressurePipeTolerances = parsePressurePipeTolerances(
            DEFAULT_PRESSURE_PIPE_TOLERANCES);

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

        BUILDER.comment(" 压力系统（Pressure）：单位 kPa，1 大气压 = 101.325 kPa")
                .push("pressure");
        PRESSURE_ATMOSPHERIC = BUILDER
                .comment("标准大气压（kPa）。也是腔压自变化公式的收敛目标：",
                        "腔压高于/低于该值都会向它回归，速率见 relaxBaseRateKpa。")
                .defineInRange("atmosphericPressure", 101.325, 0.1, 1.0E9);
        PRESSURE_RELAX_BASE_RATE_KPA = BUILDER
                .comment("腔压向大气回归的恒定绝对速率（kPa/tick），除以外壳等级：",
                        "relaxRate = relaxBaseRateKpa / casingTier。",
                        "恒定绝对速率：无论偏离多少每 tick 固定掉该量（钳制到大气压不再越过），",
                        "比指数比例衰减更易维持超高压（高压时掉速不再随偏离暴涨）。",
                        "外壳等级越高速率参数越小（高级外壳回归越慢，更稳定）。")
                .defineInRange("relaxBaseRateKpa", 1.0E-1, 1.0E-6, 1.0E9);
        PRESSURE_GLASS_MIN_BASE = BUILDER
                .comment("玻璃压力下限基准（kPa）：min = max(0.1, base - (玻璃等级-1) × step)。")
                .defineInRange("glassMinBaseKpa", 50.0, 0.1, 1.0E9);
        PRESSURE_GLASS_MIN_STEP = BUILDER
                .comment("玻璃压力下限步长（kPa）：玻璃等级每 +1，压力下限再放宽该量。",
                        "玻璃等级越高压力下限越低。")
                .defineInRange("glassMinStep", 10.0, 0.0, 1.0E9);
        PRESSURE_GLASS_MAX_BASE = BUILDER
                .comment("玻璃压力上限基准（kPa）：max = base + (玻璃等级-1) × step。")
                .defineInRange("glassMaxBaseKpa", 400.0, 0.1, 1.0E9);
        PRESSURE_GLASS_MAX_STEP = BUILDER
                .comment("玻璃压力上限步长（kPa）：玻璃等级每 +1，压力上限再放宽该量。",
                        "玻璃等级越高压力上限越高。")
                .defineInRange("glassMaxStep", 200.0, 0.0, 1.0E9);
        PRESSURE_CONDUCTION_RATE = BUILDER
                .comment("腔压经管道向同组均值均衡的速率（每 4 tick 比例）：",
                        "p += (组均值 - p) × conductionRate。")
                .defineInRange("conductionRate", 0.05, 0.0, 1.0);
        PRESSURE_PIPE_TOLERANCES_CONFIG = BUILDER
                .comment("压力管道容限：\"材质名=maxKpa:minKpa\" 列表（单位 kPa）。",
                        "材质名用小写英文（如 bronze/steel/titanium/tungstensteel），须与 GTM 材质注册名一致；",
                        "解析失败的条目会被跳过并在日志记录。",
                        "带该属性的材质会生成压力管道，管道超压/欠压（腔压超过 [min,max]）会破裂。")
                .defineListAllowEmpty("pressurePipeTolerances", DEFAULT_PRESSURE_PIPE_TOLERANCES,
                        element -> element instanceof String s && s.contains("="));
        BUILDER.pop();

        BUILDER.comment(" 节能仓（energy saving hatch）")
                .push("energySaving");
        ENERGY_SAVING_EXTRA_MULTIPLIER = BUILDER
                .comment("节能仓减免额外倍率：能耗倍率 = max(minMultiplier, (100 - 5 × 档位差 × 该值) / 100)。",
                        "档位差从 LV 起算 1（LV=1、MV=2、HV=3、EV=4…）。",
                        "默认 1.0：LV=95%、MV=90%、HV=85%、EV=80%…每级多减免 5%。",
                        "调大让高等级仓减免更多（如 2.0：LV=90%、MV=80%…）。")
                .defineInRange("extraMultiplier", 1.0, 0.0, Double.MAX_VALUE);
        ENERGY_SAVING_MIN_MULTIPLIER = BUILDER
                .comment("节能仓能耗倍率下限（防减免到 0/负）：能耗倍率最低为该值。",
                        "默认 0.05 = 5%（再高的仓也不会让配方完全免费）。")
                .defineInRange("minMultiplier", 0.05, 0.01, 1.0);
        BUILDER.pop();

        BUILDER.comment(" 仓室与原版多方块结构兼容（mixin 注入）")
                .push("structureCompat");
        THREAD_HATCH_VANILLA_INJECT = BUILDER
                .comment("线程仓装入 GTM 原版电力多方块结构位（autoAbilities mixin 注入）。",
                        "开启后线程仓可占据任意用 autoAbilities 定义能力位的 GTM 电力多方块的仓室位",
                        "（含 GTM 原生机器，如大型组装厂）。关闭后线程仓只能装入显式声明其能力位的",
                        "GTUF 多方块结构。结构位在游戏内首次结构检查时固化，改动需重启生效。")
                .define("threadHatchVanillaInjection", false);
        ENERGY_SAVING_HATCH_VANILLA_INJECT = BUILDER
                .comment("节能仓装入 GTM 原版电力多方块结构位（autoAbilities mixin 注入）。",
                        "开启后节能仓可占据任意用 autoAbilities 定义能力位的 GTM 电力多方块的仓室位",
                        "（含 GTM 原生机器）。关闭后节能仓只能装入显式声明其能力位的 GTUF 多方块结构。",
                        "结构位在游戏内首次结构检查时固化，改动需重启生效。")
                .define("energySavingHatchVanillaInjection", false);
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
    private static double pressureAtmospheric = 101.325;
    private static double pressureRelaxBaseRateKpa = 10.0;
    private static double pressureGlassMinBaseKpa = 50.0;
    private static double pressureGlassMinStep = 10.0;
    private static double pressureGlassMaxBaseKpa = 400.0;
    private static double pressureGlassMaxStep = 200.0;
    private static double pressureConductionRate = 0.05;
    private static double energySavingExtraMultiplier = 1.0;
    private static double energySavingMinMultiplier = 0.05;
    private static boolean threadHatchVanillaInjection = true;
    private static boolean energySavingHatchVanillaInjection = true;

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

    /** 标准大气压（kPa），也是腔压自变化公式的收敛目标。 */
    public static double getPressureAtmospheric() {
        return pressureAtmospheric;
    }

    /** 腔压向大气回归的恒定绝对速率（kPa/tick，再除以外壳等级）。 */
    public static double getPressureRelaxBaseRateKpa() {
        return pressureRelaxBaseRateKpa;
    }

    /** 玻璃压力下限基准（kPa）。 */
    public static double getPressureGlassMinBaseKpa() {
        return pressureGlassMinBaseKpa;
    }

    /** 玻璃压力下限步长（kPa）。 */
    public static double getPressureGlassMinStep() {
        return pressureGlassMinStep;
    }

    /** 玻璃压力上限基准（kPa）。 */
    public static double getPressureGlassMaxBaseKpa() {
        return pressureGlassMaxBaseKpa;
    }

    /** 玻璃压力上限步长（kPa）。 */
    public static double getPressureGlassMaxStep() {
        return pressureGlassMaxStep;
    }

    /** 腔压向同组均值均衡的速率（每 4 tick 比例）。 */
    public static double getPressureConductionRate() {
        return pressureConductionRate;
    }

    /** 节能仓减免额外倍率（乘在 "-5% × 档位差" 上）。 */
    public static double getEnergySavingExtraMultiplier() {
        return energySavingExtraMultiplier;
    }

    /** 节能仓能耗倍率下限（防减免到 0/负）。 */
    public static double getEnergySavingMinMultiplier() {
        return energySavingMinMultiplier;
    }

    /** 线程仓能否装入 GTM 原版电力多方块结构位（autoAbilities mixin 注入）。 */
    public static boolean isThreadHatchVanillaInjection() {
        return threadHatchVanillaInjection;
    }

    /** 节能仓能否装入 GTM 原版电力多方块结构位（autoAbilities mixin 注入）。 */
    public static boolean isEnergySavingHatchVanillaInjection() {
        return energySavingHatchVanillaInjection;
    }

    /** 查询某材质（小写英文名，如 "bronze"）的压力管道容限；未配置返回 null。 */
    @Nullable
    public static PressurePipeProperties getPressurePipeTolerance(String materialName) {
        return pressurePipeTolerances.get(materialName);
    }

    /**
     * 当前压力管道容限映射（材质名 → 容限属性）。返回的 map 是运行期共享实例，
     * config 重载时在内部原地刷新；注意运行期重载不会回头改已 attach 到材质上的旧属性对象，
     * 压力管道容限改动需重启生效（重新生成管道方块）。
     */
    public static Map<String, PressurePipeProperties> getPressurePipeTolerances() {
        return pressurePipeTolerances;
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
            pressureAtmospheric = PRESSURE_ATMOSPHERIC.get();
            pressureRelaxBaseRateKpa = PRESSURE_RELAX_BASE_RATE_KPA.get();
            pressureGlassMinBaseKpa = PRESSURE_GLASS_MIN_BASE.get();
            pressureGlassMinStep = PRESSURE_GLASS_MIN_STEP.get();
            pressureGlassMaxBaseKpa = PRESSURE_GLASS_MAX_BASE.get();
            pressureGlassMaxStep = PRESSURE_GLASS_MAX_STEP.get();
            pressureConductionRate = PRESSURE_CONDUCTION_RATE.get();
            energySavingExtraMultiplier = ENERGY_SAVING_EXTRA_MULTIPLIER.get();
            energySavingMinMultiplier = ENERGY_SAVING_MIN_MULTIPLIER.get();
            threadHatchVanillaInjection = THREAD_HATCH_VANILLA_INJECT.get();
            energySavingHatchVanillaInjection = ENERGY_SAVING_HATCH_VANILLA_INJECT.get();
            pressurePipeTolerances.clear();
            pressurePipeTolerances.putAll(parsePressurePipeTolerances(PRESSURE_PIPE_TOLERANCES_CONFIG.get()));
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

    /**
     * 解析 {@code pressurePipeTolerances} 配置条目（"{@code 材质名=maxKpa:minKpa}"）为
     * 材质名 → 容限属性映射。格式错误的条目逐个跳过并记录日志——单个条目写错不会让整份映射丢失。
     * 校验：max 须 > 0、min 须 >= 0 且 min < max（min==max 无意义，min 高于 max 是反的）。
     */
    private static Map<String, PressurePipeProperties> parsePressurePipeTolerances(List<? extends String> entries) {
        Map<String, PressurePipeProperties> parsed = new LinkedHashMap<>();
        for (String entry : entries) {
            String trimmed = entry.trim();
            int eq = trimmed.indexOf('=');
            if (eq <= 0 || eq == trimmed.length() - 1) {
                LOGGER.warn("[GTUF] 压力管道容限条目格式错误（应为 \"材质名=maxKpa:minKpa\"），已跳过: {}", entry);
                continue;
            }
            String materialName = trimmed.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String range = trimmed.substring(eq + 1).trim();
            int colon = range.indexOf(':');
            if (colon <= 0 || colon == range.length() - 1) {
                LOGGER.warn("[GTUF] 压力管道容限条目区间格式错误（应为 \"maxKpa:minKpa\"），已跳过: {}", entry);
                continue;
            }
            double maxKpa;
            double minKpa;
            try {
                maxKpa = Double.parseDouble(range.substring(0, colon).trim());
                minKpa = Double.parseDouble(range.substring(colon + 1).trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("[GTUF] 压力管道容限条目数值非数字，已跳过: {}", entry);
                continue;
            }
            if (maxKpa <= 0 || minKpa < 0 || minKpa >= maxKpa) {
                LOGGER.warn("[GTUF] 压力管道容限须满足 max>0、min>=0 且 min<max，已跳过: {}", entry);
                continue;
            }
            parsed.put(materialName, new PressurePipeProperties(maxKpa, minKpa));
        }
        return parsed;
    }
}
