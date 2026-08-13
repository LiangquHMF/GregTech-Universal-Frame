package com.liangqu.gtuf.core.mixins;

import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import com.liangqu.gtuf.api.pressure.GTUF_Pressure;
import com.liangqu.gtuf.api.recipe.IGTUFRecipeBuilder;
import com.liangqu.gtuf.api.recipe.condition.PressureCondition;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 往 GTM {@link GTRecipeBuilder} 注入压力配方属性方法（Java 配方 builder API）。
 *
 * <p>
 * 通过实现 {@link IGTUFRecipeBuilder} 接口把 {@code Pressure/RequiredP} 以<b>原名</b>
 * 合并进目标类（接口方法不受 {@code @Unique} 的 {@code $} 前缀改名影响），Java 侧
 * {@code builder.Pressure(50)} 可直接链式调用。
 * </p>
 *
 * <p>
 * 注意：KubeJS 的配方 builder 是 {@code GTRecipeSchema$GTRecipeJS}，不经过本 mixin；
 * 脚本里用 GTRecipeJS 原生 {@code .addData("gtuf_pressure_produce", kpa)} 与
 * {@code .addCondition(new PressureCondition(min, max))}。
 * </p>
 *
 * <p>
 * 目标类是 GTM mod 类（不混淆、dev 与正式包名一致），故无需 remap 标志。
 * mixin 类不继承目标类，调用目标方法用 {@code ((GTRecipeBuilder)(Object)this)} 强转。
 * </p>
 */
@Mixin(GTRecipeBuilder.class)
public abstract class GTUFRecipeBuilderMixin implements IGTUFRecipeBuilder {

    @Override
    public GTRecipeBuilder Pressure(double kpa) {
        if (kpa == 0) {
            throw new IllegalArgumentException("Pressure produce amount must be non-zero, got 0 kPa");
        }
        // 有符号：正数=加压机加压（腔压上升，钳制到玻璃上限），负数=抽压机抽压（下降，钳制到玻璃下限）。
        return ((GTRecipeBuilder) (Object) this).addData(GTUF_Pressure.Keys.PRODUCE, (float) kpa);
    }

    @Override
    public GTRecipeBuilder RequiredP(double min, double max) {
        return ((GTRecipeBuilder) (Object) this).addCondition(new PressureCondition(min, max));
    }
}
