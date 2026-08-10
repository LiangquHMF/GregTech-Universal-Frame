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
 * 目前仅一个配方平衡项：并行能耗倍率。机器做并行放大时把 EUt 乘上并行数（能耗随并行线性增长、
 * 单件蒸汽/EU 成本恒定），该倍率供整合包作者整体调节并行带来的能耗开销——
 * 例如调低让并行更省汽、或设为 0 让并行不增耗能。
 * </p>
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

    private static final ForgeConfigSpec SPEC = BUILDER.build();

    private static double parallelEutMultiplier = 1.0;

    private GTUF_Config() {}

    /** 当前并行能耗倍率（供配方处理逻辑读取）。 */
    public static double getParallelEutMultiplier() {
        return parallelEutMultiplier;
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
        }
    }
}
