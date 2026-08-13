package com.liangqu.gtuf.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.DistillationTowerMachine;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 增强蒸馏塔（GTUF 版）：完整复制 GTM {@code DistillationTowerMachine} 的行为（含
 * {@code FluidRecipeCapability.ICustomParallel.limitFluidParallel} 流体输出分层并行判定），
 * 额外支持在创建（KubeJS 构建多方块结构）时配置<b>最大并行数、时长倍率、能耗倍率</b>。
 *
 * <p>
 * <b>实现方式：继承而非重写。</b>本类直接 extends {@code DistillationTowerMachine}，所有结构/
 * 配方逻辑/内类 {@code DistillationTowerLogic} 原样继承。并行通过挂载本类的
 * {@link #recipeModifier}（{@code .recipeModifier(GTUFDistillationTowerMachine::recipeModifier,
 * true)}）实现——与 {@link TierElectricParallelMachine} 同一套配方修改器契约：
 * 先 {@link ParallelLogic#getParallelAmount} 求并行数（内部经继承的
 * {@code limitFluidParallel} 按流体输出仓层数/容量封顶），放大配方内容 + EUt×并行，乘时长/能耗
 * 倍率，再完美过时钟（OC 基于放大后的配方计算）。
 * </p>
 *
 * <p>
 * <b>倍率约定</b>（与 {@link TierElectricParallelMachine} 一致）：时长/能耗倍率 {@code <= 0}
 * 视为关闭（等效 1.0），正数倍率直接生效（如 0.5 = 减半）。并行数下限 1（1 = 不并行，仅时长/
 * 能耗倍率仍生效）。默认并行 1、两倍率关闭 → 行为与原生蒸馏塔一致（加一层 OC_PERFECT 取代
 * GTM 原生 OC_NON_PERFECT_SUBTICK，速度体验与增强电力并行机统一）。
 * </p>
 *
 * <p>
 * 节能仓兼容：本机走标准 {@code RecipeLogic} 配方修改路径，{@code GTUFEnergySavingRecipeLogicMixin}
 * 会在本类 recipeModifier 之后把节能仓倍率作用于最终 EUt（并行/OC 之后），与 GTM 原生电力
 * 多方块一致。
 * </p>
 */
public class EnhancedDistillationTowerMachine extends DistillationTowerMachine {

    /** 默认并行数 = 1（不并行）。 */
    public static final int DEFAULT_PARALLEL = 1;

    /** 默认时长倍率 = 0（关闭，等效 1.0 不改动耗时）。 */
    public static final double DEFAULT_DURATION_MULTIPLIER = 0.0;
    /** 默认能耗倍率 = 0（关闭，等效 1.0 不改动能耗）。 */
    public static final double DEFAULT_ENERGY_MULTIPLIER = 0.0;

    /** 最大并行数（≥1）。KubeJS 注册多方块结构时经构造器传入。 */
    private final int maxParallel;

    /** 时长倍率（≤0 = 关闭，等效 1.0）。创建期设置，final 运行时只读。 */
    private final double durationMultiplier;

    /** 能耗倍率（≤0 = 关闭，等效 1.0）。创建期设置，final 运行时只读。 */
    private final double energyMultiplier;

    /**
     * 默认构造：yOffset=1、并行 1、时长/能耗倍率关闭。与 GTM {@code DistillationTowerMachine}
     * 签名一致，供 testmod/工厂的 {@code ::new} 方法引用使用。
     */
    public EnhancedDistillationTowerMachine(IMachineBlockEntity holder) {
        this(holder, 1);
    }

    /**
     * 指定 yOffset 构造：并行 1、时长/能耗倍率关闭。与 GTM {@code DistillationTowerMachine} 一致。
     *
     * @param yOffset 控制器 Y 与第一个流体输出仓的 Y 差
     */
    public EnhancedDistillationTowerMachine(IMachineBlockEntity holder, int yOffset) {
        this(holder, yOffset, DEFAULT_PARALLEL, DEFAULT_DURATION_MULTIPLIER, DEFAULT_ENERGY_MULTIPLIER);
    }

    /**
     * KubeJS 注册多方块结构时指定最大并行数（时长/能耗倍率关闭）：
     * {@code .machine((holder) => new GTUFDistillationTowerMachine(holder, 1, 4))}。
     *
     * @param yOffset     控制器 Y 与第一个流体输出仓的 Y 差
     * @param maxParallel 最大并行数（≥1，1 = 不并行）
     */
    public EnhancedDistillationTowerMachine(IMachineBlockEntity holder, int yOffset, int maxParallel) {
        this(holder, yOffset, maxParallel, DEFAULT_DURATION_MULTIPLIER, DEFAULT_ENERGY_MULTIPLIER);
    }

    /**
     * 完整构造器：并行数 + 时长倍率 + 能耗倍率全部在创建（KubeJS 构建多方块结构）时设定：
     * {@code .machine((holder) => new GTUFDistillationTowerMachine(holder, 1, 4, 0.75, 0.8))}。
     *
     * @param yOffset            控制器 Y 与第一个流体输出仓的 Y 差
     * @param maxParallel        最大并行数（≥1，1 = 不并行）
     * @param durationMultiplier 时长倍率（≤0 = 关闭不改耗时；0.5 = 提速一倍）
     * @param energyMultiplier   能耗倍率（≤0 = 关闭不改能耗；0.5 = 半能耗）
     */
    public EnhancedDistillationTowerMachine(IMachineBlockEntity holder, int yOffset, int maxParallel,
                                            double durationMultiplier, double energyMultiplier) {
        super(holder, yOffset);
        this.maxParallel = Math.max(1, maxParallel);
        this.durationMultiplier = durationMultiplier;
        this.energyMultiplier = energyMultiplier;
    }

    /** 最大并行数（≥1）。 */
    public int getMaxParallel() {
        return maxParallel;
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
     * 配方修改器：先按流体输出能力求最大并行数，乘能耗/时长倍率，再完美过时钟。
     * 注册时用 {@code .recipeModifier(GTUFDistillationTowerMachine::recipeModifier, true)} 挂载。
     */
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof EnhancedDistillationTowerMachine tower)) return ModifierFunction.NULL;

        int parallels = ParallelLogic.getParallelAmount(machine, recipe, tower.getMaxParallel());
        if (parallels == 0) return ModifierFunction.NULL;

        // 并行数为 1 且两个倍率都关闭时退化为 IDENTITY（不放大、直接过时钟）。
        ModifierFunction parallelFunc = parallels == 1 && !tower.hasMultipliers() ?
                ModifierFunction.IDENTITY :
                ModifierFunction.builder()
                        .modifyAllContents(ContentModifier.multiplier(parallels))
                        // 能耗倍率乘进 eutMultiplier（并行份数 × 倍率）
                        .eutMultiplier(parallels * tower.getEnergyMultiplier())
                        // 时长倍率单独乘
                        .durationMultiplier(tower.getDurationMultiplier())
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
