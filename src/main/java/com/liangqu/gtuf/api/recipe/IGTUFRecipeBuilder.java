package com.liangqu.gtuf.api.recipe;

import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

/**
 * GTUF 注入 GTM {@link GTRecipeBuilder} 的压力配方 API（由
 * {@code GTUFRecipeBuilderMixin} 实现）。
 *
 * <p>
 * 用接口实现而不是裸 {@code @Unique} 方法：Mixin 0.8.2+ 会把 {@code @Unique} 成员在
 * 目标类中重命名为带 {@code $} 前缀的名字（{@code Pressure} → {@code $Pressure}），
 * 外部（Java 与 Rhino）按原名查不到。实现接口的方法保持原名合并进目标类，Java 侧
 * 直接 {@code builder.Pressure(50)} 即可链式调用。
 * </p>
 *
 * <p>
 * 注意：此接口只覆盖 <b>Java 配方 builder</b>。KubeJS 的配方 builder 是 GTM
 * {@code GTRecipeSchema$GTRecipeJS}（非 GTRecipeBuilder），脚本里用其原生
 * {@code .addData("gtuf_pressure_produce", kpa)} 与
 * {@code .addCondition(new PressureCondition(min, max))}。
 * </p>
 *
 * @see com.liangqu.gtuf.api.recipe.condition.PressureCondition
 */
public interface IGTUFRecipeBuilder {

    /**
     * 设置配方压力产生量（kPa，仅正数）。
     *
     * @param kpa 产生量；必须 {@code > 0}（压力只能产生，不存在消耗）。
     * @return this，可链式调用
     */
    GTRecipeBuilder Pressure(double kpa);

    /**
     * 设置配方运行所需的压力区间（kPa），腔压不处于 {@code [min, max]} 配方不运行。
     *
     * @param min 最低所需压力（kPa）
     * @param max 最高所需压力（kPa）
     * @return this，可链式调用
     */
    GTRecipeBuilder RequiredP(double min, double max);
}
