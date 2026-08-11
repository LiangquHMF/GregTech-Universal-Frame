package com.liangqu.gtuf.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

public class TierElectricParallelMachine extends WorkableElectricMultiblockMachine {

    /** 默认并行倍率 = 2（历史行为：最大并行数 = 2 × 最大电压等级）。 */
    public static final int DEFAULT_PARALLEL_MULTIPLIER = 2;

    /** 并行倍率：最大并行数 = 倍率 × 最大电压等级。KubeJS 注册时经构造器传入。 */
    private final int parallelMultiplier;

    /**
     * 默认构造：倍率取 {@link #DEFAULT_PARALLEL_MULTIPLIER}。
     * 供 testmod 的 {@code TierElectricParallelMachine::new} 等方法引用使用。
     */
    public TierElectricParallelMachine(IMachineBlockEntity holder, Object... args) {
        this(holder, DEFAULT_PARALLEL_MULTIPLIER, args);
    }

    /**
     * KubeJS 注册多方块结构时指定初始并行倍率（{@code Builder(holder, 倍率)}）：
     * {@code .machine((holder) => new TierElectricParallelMachine(holder, 倍率))}。
     *
     * @param parallelMultiplier 并行倍率（最大并行数 = 倍率 × 最大电压等级），下限 1
     */
    public TierElectricParallelMachine(IMachineBlockEntity holder, int parallelMultiplier, Object... args) {
        super(holder, args);
        this.parallelMultiplier = Math.max(1, parallelMultiplier);
    }

    /**
     * 最大并行数 = 倍率 × 最大电压等级。倍率由构造器传入（KubeJS 注册时指定），默认 2。
     * LV(1)=2, MV(2)=4, HV(3)=6, EV(4)=8, IV(5)=10……（倍率=2 时）。
     */
    public int getMaxParallel() {
        int tier = GTUtil.getTierByVoltage(getMaxVoltage());
        return Math.max(1, parallelMultiplier * tier);
    }

    /**
     * 配方修改器：先应用电压并行，再完美过时钟。
     * 注册时用 .recipeModifier(TierElectricParallelMachine::recipeModifier) 挂载。
     */
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof TierElectricParallelMachine parallelMachine)) return ModifierFunction.NULL;

        int parallels = ParallelLogic.getParallelAmount(machine, recipe, parallelMachine.getMaxParallel());
        if (parallels == 0) return ModifierFunction.NULL;

        ModifierFunction parallelFunc = parallels == 1 ? ModifierFunction.IDENTITY : ModifierFunction.builder()
                .modifyAllContents(ContentModifier.multiplier(parallels))
                .eutMultiplier(parallels)
                .parallels(parallels)
                .build();

        // 先并行放大配方，再过时钟（OC 基于放大后的配方计算）
        return recipe1 -> {
            GTRecipe paralleled = parallelFunc.apply(recipe1);
            if (paralleled == null) return null;
            return GTRecipeModifiers.OC_PERFECT.getModifier(machine, paralleled).apply(paralleled);
        };
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        if (isFormed()) {
            textList.add(Component.translatable("gtuf.multiblock.parallel_amount", getMaxParallel())
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
