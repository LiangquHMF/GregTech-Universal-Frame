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
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * 配方压力区间条件（对应 {@code .RequiredP(min, max)}）—— 7.5.3 兼容变体。
 *
 * <p>
 * 目标机器腔压必须在 {@code [min, max]}（kPa）内配方才运行：{@link RecipeLogic#checkRecipe}
 * 负责开始门控；{@link RecipeLogic#handleRecipeWorking} 逐 tick 复查，超区间时
 * {@code setWaiting} 停机等待，压力回到区间后自动恢复。非 {@link IPressureMachine}
 * 机器一律放行（条件不生效）。
 * </p>
 *
 * <p>
 * 7.5.3 的 {@code RecipeCondition} 改为自引用泛型 {@code RecipeCondition<T>}，序列化经
 * {@code RecipeConditionType} 的 Codec（{@code serialize}/{@code deserialize}/
 * {@code toNetwork}/{@code fromNetwork} 已 final/static，不可覆写）——本类实现 4 个抽象方法
 * 并用 {@link #CODEC} 序列化 {@code min/max/isReverse} 三字段。
 * </p>
 */
@NoArgsConstructor
public class PressureCondition extends RecipeCondition<PressureCondition> {

    public static final Codec<PressureCondition> CODEC = RecordCodecBuilder.create(instance -> RecipeCondition
            .isReverse(instance)
            .and(instance.group(
                    Codec.DOUBLE.fieldOf("min").forGetter(val -> val.min),
                    Codec.DOUBLE.fieldOf("max").forGetter(val -> val.max)))
            .apply(instance, PressureCondition::new));

    private double min;
    private double max;

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
