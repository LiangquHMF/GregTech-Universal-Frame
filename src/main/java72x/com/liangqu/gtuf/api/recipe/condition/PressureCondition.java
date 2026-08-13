package com.liangqu.gtuf.api.recipe.condition;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;

import net.minecraft.network.chat.Component;

import com.liangqu.gtuf.api.machine.IPressureMachine;
import com.liangqu.gtuf.api.pressure.GTUF_Pressure;
import com.liangqu.gtuf.common.data.GTUF_RecipeConditions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * 配方压力区间条件（对应 {@code .RequiredP(min, max)}）—— 7.2.1~7.4.1 兼容变体。
 *
 * <p>
 * 目标机器腔压必须在 {@code [min, max]}（kPa）内配方才运行：{@link RecipeLogic#checkRecipe}
 * 负责开始门控；{@link RecipeLogic#handleRecipeWorking} 逐 tick 复查，超区间时
 * {@code setWaiting} 停机等待，压力回到区间后自动恢复。非 {@link IPressureMachine}
 * 机器一律放行（条件不生效）。
 * </p>
 *
 * <p>
 * <b>版本差异（7.2.1~7.4.1 形态）：</b>该区间 GTM 已把 {@code serialize/deserialize/
 * toNetwork/fromNetwork} 改为 final/static（序列化全面走 {@code RecipeConditionType} 的
 * Codec），本类<b>不再覆写</b>那四个方法，min/max/isReverse 全部经 {@link #CODEC}
 * 序列化（JSON 与网络同步同一路径）。{@code RecipeCondition} 本类未泛型化，故这里
 * {@code extends RecipeCondition}（raw）。7.1.4 的可覆写形态见 {@code src/main/java714}；
 * 7.5.3 的泛型形态见 {@code src/main/java75}（由 build.gradle 按 {@code gtm_version} 切换）。
 * </p>
 */
public class PressureCondition extends RecipeCondition {

    public static final Codec<PressureCondition> CODEC = RecordCodecBuilder.create(instance -> RecipeCondition
            .isReverse(instance)
            .and(instance.group(
                    Codec.DOUBLE.fieldOf("min").forGetter(val -> val.min),
                    Codec.DOUBLE.fieldOf("max").forGetter(val -> val.max)))
            .apply(instance, PressureCondition::new));

    private double min;
    private double max;

    public PressureCondition() {}

    public PressureCondition(double min, double max) {
        this.min = min;
        this.max = max;
    }

    public PressureCondition(boolean isReverse, double min, double max) {
        super(isReverse);
        this.min = min;
        this.max = max;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    @Override
    public RecipeConditionType<PressureCondition> getType() {
        return GTUF_RecipeConditions.PRESSURE;
    }

    @Override
    public String getTranslationKey() {
        return "gtuf.recipe.condition.pressure";
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("gtuf.recipe.condition.pressure", GTUF_Pressure.format(this.min),
                GTUF_Pressure.format(this.max));
    }

    @Override
    protected boolean testCondition(@NotNull GTRecipe recipe, @NotNull RecipeLogic recipeLogic) {
        if (recipeLogic.machine.self() instanceof IPressureMachine pressureMachine) {
            double pressure = pressureMachine.getPressure();
            return pressure >= this.min && pressure <= this.max;
        }
        return true;
    }

    @Override
    public PressureCondition createTemplate() {
        return new PressureCondition();
    }
}
