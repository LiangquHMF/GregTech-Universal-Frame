package com.liangqu.gtuf.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;

import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 可配置并行数的电力多方块机器。
 * <ul>
 * <li>最大并行数在注册（构建多方块结构）时通过构造参数设置，运行时不可超过该上限；</li>
 * <li>能耗倍率、时长倍率也在创建（构建多方块结构）时通过构造参数设置（1.0 = 不改变），
 * 作用于配方修改器，不在 GUI 显示；</li>
 * <li>当前生效并行数（{@link #getTargetParallel()}）可在 GUI 中用 [-] / [+] 调整，
 * 范围为 1 ~ {@link #getMaxParallel()}，并通过 {@code @Persisted} 跨端同步。</li>
 * </ul>
 * 配方修改器：先按当前并行数放大配方并乘能耗/时长倍率，再完美过时钟（OC 基于放大后的配方计算）。
 */
public class ConfigurableElectricParallelMachine extends WorkableElectricMultiblockMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            ConfigurableElectricParallelMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    private static final int MIN_PARALLEL = 1;
    /** 能耗/时长倍率下限（防止 0/负数导致配方能耗或时长异常）。 */
    private static final double MIN_MULTIPLIER = 0.01;
    /** 默认能耗倍率（1.0 = 不改变能耗）。 */
    public static final double DEFAULT_ENERGY_MULTIPLIER = 1.0;
    /** 默认时长倍率（1.0 = 不改变配方时长）。 */
    public static final double DEFAULT_DURATION_MULTIPLIER = 1.0;

    /** 注册时设置的并行数上限（final，运行时只读）。 */
    private final int maxParallel;

    /** 创建时设置的能耗倍率（final，运行时只读；低于 {@link #MIN_MULTIPLIER} 会被抬到下限）。 */
    private final double energyMultiplier;

    /** 创建时设置的时长倍率（final，运行时只读；低于 {@link #MIN_MULTIPLIER} 会被抬到下限）。 */
    private final double durationMultiplier;

    /** 当前生效并行数，GUI 可调，范围 [MIN_PARALLEL, maxParallel]。 */
    @Persisted
    private int targetParallel;

    /** 便捷构造器：能耗/时长倍率取默认（1.0，不改变能耗与时长）。 */
    public ConfigurableElectricParallelMachine(IMachineBlockEntity holder, int maxParallel, Object... args) {
        this(holder, maxParallel, DEFAULT_ENERGY_MULTIPLIER, DEFAULT_DURATION_MULTIPLIER, args);
    }

    /**
     * 完整构造器：并行上限 + 能耗倍率 + 时长倍率全部在创建（构建多方块结构）时设定。
     *
     * @param maxParallel        并行数上限（≥1，恒启用）
     * @param energyMultiplier   能耗倍率（1.0 = 原能耗；0.5 = 半能耗；低于 {@link #MIN_MULTIPLIER}
     *                           会被抬到下限）
     * @param durationMultiplier 时长倍率（1.0 = 原时长；0.5 = 提速一倍；低于
     *                           {@link #MIN_MULTIPLIER} 会被抬到下限）
     * @param args               透传给基类的额外参数（如配方类型）
     */
    public ConfigurableElectricParallelMachine(IMachineBlockEntity holder, int maxParallel,
                                               double energyMultiplier, double durationMultiplier, Object... args) {
        super(holder, args);
        this.maxParallel = Math.max(MIN_PARALLEL, maxParallel);
        this.targetParallel = this.maxParallel;
        this.energyMultiplier = Math.max(MIN_MULTIPLIER, energyMultiplier);
        this.durationMultiplier = Math.max(MIN_MULTIPLIER, durationMultiplier);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /** 注册时设置的并行数上限。 */
    public int getMaxParallel() {
        return maxParallel;
    }

    /** 当前生效并行数（GUI 调整后）。 */
    public int getTargetParallel() {
        return targetParallel;
    }

    /** 创建时设置的能耗倍率（≥ {@link #MIN_MULTIPLIER}）。 */
    public double getEnergyMultiplier() {
        return energyMultiplier;
    }

    /** 创建时设置的时长倍率（≥ {@link #MIN_MULTIPLIER}）。 */
    public double getDurationMultiplier() {
        return durationMultiplier;
    }

    /**
     * 配方修改器：先应用当前并行数 × 能耗倍率 × 时长倍率，再完美过时钟。
     * 注册时用 {@code .recipeModifier(ConfigurableElectricParallelMachine::recipeModifier, true)} 挂载。
     */
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof ConfigurableElectricParallelMachine parallelMachine)) return ModifierFunction.NULL;

        int parallels = ParallelLogic.getParallelAmount(machine, recipe, parallelMachine.getTargetParallel());
        if (parallels == 0) return ModifierFunction.NULL;

        // 并行放大（modifyAllContents × parallels）+ 能耗倍率（乘进 eutMultiplier，并行份数 × 倍率）
        // + 时长倍率（单独乘）。
        ModifierFunction parallelFunc = ModifierFunction.builder()
                .modifyAllContents(ContentModifier.multiplier(parallels))
                .eutMultiplier(parallels * parallelMachine.getEnergyMultiplier())
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
            textList.add(Component.translatable("gtuf.multiblock.parallel_amount", targetParallel)
                    .withStyle(ChatFormatting.GOLD));
            textList.add(Component.translatable("gtuf.gui.parallel")
                    .append(ComponentPanelWidget.withButton(
                            Component.translatable("gtuf.gui.decrease"), "parallelSub"))
                    .append(ComponentPanelWidget.withButton(
                            Component.translatable("gtuf.gui.increase"), "parallelAdd")));
        }
    }

    @Override
    public void handleDisplayClick(String componentData, ClickData clickData) {
        if (clickData.isRemote) return;
        this.targetParallel = "parallelSub".equals(componentData) ? adjust(targetParallel, false) :
                adjust(targetParallel, true);
    }

    private int adjust(int current, boolean increase) {
        int newValue = increase ? current * 2 : current / 2;
        return Math.max(MIN_PARALLEL, Math.min(newValue, maxParallel));
    }
}
