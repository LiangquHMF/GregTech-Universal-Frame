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
 * HV(3)=6, EV(4)=8, IV(5)=10……）。额外支持在创建（构建多方块结构）时配置固定时长/能耗
 * 倍率，以及<b>按电压等级缩放</b>的额外能耗/加速倍率，全部作用于配方修改器，不在 GUI 显示。
 * <p>
 * <b>固定倍率</b>（沿用 {@link EnhancedCoilElectricMachine} 约定）：{@code <= 0} 视为关闭
 * （等效 1.0），0 即关闭；正数直接生效（如 0.5 = 减半）。默认 0（关闭）。
 * </p>
 * <p>
 * <b>按等级倍率</b>（新增，取值 <b>1 = 关闭</b>，与固定倍率的 0 = 关闭不同）：
 * <ul>
 * <li>额外能耗倍率 {@code tieredEnergy}：<1 省电。有效能耗倍率 = {@code tieredEnergy^(tier-1)}，
 *     LV(1)=1.0 恒不缩放，MV(2)=tieredEnergy，HV(3)=tieredEnergy²……电压等级越高越省电。</li>
 * <li>额外加速倍率 {@code tieredSpeed}：>1 加速（<1 会变慢）。有效时长倍率 =
 *     {@code 1 / tieredSpeed^(tier-1)}，MV 起逐级加速。注意方向与固定时长倍率相反
 *     （固定倍率 0.5 = 提速一倍；按等级倍率 2.0 = 提速一倍）。</li>
 * </ul>
 * 按等级倍率取 {@code <= 0} 时同样视为关闭（防御非法值，避免除零/负幂）。两者与固定倍率
 * 相乘叠加。默认双 1（关闭）→ 行为与历史版本一致（纯并行 + 完美过时钟）。
 * </p>
 * <p>
 * 配方修改器在注册时用 {@code .recipeModifier(TierElectricParallelMachine::recipeModifier, true)}
 * 挂载：先按最大并行放大配方，乘固定/按等级能耗倍率与时长倍率，再完美过时钟（OC 基于放大后
 * 的配方计算）。
 * </p>
 */
public class TierElectricParallelMachine extends WorkableElectricMultiblockMachine {

    /** 默认并行倍率 = 2（历史行为：最大并行数 = 2 × 最大电压等级）。 */
    public static final int DEFAULT_PARALLEL_MULTIPLIER = 2;

    /** 默认时长倍率 = 0（关闭，等效 1.0 不改动耗时）。 */
    public static final double DEFAULT_DURATION_MULTIPLIER = 0.0;
    /** 默认能耗倍率 = 0（关闭，等效 1.0 不改动能耗）。 */
    public static final double DEFAULT_ENERGY_MULTIPLIER = 0.0;

    /** 默认额外能耗倍率 = 1（关闭，等效 1.0 不改动能耗）。 */
    public static final double DEFAULT_TIERED_ENERGY_MULTIPLIER = 1.0;
    /** 默认额外加速倍率 = 1（关闭，等效 1.0 不改动耗时）。 */
    public static final double DEFAULT_TIERED_SPEED_MULTIPLIER = 1.0;

    /** 并行倍率：最大并行数 = 倍率 × 最大电压等级。KubeJS 注册时经构造器传入。 */
    private final int parallelMultiplier;

    /** 时长倍率（≤0 = 关闭，等效 1.0）。创建期设置，final 运行时只读，不在 GUI 显示。 */
    private final double durationMultiplier;

    /** 能耗倍率（≤0 = 关闭，等效 1.0）。创建期设置，final 运行时只读，不在 GUI 显示。 */
    private final double energyMultiplier;

    /** 额外能耗倍率（1 = 关闭；<1 省电，有效倍率 = 本值^(电压等级-1)）。创建期设置，final 只读。 */
    private final double tieredEnergyMultiplier;

    /** 额外加速倍率（1 = 关闭；>1 加速，有效时长倍率 = 1 / 本值^(电压等级-1)）。创建期设置，final 只读。 */
    private final double tieredSpeedMultiplier;

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
     * 完整构造器：并行倍率 + 时长倍率 + 能耗倍率 + 按等级倍率全部在创建（构建多方块结构）时设定。
     * {@code .machine((holder) => new TierElectricParallelMachine(holder, 倍率, 时长倍率, 能耗倍率, 额外能耗倍率, 额外加速倍率))}。
     *
     * @param parallelMultiplier      并行倍率（最大并行数 = 倍率 × 最大电压等级），下限 1
     * @param durationMultiplier      固定时长倍率（≤0 = 关闭不改耗时；0.5 = 提速一倍）
     * @param energyMultiplier        固定能耗倍率（≤0 = 关闭不改能耗；0.5 = 半能耗）
     * @param tieredEnergyMultiplier  额外能耗倍率（1 = 关闭；<1 省电，有效倍率 = 本值^(电压等级-1)）
     * @param tieredSpeedMultiplier   额外加速倍率（1 = 关闭；>1 加速，有效时长倍率 = 1/本值^(电压等级-1)）
     * @param args                    透传给基类的额外参数（如配方类型）
     */
    public TierElectricParallelMachine(IMachineBlockEntity holder, int parallelMultiplier,
                                       double durationMultiplier, double energyMultiplier,
                                       double tieredEnergyMultiplier, double tieredSpeedMultiplier, Object... args) {
        super(holder, args);
        this.parallelMultiplier = Math.max(1, parallelMultiplier);
        this.durationMultiplier = durationMultiplier;
        this.energyMultiplier = energyMultiplier;
        this.tieredEnergyMultiplier = tieredEnergyMultiplier;
        this.tieredSpeedMultiplier = tieredSpeedMultiplier;
    }

    /**
     * 向后兼容完整构造器：并行倍率 + 固定时长/能耗倍率，按等级倍率取默认（双 1，关闭）。
     *
     * @param parallelMultiplier 并行倍率（最大并行数 = 倍率 × 最大电压等级），下限 1
     * @param durationMultiplier 固定时长倍率（≤0 = 关闭不改耗时；0.5 = 提速一倍）
     * @param energyMultiplier   固定能耗倍率（≤0 = 关闭不改能耗；0.5 = 半能耗）
     * @param args               透传给基类的额外参数（如配方类型）
     */
    public TierElectricParallelMachine(IMachineBlockEntity holder, int parallelMultiplier,
                                       double durationMultiplier, double energyMultiplier, Object... args) {
        this(holder, parallelMultiplier, durationMultiplier, energyMultiplier,
                DEFAULT_TIERED_ENERGY_MULTIPLIER, DEFAULT_TIERED_SPEED_MULTIPLIER, args);
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

    /**
     * 额外能耗倍率（按电压等级幂次缩放）：{@code X^(tier-1)}。
     * 关闭（X = 1 或 X ≤ 0）返回 1.0；<1 省电：MV(2)=X, HV(3)=X², IV(4)=X³……LV(1)=1.0 恒不缩放。
     */
    public double getTieredEnergyMultiplier() {
        if (tieredEnergyMultiplier == 1.0 || tieredEnergyMultiplier <= 0) return 1.0;
        int tier = GTUtil.getTierByVoltage(getMaxVoltage());
        return Math.pow(tieredEnergyMultiplier, tier - 1);
    }

    /**
     * 额外加速倍率（按电压等级幂次缩放）：{@code X^(tier-1)}，作用于时长（见
     * {@link #getTieredDurationMultiplier()}）。关闭（X = 1 或 X ≤ 0）返回 1.0；
     * >1 加速：MV(2)=X, HV(3)=X²……LV(1)=1.0 恒不缩放。
     */
    public double getTieredSpeedMultiplier() {
        if (tieredSpeedMultiplier == 1.0 || tieredSpeedMultiplier <= 0) return 1.0;
        int tier = GTUtil.getTierByVoltage(getMaxVoltage());
        return Math.pow(tieredSpeedMultiplier, tier - 1);
    }

    /** 额外加速倍率折算的时长倍率：{@code 1 / getTieredSpeedMultiplier()}。关闭时为 1.0（不改耗时）。 */
    public double getTieredDurationMultiplier() {
        double speed = getTieredSpeedMultiplier();
        return speed == 1.0 ? 1.0 : 1.0 / speed;
    }

    /** 固定或按等级倍率是否任一启用（决定并行放大后是否还需叠倍率）。 */
    private boolean hasMultipliers() {
        return durationMultiplier > 0 || energyMultiplier > 0
                || (tieredEnergyMultiplier != 1.0 && tieredEnergyMultiplier > 0)
                || (tieredSpeedMultiplier != 1.0 && tieredSpeedMultiplier > 0);
    }

    /**
     * 配方修改器：先应用电压并行，乘能耗/时长倍率，再完美过时钟。
     * 注册时用 .recipeModifier(TierElectricParallelMachine::recipeModifier) 挂载。
     */
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof TierElectricParallelMachine parallelMachine)) return ModifierFunction.NULL;

        int parallels = ParallelLogic.getParallelAmount(machine, recipe, parallelMachine.getMaxParallel());
        if (parallels == 0) return ModifierFunction.NULL;

        // 并行数为 1 且所有倍率都关闭时退化为 IDENTITY（不放大、直接过时钟），保留历史快捷路径。
        // 倍率启用时即使并行数为 1 也须走 builder 应用倍率。
        ModifierFunction parallelFunc = parallels == 1 && !parallelMachine.hasMultipliers() ?
                ModifierFunction.IDENTITY :
                ModifierFunction.builder()
                        .modifyAllContents(ContentModifier.multiplier(parallels))
                        // 能耗倍率乘进 eutMultiplier（并行份数 × 固定倍率 × 按等级倍率）
                        .eutMultiplier(parallels * parallelMachine.getEnergyMultiplier()
                                * parallelMachine.getTieredEnergyMultiplier())
                        // 时长倍率单独乘（固定倍率 × 按等级倍率折算的时长倍率）
                        .durationMultiplier(parallelMachine.getDurationMultiplier()
                                * parallelMachine.getTieredDurationMultiplier())
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
