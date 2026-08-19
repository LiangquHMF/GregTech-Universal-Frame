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
 * GTUF config (config/gtuf-common.toml).
 *
 * <p>Balance sections:</p>
 * <ul>
 * <li><b>[parallelEutMultiplier]</b>: EUt = recipe EUt × parallels × this.</li>
 * <li><b>[enhanceableElectric]</b>: parallel base, frame energy step, pipe speed step for EnhanceableElectricMachine.</li>
 * <li><b>[enhanceableSteam]</b>: parallel base, frame speed step for EnhanceableSteamMachine.</li>
 * <li><b>[glass]</b>: block ID → glass tier (voltage tier) mapping for GlassTier predicate.</li>
 * <li><b>[pressure]</b>: atmospheric pressure, relax/conduction rates, glass pressure bounds, pipe tolerances.</li>
 * <li><b>[energySaving]</b>: extra multiplier and min multiplier for energy-saving hatch.</li>
 * <li><b>[structureCompat]</b>: allow thread/energy-saving hatches in vanilla GTM multiblock structure slots.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = GTUF_Core.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GTUF_Config {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /** Parallel EUt multiplier: actual EUt = recipe EUt × parallels × this. Default 1.0. */
    private static final ForgeConfigSpec.DoubleValue PARALLEL_EUT_MULTIPLIER = BUILDER
            .comment("Parallel EUt Multiplier",
                    "并行能耗倍率：实际 EUt = 配方单次 EUt × 并行数 × 该倍率。",
                    "Default 1.0: energy scales linearly with parallels.",
                    "Set to 0.0 for free parallelism (not recommended).")
            .defineInRange("parallelEutMultiplier", 1.0, 0.0, Double.MAX_VALUE);

    // ---------------------------------------------------------------
    // 可增强电力多方块机（EnhanceableElectricMachine）公式倍率
    // ---------------------------------------------------------------

    /** Parallel base (exponential): max parallel = base parallel × this^(casing tier-1). Default 2.0. */
    private static final ForgeConfigSpec.DoubleValue ELECTRIC_PARALLEL_BASE;
    /** Frame energy step: energy multiplier = 1 - (frame tier-1) × this. Default 0.05. */
    private static final ForgeConfigSpec.DoubleValue ELECTRIC_FRAME_ENERGY_STEP;
    /** Pipe speed step: duration multiplier = 1 - (pipe tier-1) × this. Default 0.1. */
    private static final ForgeConfigSpec.DoubleValue ELECTRIC_PIPE_SPEED_STEP;

    // ---------------------------------------------------------------
    // 可增强蒸汽多方块机（EnhanceableSteamMachine）公式倍率
    // ---------------------------------------------------------------

    /** Parallel base (exponential): max parallel = base parallel × this^(casing tier-1). Default 4.0. */
    private static final ForgeConfigSpec.DoubleValue STEAM_PARALLEL_BASE;
    /** Frame speed step: actual duration = base^(1 - (frame tier-1) × this). Default 0.1. */
    private static final ForgeConfigSpec.DoubleValue STEAM_FRAME_SPEED_STEP;

    // ---------------------------------------------------------------
    // 压力系统（Pressure）
    // ---------------------------------------------------------------

    /** Standard atmospheric pressure (kPa). */
    private static final ForgeConfigSpec.DoubleValue PRESSURE_ATMOSPHERIC;
    /** Chamber pressure relax base rate (kPa/tick), divided by casing tier. */
    private static final ForgeConfigSpec.DoubleValue PRESSURE_RELAX_BASE_RATE_KPA;
    /** Glass min pressure base (kPa): min = max(0.1, base - (tier-1)×step). */
    private static final ForgeConfigSpec.DoubleValue PRESSURE_GLASS_MIN_BASE;
    /** Glass min pressure step (kPa): lowered per glass tier. */
    private static final ForgeConfigSpec.DoubleValue PRESSURE_GLASS_MIN_STEP;
    /** Glass max pressure base (kPa): max = base + (tier-1)×step. */
    private static final ForgeConfigSpec.DoubleValue PRESSURE_GLASS_MAX_BASE;
    /** Glass max pressure step (kPa): raised per glass tier. */
    private static final ForgeConfigSpec.DoubleValue PRESSURE_GLASS_MAX_STEP;
    /** Conduction rate: pressure equalizes toward group mean every 4 ticks. */
    private static final ForgeConfigSpec.DoubleValue PRESSURE_CONDUCTION_RATE;
    /** Pressure pipe tolerance entries ("material=maxKpa:minKpa"). */
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> PRESSURE_PIPE_TOLERANCES_CONFIG;

    // ---------------------------------------------------------------
    // 节能仓（Energy Saving Hatch）
    // ---------------------------------------------------------------

    /** Energy saving extra multiplier: rate = max(min, (100 - 5 × tier diff × this) / 100). */
    private static final ForgeConfigSpec.DoubleValue ENERGY_SAVING_EXTRA_MULTIPLIER;
    /** Energy saving min multiplier floor (prevents 0 or negative EUt). */
    private static final ForgeConfigSpec.DoubleValue ENERGY_SAVING_MIN_MULTIPLIER;

    // ---------------------------------------------------------------
    // 线程仓（threading）
    // ---------------------------------------------------------------

    /** Thread hatch minimum voltage tier. */
    private static final ForgeConfigSpec.IntValue THREAD_HATCH_MIN_TIER;

    // ---------------------------------------------------------------
    // 仓室与原版多方块结构兼容（structureCompat）
    // ---------------------------------------------------------------

    /** Allow thread hatches in vanilla GTM multiblock structure slots (autoAbilities mixin). */
    private static final ForgeConfigSpec.BooleanValue THREAD_HATCH_VANILLA_INJECT;
    /** Allow energy-saving hatches in vanilla GTM multiblock structure slots (autoAbilities mixin). */
    private static final ForgeConfigSpec.BooleanValue ENERGY_SAVING_HATCH_VANILLA_INJECT;

    /** Default pressure pipe tolerances ("material=maxKpa:minKpa", kPa). Material names match GTM registry. */
    public static final List<String> DEFAULT_PRESSURE_PIPE_TOLERANCES = List.of(
            "bronze=3000:10",
            "steel=6000:10",
            "titanium=12000:5",
            "tungsten_steel=24000:5");

    /** Active pipe tolerances (material → properties); refreshed on config load. Pre-filled with defaults. */
    private static final Map<String, PressurePipeProperties> pressurePipeTolerances = parsePressurePipeTolerances(
            DEFAULT_PRESSURE_PIPE_TOLERANCES);

    // ---------------------------------------------------------------
    // 玻璃等级映射（glass tier）
    // ---------------------------------------------------------------

    /** Default glass tier mapping (voltage tier: ULV=1/LV=2/.../UV=9). */
    public static final List<String> DEFAULT_GLASS_TIERS = List.of(
            "minecraft:glass=1",
            "minecraft:tinted_glass=2",
            "gtceu:tempered_glass=3",
            "gtceu:cleanroom_glass=4",
            "gtceu:laminated_glass=5",
            "gtceu:fusion_glass=7");

    /** Config glass tier entries ("registryName=tier"). */
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> GLASS_TIERS_CONFIG;

    /** Active glass tier mapping (block ID → tier); refreshed on config load. Pre-filled with defaults. */
    private static final Map<String, Integer> glassTiers = parseGlassTiers(DEFAULT_GLASS_TIERS);

    static {
        BUILDER.comment(" EnhanceableElectricMachine formula multipliers")
                .push("enhanceableElectric");
        ELECTRIC_PARALLEL_BASE = BUILDER
                .comment("Parallel base (exponential): max = base × this^(casing tier-1).",
                        "Default 2.0: Steel=×1, Al=×2, SS=×4, Ti=×8, TungstenSteel=×16.")
                .defineInRange("parallelBase", 2.0, 1.0, 64.0);
        ELECTRIC_FRAME_ENERGY_STEP = BUILDER
                .comment("Frame energy step: energy mult = 1 - (frame tier-1) × this (floor 0).",
                        "Default 0.05: Steel=100%, Al=95%, SS=90%, Ti=85%, TungstenSteel=80%.")
                .defineInRange("frameEnergyStep", 0.05, 0.0, 1.0);
        ELECTRIC_PIPE_SPEED_STEP = BUILDER
                .comment("Pipe speed step: duration mult = 1 - (pipe tier-1) × this (floor 0).",
                        "Default 0.1: Bronze=100%, Steel=90%, Ti=70%, TungstenSteel=60%.")
                .defineInRange("pipeSpeedStep", 0.1, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.comment(" EnhanceableSteamMachine formula multipliers")
                .push("enhanceableSteam");
        STEAM_PARALLEL_BASE = BUILDER
                .comment("Parallel base (exponential): max = base × this^(casing tier-1).",
                        "Default 4.0: Bronze=×1, Steel=×4.")
                .defineInRange("parallelBase", 4.0, 1.0, 64.0);
        STEAM_FRAME_SPEED_STEP = BUILDER
                .comment("Frame speed step: duration = base^(1 - (frame tier-1) × this).",
                        "Default 0.1: Bronze(1)=base, Steel(2)=base^0.9.")
                .defineInRange("frameSpeedStep", 0.1, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.comment(" Pressure system: unit kPa, 1 atm = 101.325 kPa")
                .push("pressure");
        PRESSURE_ATMOSPHERIC = BUILDER
                .comment("Standard atmospheric pressure (kPa). Chamber pressure relaxes toward this.",
                        "标准大气压（kPa），腔压收敛目标。")
                .defineInRange("atmosphericPressure", 101.325, 0.1, 1.0E9);
        PRESSURE_RELAX_BASE_RATE_KPA = BUILDER
                .comment("Chamber pressure relax base rate (kPa/tick), divided by casing tier.",
                        "Constant absolute rate: drops this amount per tick regardless of deviation,",
                        "clamped to atmospheric. Higher casing tier → slower relax → more stable.")
                .defineInRange("relaxBaseRateKpa", 1.0E-1, 1.0E-6, 1.0E9);
        PRESSURE_GLASS_MIN_BASE = BUILDER
                .comment("Glass min pressure base (kPa): min = max(0.1, base - (glass tier-1) × step).")
                .defineInRange("glassMinBaseKpa", 50.0, 0.1, 1.0E9);
        PRESSURE_GLASS_MIN_STEP = BUILDER
                .comment("Glass min pressure step (kPa): each glass tier lowers the min by this amount.")
                .defineInRange("glassMinStep", 10.0, 0.0, 1.0E9);
        PRESSURE_GLASS_MAX_BASE = BUILDER
                .comment("Glass max pressure base (kPa): max = base + (glass tier-1) × step.")
                .defineInRange("glassMaxBaseKpa", 400.0, 0.1, 1.0E9);
        PRESSURE_GLASS_MAX_STEP = BUILDER
                .comment("Glass max pressure step (kPa): each glass tier raises the max by this amount.")
                .defineInRange("glassMaxStep", 200.0, 0.0, 1.0E9);
        PRESSURE_CONDUCTION_RATE = BUILDER
                .comment("Conduction rate: pressure equalizes toward group mean every 4 ticks.",
                        "p += (group mean - p) × conductionRate.")
                .defineInRange("conductionRate", 0.05, 0.0, 1.0);
        PRESSURE_PIPE_TOLERANCES_CONFIG = BUILDER
                .comment("Pressure pipe tolerances: \"material=maxKpa:minKpa\" list (kPa).",
                        "Material name must match GTM registry (lowercase, e.g. bronze/steel/titanium/tungstensteel).",
                        "Invalid entries are skipped and logged. Pipes burst if pressure exceeds [min,max].")
                .defineListAllowEmpty("pressurePipeTolerances", DEFAULT_PRESSURE_PIPE_TOLERANCES,
                        element -> element instanceof String s && s.contains("="));
        BUILDER.pop();

        BUILDER.comment(" Energy saving hatch")
                .push("energySaving");
        ENERGY_SAVING_EXTRA_MULTIPLIER = BUILDER
                .comment("Extra multiplier: rate = max(min, (100 - 5 × tier diff × this) / 100).",
                        "Tier diff starts at LV=1, MV=2, HV=3, EV=4...",
                        "Default 1.0: LV=95%, MV=90%, HV=85%, EV=80% (-5% per tier).",
                        "Increase to boost high-tier discount (e.g. 2.0: LV=90%, MV=80%...).")
                .defineInRange("extraMultiplier", 1.0, 0.0, Double.MAX_VALUE);
        ENERGY_SAVING_MIN_MULTIPLIER = BUILDER
                .comment("Energy rate floor (prevents 0 or negative EUt).",
                        "Default 0.05 = 5% (recipes can never be fully free).")
                .defineInRange("minMultiplier", 0.05, 0.01, 1.0);
        BUILDER.pop();

        BUILDER.comment(" Thread hatch")
                .push("threading");
        THREAD_HATCH_MIN_TIER = BUILDER
                .comment("Minimum voltage tier for thread hatches.",
                        "Default: 6 = LuV. Thread hatches below this tier cannot be crafted.",
                        "Tier values: 1=ULV, 2=LV, 3=MV, 4=HV, 5=EV, 6=LuV, 7=UV, 8=UHV, 9=UEV, 10=UIV, 11=UXV, 12=OpV, 13=MAX.")
                .defineInRange("minTier", 6, 1, 13);
        BUILDER.pop();

        BUILDER.comment(" Structure compatibility with vanilla GTM multiblocks (mixin injection)")
                .push("structureCompat");
        THREAD_HATCH_VANILLA_INJECT = BUILDER
                .comment("Allow thread hatches in vanilla GTM multiblock structure slots (autoAbilities mixin).",
                        "When on, thread hatches can fill any autoAbilities-defined slot in GTM electric multis",
                        "(incl. vanilla machines like Large Assembler). Off: only GTUF multis that explicitly",
                        "declare the slot. Structure slots are locked on first formation; restart to apply.")
                .define("threadHatchVanillaInjection", false);
        ENERGY_SAVING_HATCH_VANILLA_INJECT = BUILDER
                .comment("Allow energy-saving hatches in vanilla GTM multiblock structure slots (autoAbilities mixin).",
                        "When on, energy-saving hatches can fill any autoAbilities-defined slot in GTM electric multis.",
                        "Off: only GTUF multis that explicitly declare the slot.",
                        "Structure slots are locked on first formation; restart to apply.")
                .define("energySavingHatchVanillaInjection", false);
        BUILDER.pop();

        BUILDER.comment(" Glass tier mapping")
                .push("glass");
        GLASS_TIERS_CONFIG = BUILDER
                .comment("Glass tier mapping: \"registryName=tier\" list. Tier = voltage tier (ULV=1/LV=2/.../UV=9).",
                        "Defaults: 6 built-in glasses. Add other mod glasses (e.g. \"chisel:glass=1\"),",
                        "modify or remove entries to change/disable. Invalid entries skipped and logged.",
                        "Out-of-range tier (>9) in tooltip gracefully hides the voltage name.")
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
    private static int threadHatchMinTier = 6;
    private static boolean threadHatchVanillaInjection = true;
    private static boolean energySavingHatchVanillaInjection = true;

    private GTUF_Config() {}

    /** Current parallel EUt multiplier. */
    public static double getParallelEutMultiplier() {
        return parallelEutMultiplier;
    }

    /** Electric machine parallel base (exponential). */
    public static double getElectricParallelBase() {
        return electricParallelBase;
    }

    /** Electric machine frame energy step. */
    public static double getElectricFrameEnergyStep() {
        return electricFrameEnergyStep;
    }

    /** Electric machine pipe speed step. */
    public static double getElectricPipeSpeedStep() {
        return electricPipeSpeedStep;
    }

    /** Steam machine parallel base (exponential). */
    public static double getSteamParallelBase() {
        return steamParallelBase;
    }

    /** Steam machine frame speed step. */
    public static double getSteamFrameSpeedStep() {
        return steamFrameSpeedStep;
    }

    /** Atmospheric pressure (kPa); chamber pressure relaxes toward this. */
    public static double getPressureAtmospheric() {
        return pressureAtmospheric;
    }

    /** Chamber pressure relax base rate (kPa/tick), divided by casing tier. */
    public static double getPressureRelaxBaseRateKpa() {
        return pressureRelaxBaseRateKpa;
    }

    /** Glass min pressure base (kPa). */
    public static double getPressureGlassMinBaseKpa() {
        return pressureGlassMinBaseKpa;
    }

    /** Glass min pressure step (kPa). */
    public static double getPressureGlassMinStep() {
        return pressureGlassMinStep;
    }

    /** Glass max pressure base (kPa). */
    public static double getPressureGlassMaxBaseKpa() {
        return pressureGlassMaxBaseKpa;
    }

    /** Glass max pressure step (kPa). */
    public static double getPressureGlassMaxStep() {
        return pressureGlassMaxStep;
    }

    /** Conduction rate: equalizes toward group mean every 4 ticks. */
    public static double getPressureConductionRate() {
        return pressureConductionRate;
    }

    /** Energy saving extra multiplier. */
    public static double getEnergySavingExtraMultiplier() {
        return energySavingExtraMultiplier;
    }

    /** Energy saving min multiplier floor. */
    public static double getEnergySavingMinMultiplier() {
        return energySavingMinMultiplier;
    }

    /** Thread hatch minimum voltage tier. */
    public static int getThreadHatchMinTier() {
        return threadHatchMinTier;
    }

    /** Whether thread hatches can fill vanilla GTM multiblock slots. */
    public static boolean isThreadHatchVanillaInjection() {
        return threadHatchVanillaInjection;
    }

    /** Whether energy-saving hatches can fill vanilla GTM multiblock slots. */
    public static boolean isEnergySavingHatchVanillaInjection() {
        return energySavingHatchVanillaInjection;
    }

    /** Query pipe tolerance for a material name (lowercase); null if not configured. */
    @Nullable
    public static PressurePipeProperties getPressurePipeTolerance(String materialName) {
        return pressurePipeTolerances.get(materialName);
    }

    /**
     * Active pipe tolerance map (material → properties). Shared instance, refreshed on config reload.
     * Runtime reload does NOT retroactively update already-attached material properties; restart required.
     */
    public static Map<String, PressurePipeProperties> getPressurePipeTolerances() {
        return pressurePipeTolerances;
    }

    /**
     * Active glass tier map (block ID → tier). Shared instance, refreshed on config reload.
     * Holders (e.g. GTUF_PatternPredicates.GLASS_TIERS) see updates without re-fetching.
     */
    public static Map<String, Integer> getGlassTiers() {
        return glassTiers;
    }

    /** Query glass tier for a block registry name; null if not in mapping. */
    @Nullable
    public static Integer getGlassTier(String blockId) {
        return glassTiers.get(blockId);
    }

    /** Config spec, registered by GTUF_Core during construction. */
    public static ForgeConfigSpec spec() {
        return SPEC;
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // Covers both initial load and runtime reload.
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
            threadHatchMinTier = THREAD_HATCH_MIN_TIER.get();
            threadHatchVanillaInjection = THREAD_HATCH_VANILLA_INJECT.get();
            energySavingHatchVanillaInjection = ENERGY_SAVING_HATCH_VANILLA_INJECT.get();
            pressurePipeTolerances.clear();
            pressurePipeTolerances.putAll(parsePressurePipeTolerances(PRESSURE_PIPE_TOLERANCES_CONFIG.get()));
            glassTiers.clear();
            glassTiers.putAll(parseGlassTiers(GLASS_TIERS_CONFIG.get()));
        }
    }

    /** Parse "registryName=tier" entries into a map. Invalid entries are skipped and logged. */
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
     * Parse "material=maxKpa:minKpa" entries into a map. Invalid entries skipped and logged.
     * Validation: max > 0, min >= 0, min < max.
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
