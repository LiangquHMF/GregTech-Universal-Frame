package com.liangqu.gtuf.api.recipe.condition;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import com.google.gson.JsonObject;
import com.liangqu.gtuf.api.machine.IPressureMachine;
import com.liangqu.gtuf.api.pressure.GTUF_Pressure;
import com.liangqu.gtuf.common.data.GTUF_RecipeConditions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * 配方压力区间条件（对应 {@code .RequiredP(min, max)}）—— 7.1.4 独有兼容变体。
 *
 * <p>
 * 目标机器腔压必须在 {@code [min, max]}（kPa）内配方才运行：{@link RecipeLogic#checkRecipe}
 * 负责开始门控；{@link RecipeLogic#handleRecipeWorking} 逐 tick 复查，超区间时
 * {@code setWaiting} 停机等待，压力回到区间后自动恢复。非 {@link IPressureMachine}
 * 机器一律放行（条件不生效）。
 * </p>
 *
 * <p>
 * <b>版本差异（7.1.4 独有形态）：</b>GTM 7.1.4 的 {@code RecipeCondition} 非泛型，
 * {@code serialize/deserialize/toNetwork/fromNetwork} 为<b>可覆写</b>的具体方法，且
 * {@code GTRecipeSerializer} 的网络同步路径（conditionReader/conditionWriter）确实走
 * {@code toNetwork/fromNetwork}——若不覆写，{@code min/max} 会丢失。本类覆写补齐两字段。
 * 7.2.1 起这四个方法改为 final/static（序列化全面 Codec 化），不再可覆写；对应变体见
 * {@code src/main/java72x}（7.2.1~7.4.1）与 {@code src/main/java75}（7.5.3 泛型），
 * 由 build.gradle 按 {@code gtm_version} 切换源码目录。
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

    //////// 7.1.4 序列化：基类 serialize/deserialize/toNetwork/fromNetwork 只处理 isReverse，
    //////// 这里覆写补齐 min/max。7.5.3 起为 final/static，此组覆写只在 714 变体存在。//////

    @Override
    public JsonObject serialize() {
        JsonObject json = super.serialize();
        json.addProperty("min", min);
        json.addProperty("max", max);
        return json;
    }

    @Override
    public RecipeCondition deserialize(JsonObject json) {
        super.deserialize(json);
        this.min = json.get("min").getAsDouble();
        this.max = json.get("max").getAsDouble();
        return this;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        super.toNetwork(buf);
        buf.writeDouble(min);
        buf.writeDouble(max);
    }

    @Override
    public RecipeCondition fromNetwork(FriendlyByteBuf buf) {
        super.fromNetwork(buf);
        this.min = buf.readDouble();
        this.max = buf.readDouble();
        return this;
    }
}
