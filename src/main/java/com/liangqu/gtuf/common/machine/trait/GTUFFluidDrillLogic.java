package com.liangqu.gtuf.common.machine.trait;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.common.machine.trait.FluidDrillLogic;

import com.liangqu.gtuf.common.machine.multiblock.electric.EnhancedFluidDrillMachine;
import org.jetbrains.annotations.NotNull;

/**
 * 增强钻井机逻辑（GTUF 版）：在 GTM {@code FluidDrillLogic} 之上把创建期可配置的
 * 并行数/时长倍率/能耗倍率落到钻井配方上。
 *
 * <p>
 * <b>实现方式：继承 {@code FluidDrillLogic}，配方变换收口在 {@link #setupRecipe}。</b>
 * 原生 {@code FluidDrillLogic} 每次钻井循环由私有的 {@code getFluidDrillRecipe()} 现场生成
 * 一条基础配方（duration = {@code MAX_PROGRESS}=20、EUt = {@code VA[energyTier]}、输出 =
 * {@code getFluidToProduce()} 的矿脉流体），随后经 {@code setupRecipe} 交给
 * {@code RecipeLogic}。此处覆写 {@code setupRecipe}：在交给基类前把基础配方用
 * {@code ModifierFunction} 变换——并行数放大流体输出（{@code modifyAllContents(P)}）与能耗
 * （{@code eutMultiplier(P×E)}），时长倍率缩放 duration（{@code durationMultiplier(D)}）。
 * 因并行/倍率是创建期配置、每轮配方都重新生成，无需复制整类。
 * </p>
 *
 * <p>
 * <b>GUI 一致性：</b>覆写 {@link #getFluidToProduce()} 让控制器 GUI 显示的产液量同样按
 * 并行数放大。注意该无参方法只被 GUI/显示调用；配方生成走私有的
 * {@code getFluidToProduce(FluidVeinWorldEntry)} 重载（不受覆写影响，仍为基准量），由
 * {@code setupRecipe} 变换统一放大——两条路径结果一致（均为 ×并行），不会双重叠加。
 * </p>
 *
 * <p>
 * 能耗倍率与并行相乘（{@code P × E}）：并行放大的能量需求 × 用户倍率。时长倍率 &lt;1 = 更快
 * （每份作业 ticks 更少、单位时间产出更多），与配方倍率约定一致。
 * </p>
 */
public class GTUFFluidDrillLogic extends FluidDrillLogic {

    public GTUFFluidDrillLogic(EnhancedFluidDrillMachine machine) {
        super(machine);
    }

    @NotNull
    @Override
    public EnhancedFluidDrillMachine getMachine() {
        return (EnhancedFluidDrillMachine) super.getMachine();
    }

    /**
     * GUI/显示用产液量：按并行数放大（与 {@code setupRecipe} 的配方变换一致）。
     */
    @Override
    public int getFluidToProduce() {
        return super.getFluidToProduce() * getMachine().getMaxParallel();
    }

    /**
     * 把创建期并行/时长/能耗倍率变换到刚生成的基础钻井配方上，再交给基类 {@code RecipeLogic}。
     */
    @Override
    public void setupRecipe(GTRecipe recipe) {
        GTRecipe modified = buildModifier().apply(recipe);
        super.setupRecipe(modified != null ? modified : recipe);
    }

    private ModifierFunction buildModifier() {
        int parallel = getMachine().getMaxParallel();
        return ModifierFunction.builder()
                // 并行：放大非 EU 内容（流体输出）——EU 由 eutMultiplier 单独处理，不重复
                .modifyAllContents(ContentModifier.multiplier(parallel))
                // 能耗：并行份数 × 能耗倍率
                .eutMultiplier(parallel * getMachine().getEnergyMultiplier())
                // 时长：直接乘用户倍率（<1 = 更快）
                .durationMultiplier(getMachine().getDurationMultiplier())
                .parallels(parallel)
                .build();
    }
}
