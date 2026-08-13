package com.liangqu.gtuf.api.machine;

/**
 * 压力机器接口：暴露腔体压力与结构等级，供 {@code PressureCondition} 与配方门控读取。
 *
 * <p>
 * 实现类需持有 {@code chamberPressure}（kPa）以及成型时从 {@code getMatchContext()}
 * 读到的 {@code casingTier}/{@code glassTier}。压力只存于机器腔体，管道与压力仓仅传导。
 * </p>
 */
public interface IPressureMachine {

    /** 当前腔体压力（kPa）。 */
    double getPressure();

    /** 玻璃等级决定的腔体最低压力（kPa），低于则配方停机等待。 */
    double getPressureMin();

    /** 玻璃等级决定的腔体最高压力（kPa），高于则配方停机等待。 */
    double getPressureMax();

    /** 外壳等级（UniversalCasingTier），决定腔内压力向大气回归的速率。 */
    int getCasingTier();

    /** 玻璃等级（GlassTier），决定腔体压力上下限。 */
    int getGlassTier();
}
