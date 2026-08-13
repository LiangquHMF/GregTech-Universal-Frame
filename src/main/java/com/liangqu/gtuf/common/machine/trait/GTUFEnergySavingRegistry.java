package com.liangqu.gtuf.common.machine.trait;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;

import com.liangqu.gtuf.common.machine.multiblock.part.EnergySavingHatchPartMachine;

/**
 * 节能仓应用入口：由 {@code GTUFEnergySavingRecipeLogicMixin} 在配方修改结果上追加能耗减免。
 *
 * <p>
 * <b>权威判定而非注册表</b>：直接扫描 {@link IMultiController#getParts()}——这是 GTM 维护的
 * 当前真实结构部件列表，任何装在结构里的节能仓（含 GTM 原生机器经
 * {@code GTUFPredicatesMixin} 注入的能力位）都在其中。不依赖
 * {@code addedToController}/{@code removedFromController} 回调的注册/注销时序，因此
 * <b>拆除节能仓后立即失效</b>：拆块瞬间部件 {@code isInValid()} 变 true（BlockEntity 已移除），
 * 即使控制器尚未重检结构、parts 列表仍残留旧部件，也会被过滤掉。
 * </p>
 *
 * <p>
 * 一个控制器可装多个节能仓；多仓共存取<b>最优</b>（减免最大、能耗倍率最小者）。
 * 无节能仓、或倍率 = 1 时配方原样返回（零行为改变）。
 * </p>
 *
 * <p>
 * 仅服务端逻辑访问（配方修改），单线程模型下无需加锁。
 * </p>
 */
public class GTUFEnergySavingRegistry {

    /**
     * 若控制器挂了节能仓，把能耗减免倍率应用到配方：{@code < 1} 时经
     * {@code ModifierFunction.eutMultiplier} 缩放配方 EUt（作用于并行/OC 之后的实际消耗）；
     * 无节能仓、倍率 = 1、或配方无 EU 内容（如锅炉/零 EUt 配方——应用减免只会给配方塞一个
     * 空 EU 内容）时原样返回。
     */
    public static GTRecipe applyMultiplier(IMultiController controller, GTRecipe recipe) {
        double best = 1.0;
        for (IMultiPart part : controller.getParts()) {
            if (part instanceof EnergySavingHatchPartMachine hatch && !part.self().isInValid()) {
                best = Math.min(best, hatch.getEnergyMultiplier());
            }
        }
        if (best >= 1.0) return recipe;
        if (recipe.getInputEUt().isEmpty() && recipe.getOutputEUt().isEmpty()) return recipe;
        return ModifierFunction.builder().eutMultiplier(best).build().apply(recipe);
    }
}
