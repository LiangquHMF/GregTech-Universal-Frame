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

    public TierElectricParallelMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    /**
     * 最大并行数 = 2 × 最大电压等级。LV(1)=2, MV(2)=4, HV(3)=6, EV(4)=8, IV(5)=10……
     */
    public int getMaxParallel() {
        int tier = GTUtil.getTierByVoltage(getMaxVoltage());
        return Math.max(1, 2 * tier);
    }

    /**
     * 配方修改器：先应用电压并行，再完美过时钟。
     * 注册时用 .recipeModifier(TierElectricParallelMachine::recipeModifier) 挂载。
     */
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof TierElectricParallelMachine parallelMachine)) return ModifierFunction.NULL;

        int parallels = ParallelLogic.getParallelAmount(machine, recipe, parallelMachine.getMaxParallel());
        if (parallels == 0) return ModifierFunction.NULL;

        ModifierFunction parallelFunc = parallels == 1 ? ModifierFunction.IDENTITY
                : ModifierFunction.builder()
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
