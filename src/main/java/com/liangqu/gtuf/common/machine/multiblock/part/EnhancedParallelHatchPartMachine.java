package com.liangqu.gtuf.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.util.Mth;

import org.jetbrains.annotations.NotNull;

/**
 * 增强型并行控制仓（Enhanced Parallel Hatch）：仿原生 {@code ParallelHatchPartMachine}。
 *
 * <p>
 * 与原生最大的区别是并行上限公式：原生 {@code (int) Math.pow(4, tier - EV)} 在 EV 以下为负指数 →
 * 并行数恒为 0，导致 LV/MV/HV 等级完全不可用。本机改为每升一级并行数 ×2：
 * </p>
 * 
 * <pre>
 *   LV=1, MV=2, HV=4, EV=8, IV=16, LuV=32, ZPM=64, UV=128, ...
 * </pre>
 * 
 * 公式封装在可覆盖的 {@link #getMaxParallel()} 中，需要修改时直接覆盖该方法。
 */
public class EnhancedParallelHatchPartMachine extends TieredPartMachine implements IFancyUIMachine, IParallelHatch {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            EnhancedParallelHatchPartMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);
    private static final int MIN_PARALLEL = 1;

    @Persisted
    private int currentParallel = 1;

    public int getCurrentParallel() {
        return currentParallel;
    }

    public EnhancedParallelHatchPartMachine(IMachineBlockEntity holder, int tier, Object... args) {
        super(holder, tier);
    }

    /**
     * 并行上限公式：2^(tier-1 + max(0, tier - IV))。
     * <p>
     * 前期（LV~IV）每级 ×2：1, 2, 4, 8, 16；后期（LuV 起）每级 ×4：64, 256, 1024。
     * 相比原生公式 4^(tier-4)（EV 以下为 0、UV=256），本机保证低等级可用，且后期增幅不低于原生
     * （UV=1024 为原生的 4 倍）。如需修改公式，直接覆盖此方法。
     * </p>
     */
    protected int getMaxParallel() {
        return getParallelLimit(getTier());
    }

    /**
     * 并行上限统一公式（静态）：2^(tier-1 + max(0, tier - IV))。
     * LV=1, MV=2, HV=4, EV=8, IV=16, LuV=64, ZPM=256, UV=1024。
     * 供注册处 tooltip 复用，保证显示与实际一致。
     */
    public static int getParallelLimit(int tier) {
        return (int) Math.pow(2, tier - GTValues.LV + Math.max(0, tier - GTValues.IV));
    }

    public void setCurrentParallel(int parallelAmount) {
        this.currentParallel = Mth.clamp(parallelAmount, MIN_PARALLEL, getMaxParallel());
        for (var controller : this.getControllers()) {
            if (controller instanceof IRecipeLogicMachine rlm) {
                rlm.getRecipeLogic().markLastRecipeDirty();
            }
        }
    }

    /**
     * 成型后允许控制器模型把本仓替换为结构外壳材质（来源 GTM 7.3.0
     * {@code com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine} 的同名方法）。
     *
     * <p>
     * GTM 7.3.0 起该方法被 {@code GTMachineModelProperties.IS_FORMED} 渲染属性门控——
     * 仅当部件模型注册了该属性且值为 true 时才替换。GTUF 仓室只注册了 RECIPE_LOGIC_STATUS，
     * 未注册 IS_FORMED → {@code hasProperty(IS_FORMED)} 为 false → 成型后整体跳过替换，
     * 保持注册时的仓室材质。这里改为按 {@link #isFormed()}（控制器位置表非空）判定；
     * 7.1.4 无此门控（接口默认 true），{@code isFormed()} 与之行为等价且更精确。
     * </p>
     */
    @Override
    public boolean replacePartModelWhenFormed() {
        return isFormed();
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup parallelAmountGroup = new WidgetGroup(0, 0, 100, 20);
        parallelAmountGroup.addWidget(new IntInputWidget(this::getCurrentParallel, this::setCurrentParallel)
                .setMin(MIN_PARALLEL)
                .setMax(getMaxParallel()));

        return parallelAmountGroup;
    }

    @Override
    @NotNull
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public boolean canShared() {
        return false;
    }
}
