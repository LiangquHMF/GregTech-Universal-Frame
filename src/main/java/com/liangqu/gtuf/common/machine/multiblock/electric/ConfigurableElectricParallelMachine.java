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
 * <li>当前生效并行数（{@link #getTargetParallel()}）可在 GUI 中用 [-] / [+] 调整，
 * 范围为 1 ~ {@link #getMaxParallel()}，并通过 {@code @Persisted} 跨端同步。</li>
 * </ul>
 * 配方修改器：先按当前并行数放大配方，再完美过时钟（OC 基于放大后的配方计算）。
 */
public class ConfigurableElectricParallelMachine extends WorkableElectricMultiblockMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            ConfigurableElectricParallelMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    private static final int MIN_PARALLEL = 1;

    /** 注册时设置的并行数上限（final，运行时只读）。 */
    private final int maxParallel;

    /** 当前生效并行数，GUI 可调，范围 [MIN_PARALLEL, maxParallel]。 */
    @Persisted
    private int targetParallel;

    public ConfigurableElectricParallelMachine(IMachineBlockEntity holder, int maxParallel, Object... args) {
        super(holder, args);
        this.maxParallel = Math.max(MIN_PARALLEL, maxParallel);
        this.targetParallel = this.maxParallel;
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

    /**
     * 配方修改器：先应用当前并行数，再完美过时钟。
     * 注册时用 {@code .recipeModifier(ConfigurableElectricParallelMachine::recipeModifier, true)} 挂载。
     */
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof ConfigurableElectricParallelMachine parallelMachine)) return ModifierFunction.NULL;

        int parallels = ParallelLogic.getParallelAmount(machine, recipe, parallelMachine.getTargetParallel());
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
        int newValue = increase ? current + 1 : current - 1;
        return Math.max(MIN_PARALLEL, Math.min(newValue, maxParallel));
    }
}
