package com.liangqu.gtuf.api.machine;

import com.liangqu.gtuf.common.machine.trait.GTUFThreadingLogic;
import org.jetbrains.annotations.Nullable;

/**
 * 线程化配方逻辑标记接口：由 {@code GTUFRecipeLogicMixin} 注入到所有 GTM
 * {@code com.gregtechceu.gtceu.api.machine.trait.RecipeLogic} 实例上。
 *
 * <p>
 * 消费方（控制器显示、Jade provider）拿到 RecipeLogic 后强转本接口即可安全
 * 获取线程化核心 {@link GTUFThreadingLogic}；无线程仓、未进入线程模式的机器
 * {@link #getThreadingLogic()} 返回 null（无线程仓时机器行为与原生 GTM 完全一致）。
 * </p>
 */
public interface IThreadingRecipeLogic {

    /**
     * @return 线程化多槽核心，无线程仓（未进入线程模式）时为 null
     */
    @Nullable
    GTUFThreadingLogic getThreadingLogic();
}
