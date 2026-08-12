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

/**
 * 分级电力并行机：最大并行数 = 并行倍率 × 最大电压等级（倍率 2 时 LV(1)=2, MV(2)=4,
 * HV(3)=6, EV(4)=8, IV(5)=10……）。额外支持在创建（构建多方块结构）时配置时长倍率与
 * 能耗倍率，作用于配方修改器，不在 GUI 显示。
 * <p>
 * 时长/能耗倍率沿用 {@link EnhancedCoilElectricMachine} 的约定：{@code <= 0} 视为关闭
 * 该功能（等效 1.0，不改动时长/能耗），0 即关闭；正数倍率直接生效（如 0.5 = 减半）。
 * 默认两个倍率均为 0（关闭）→ 机器行为与历史版本一致（纯并行 + 完美过时钟）。
 * </p>
 * <p>
 * 配方修改器在注册时用 {@code .recipeModifier(TierElectricParallelMachine::recipeModifier, true)}
 * 挂载：先按最大并行放大配方，乘能耗倍率与时长倍率，再完美过时钟（OC 基于放大后的配方计算）。
 * </p>
 */
public class TierElectricParallelMachine extends WorkableElectricMultiblockMachine {

    /** 默认并行倍率 = 2（历史行为：最大并行数 = 2 × 最大电压等级）。 */
    public static final int DEFAULT_PARALLEL_MULTIPLIER = 2;

    /** 默认时长倍率 = 0（关闭，等效 1.0 不改动耗时）。 */
    public static final double DEFAULT_DURATION_MULTIPLIER = 0.0;
    /** 默认能耗倍率 = 0（关闭，等效 1.0 不改动能耗）。 */
    public static final double DEFAULT_ENERGY_MULTIPLIER = 0.0;

    /** 并行倍率：最大并行数 = 倍率 × 最大电压等级。KubeJS 注册时经构造器传入。 */
    private final int parallelMultiplier;

    /** 时长倍率（≤0 = 关闭，等效 1.0）。创建期设置，final 运行时只读，不在 GUI 显示。 */
    private final double durationMultiplier;

    /** 能耗倍率（≤0 = 关闭，等效 1.0）。创建期设置，final 运行时只读，不在 GUI 显示。 */
    private final double energyMultiplier;

    /**
     * 默认构造：倍率取 {@link #DEFAULT_PARALLEL_MULTIPLIER}，时长/能耗倍率关闭。
     * 供 testmod 的 {@code TierElectricParallelMachine::new} 等方法引用使用。
     */
    public TierElectricParallelMachine(IMachineBlockEntity holder, Object... args) {
        this(holder, DEFAULT_PARALLEL_MULTIPLIER, args);
    }

    /**
     * KubeJS 注册多方块结构时指定初始并行倍率（时长/能耗倍率关闭）：
     * {@code .machine((holder) => new TierElectricParallelMachine(holder, 倍率))}。
     *
     * @param parallelMultiplier 并行倍率（最大并行数 = 倍率 × 最大电压等级），下限 1
     */
    public TierElectricParallelMachine(IMachineBlockEntity holder, int parallelMultiplier, Object... args) {
        this(holder, parallelMultiplier, DEFAULT_DURATION_MULTIPLIER, DEFAULT_ENERGY_MULTIPLIER, args);
    }

    /**
     * 完整构造器：并行倍率 + 时长倍率 + 能耗倍率全部在创建（构建多方块结构）时设定。
     * {@code .machine((holder) => new TierElectricParallelMachine(holder, 倍率, 时长倍率, 能耗倍率))}。
     *
     * @param parallelMultiplier 并行倍率（最大并行数 = 倍率 × 最大电压等级），下限 1
     * @param durationMultiplier 时长倍率（≤0 = 关闭不改耗时；0.5 = 提速一倍）
     * @param energyMultiplier   能耗倍率（≤0 = 关闭不改能耗；0.5 = 半能耗）
     * @param args               透传给基类的额外参数（如配方类型）
     */
    public TierElectricParallelMachine(IMachineBlockEntity holder, int parallelMultiplier,
                                       double durationMultiplier, double energyMultiplier, Object... args) {
        super(holder, args);
        this.parallelMultiplier = Math.max(1, parallelMultiplier);
        this.durationMultiplier = durationMultiplier;
        this.energyMultiplier = energyMultiplier;
    }

    /**
     * 最大并行数 = 倍率 × 最大电压等级。倍率由构造器传入（KubeJS 注册时指定），默认 2。
     * LV(1)=2, MV(2)=4, HV(3)=6, EV(4)=8, IV(5)=10……（倍率=2 时）。
     */
    public int getMaxParallel() {
        int tier = GTUtil.getTierByVoltage(getMaxVoltage());
        return Math.max(1, parallelMultiplier * tier);
    }

    /** 时长倍率（≤0 = 关闭，返回 1.0 不改动耗时）。 */
    public double getDurationMultiplier() {
        return durationMultiplier <= 0 ? 1.0 : durationMultiplier;
    }

    /** 能耗倍率（≤0 = 关闭，返回 1.0 不改动能耗）。 */
    public double getEnergyMultiplier() {
        return energyMultiplier <= 0 ? 1.0 : energyMultiplier;
    }

    /** 时长或能耗倍率是否任一启用（决定并行放大后是否还需叠倍率）。 */
    private boolean hasMultipliers() {
        return durationMultiplier > 0 || energyMultiplier > 0;
    }

    /**
     * 配方修改器：先应用电压并行，乘能耗/时长倍率，再完美过时钟。
     * 注册时用 .recipeModifier(TierElectricParallelMachine::recipeModifier) 挂载。
     */
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof TierElectricParallelMachine parallelMachine)) return ModifierFunction.NULL;

        int parallels = ParallelLogic.getParallelAmount(machine, recipe, parallelMachine.getMaxParallel());
        if (parallels == 0) return ModifierFunction.NULL;

        // 并行数为 1 且两个倍率都关闭时退化为 IDENTITY（不放大、直接过时钟），保留历史快捷路径。
        // 倍率启用时即使并行数为 1 也须走 builder 应用倍率。
        ModifierFunction parallelFunc = parallels == 1 && !parallelMachine.hasMultipliers() ?
                ModifierFunction.IDENTITY :
                ModifierFunction.builder()
                        .modifyAllContents(ContentModifier.multiplier(parallels))
                        // 能耗倍率乘进 eutMultiplier（并行份数 × 倍率）
                        .eutMultiplier(parallels * parallelMachine.getEnergyMultiplier())
                        // 时长倍率单独乘
                        .durationMultiplier(parallelMachine.getDurationMultiplier())
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
