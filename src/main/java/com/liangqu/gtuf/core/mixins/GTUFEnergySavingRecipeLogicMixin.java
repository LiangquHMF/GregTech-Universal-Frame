package com.liangqu.gtuf.core.mixins;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import com.liangqu.gtuf.common.machine.trait.GTUFEnergySavingRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 节能仓推广核心：给 GTM 所有电力多方块共用的 {@link RecipeLogic} 的配方修改结果
 * 追加一层能耗减免。
 *
 * <p>
 * 注入点：{@code RecipeLogic} 中两处 {@code machine.fullModifyRecipe(...)} 调用
 * （{@code checkMatchedRecipeAvailable} 的开始门控、{@code onRecipeFinish} 的下轮
 * 配方预计算）。{@code @Redirect} 拦截调用：先让机器自身 recipeModifier 正常执行，
 * 再查 {@link GTUFEnergySavingRegistry}——若控制器挂了节能仓，把返回配方的 EUt 乘上
 * 最优倍率（{@code ModifierFunction.eutMultiplier}）。因此减免作用于<b>最终</b> EUt
 * （含并行/OC 之后的实际消耗），且对任意电力多方块（含 GTM 原生机器）生效。
 * </p>
 *
 * <p>
 * 无节能仓时<b>零行为改变</b>：仅多一次 {@code instanceof IMultiController + 查表}
 * （O(1)），未命中即放行原逻辑。结构失效后仓室注销登记 → 自动退回原能耗。
 * </p>
 *
 * <p>
 * 目标 {@code RecipeLogic} 是 GTM mod 类（非原版、无 SRG 映射），故 {@code @Redirect}
 * 的 invoke 目标均 {@code remap = false}（dev 与正式包类名一致）。
 * </p>
 */
@Mixin(RecipeLogic.class)
public abstract class GTUFEnergySavingRecipeLogicMixin {

    @Redirect(method = "checkMatchedRecipeAvailable",
              at = @At(value = "INVOKE",
                       target = "Lcom/gregtechceu/gtceu/api/machine/feature/IRecipeLogicMachine;" +
                               "fullModifyRecipe(Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;)" +
                               "Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;",
                       remap = false),
              remap = false)
    private GTRecipe gtuf$applyEnergySaving(IRecipeLogicMachine machine, GTRecipe match) {
        GTRecipe modified = machine.fullModifyRecipe(match);
        if (modified == null) return null;
        if (machine.self() instanceof IMultiController controller) {
            return GTUFEnergySavingRegistry.applyMultiplier(controller, modified);
        }
        return modified;
    }

    @Redirect(method = "onRecipeFinish",
              at = @At(value = "INVOKE",
                       target = "Lcom/gregtechceu/gtceu/api/machine/feature/IRecipeLogicMachine;" +
                               "fullModifyRecipe(Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;)" +
                               "Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;",
                       remap = false),
              remap = false)
    private GTRecipe gtuf$applyEnergySavingOnFinish(IRecipeLogicMachine machine, GTRecipe recipe) {
        GTRecipe modified = machine.fullModifyRecipe(recipe);
        if (modified == null) return null;
        if (machine.self() instanceof IMultiController controller) {
            return GTUFEnergySavingRegistry.applyMultiplier(controller, modified);
        }
        return modified;
    }
}
