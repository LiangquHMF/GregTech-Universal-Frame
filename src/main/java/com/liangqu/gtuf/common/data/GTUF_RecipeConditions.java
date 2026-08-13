package com.liangqu.gtuf.common.data;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.liangqu.gtuf.api.recipe.condition.PressureCondition;

/**
 * GTUF 自定义配方条件注册表。
 *
 * <p>
 * GTM 在 {@code GTRecipeConditions.init()}（mod 总线 enqueueWork）里对
 * {@link GTRegistries#RECIPE_CONDITIONS} 抛 {@code GTCEuAPI.RegisterEvent} 后冻结；
 * 各 addon 需在 MOD 总线监听该事件（GenericEvent&lt;RecipeConditionType&gt;）期间注册。
 * 本类仅在该事件回调内调用 {@link #init}，PRESSURE 在此前为 null。
 * </p>
 */
public final class GTUF_RecipeConditions {

    private GTUF_RecipeConditions() {}

    /** 压力区间条件类型（gtuf_pressure）。 */
    public static final RecipeConditionType<PressureCondition> PRESSURE = new RecipeConditionType<>(
            PressureCondition::new, PressureCondition.CODEC);

    public static void init(GTCEuAPI.RegisterEvent<java.lang.String, RecipeConditionType<?>> event) {
        event.register("gtuf_pressure", PRESSURE);
    }
}
