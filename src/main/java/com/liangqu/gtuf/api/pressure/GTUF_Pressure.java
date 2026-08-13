package com.liangqu.gtuf.api.pressure;

import com.liangqu.gtuf.config.GTUF_Config;

import java.util.Locale;

/**
 * GTUF 压力系统核心工具类。
 *
 * <p>
 * 压力单位统一为 <b>kPa</b>（配方 API、腔体存储、管道容限全部使用 kPa）；
 * 1 标准大气压 = {@value #ATMOSPHERIC_KPA} kPa（可从 config {@code [pressure].atmosphericPressure}
 * 读取）。压力只存于机器腔体，管道与压力仓只负责传导，不存储。
 * </p>
 *
 * <p>
 * {@link #Keys} 集中定义配方 data 的 NBT 键名，避免魔法字符串散落各处。
 * </p>
 */
public final class GTUF_Pressure {

    private GTUF_Pressure() {}

    /** 配方 data 键名集合（写入 {@code GTRecipe.data} / {@code GTRecipeBuilder.data}）。 */
    public static final class Keys {

        private Keys() {}

        /** 配方压力产生量（kPa，有符号：正数=加压机加压，负数=抽压机抽压；0 无意义）。读 {@code GTRecipe.data}。 */
        public static final String PRODUCE = "gtuf_pressure_produce";
    }

    /** 标准大气压（kPa），从 config {@code [pressure].atmosphericPressure} 读取。 */
    public static double atmosphericKpa() {
        return GTUF_Config.getPressureAtmospheric();
    }

    /** 1 Pa = 0.001 kPa。 */
    public static final double KPA_PER_PA = 0.001;
    /** 1 MPa = 1000 kPa。 */
    public static final double KPA_PER_MPA = 1000.0;
    /** 1 GPa = 1_000_000 kPa。 */
    public static final double KPA_PER_GPA = 1_000_000.0;

    /**
     * 把 kPa 值格式化为自适应单位字符串：{@code <1 kPa} 用 Pa，{@code <1000} 用 kPa，
     * {@code <1_000_000} 用 MPa，其余用 GPa。保留 2~3 位有效小数。
     *
     * @param kpa 压力值（kPa）
     * @return 如 {@code "101.325 kPa"}、{@code "2.5 MPa"}、{@code "0.05 GPa"}
     */
    public static String format(double kpa) {
        if (Math.abs(kpa) < 1.0) {
            return trim(paToKpa(kpa)) + " Pa";
        }
        if (Math.abs(kpa) < KPA_PER_MPA) {
            return trim(kpa) + " kPa";
        }
        if (Math.abs(kpa) < KPA_PER_GPA) {
            return trim(kpa / KPA_PER_MPA) + " MPa";
        }
        return trim(kpa / KPA_PER_GPA) + " GPa";
    }

    private static double paToKpa(double kpa) {
        return kpa / KPA_PER_PA;
    }

    private static String trim(double value) {
        long rounded = Math.round(value * 1000.0);
        double trimmed = rounded / 1000.0;
        return String.format(Locale.ROOT, "%.3f", trimmed).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
