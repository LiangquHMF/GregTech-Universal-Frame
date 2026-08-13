package com.liangqu.gtuf.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;

import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import com.liangqu.gtuf.common.machine.trait.GTUFEnergySavingRegistry;
import com.liangqu.gtuf.config.GTUF_Config;
import org.jetbrains.annotations.NotNull;

/**
 * 节能仓（Energy Saving Hatch）：安装该仓室的多方块额外获得一个能耗减免倍率，
 * 减免幅度取决于仓室档位。
 *
 * <p>
 * <b>档位减免公式</b>（config {@code [energySaving]} 可调）：
 * {@code 能耗倍率 = max(minMultiplier, (100 - 5 × 档位差 × extraMultiplier) / 100)}，
 * 档位差从 LV 起算 1（LV=1、MV=2、HV=3…）。默认倍率 1.0 下 LV=95%、MV=90%、
 * HV=85%…每级多减免 5%。多个节能仓共存时取<b>最优</b>（减免最大、能耗倍率最小者生效）。
 * </p>
 *
 * <p>
 * <b>推广机制</b>：本仓是标准 {@link IMultiPart}，Mixin 推广方案下任何电力多方块——
 * 含 GTM 原生机器——装上本仓即获得能耗减免，不依赖机器实现任何接口。减免作用于
 * 配方<b>最终</b> EUt（含并行/OC 之后的实际消耗），由
 * {@code GTUFEnergySavingRecipeLogicMixin} 在 {@code RecipeLogic.fullModifyRecipe}
 * 调用点经 {@link GTUFEnergySavingRegistry#applyMultiplier} 直接扫描控制器的
 * {@code getParts()} 取得最优倍率后追加 {@code ModifierFunction.eutMultiplier} 实现——
 * <b>权威判定</b>，拆除本仓立即失效，无注册表残留。
 * </p>
 *
 * <p>
 * 仅影响 EUt，对蒸汽/流体/时长无作用；减免后 EUt 下限钳制为 config 的
 * {@code minMultiplier}（默认 5%），不会降到 0 或负。
 * </p>
 */
public class EnergySavingHatchPartMachine extends TieredPartMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            EnergySavingHatchPartMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    public EnergySavingHatchPartMachine(IMachineBlockEntity holder, int tier, Object... args) {
        super(holder, tier);
    }

    /**
     * 按档位计算能耗倍率：{@code 100 - 5 × 档位差 × config 额外倍率}，档位差 LV=1 起算。
     *
     * @param tier 档位（GTValues 序数，LV=0）
     * @return 能耗倍率（&lt;1 = 减免；1 = 无减免；下限 config {@code minMultiplier}）
     */
    public static double getEnergyMultiplier(int tier) {
        int tierIndex = tier - GTValues.LV + 1;
        double percent = 100.0 - 5.0 * tierIndex * GTUF_Config.getEnergySavingExtraMultiplier();
        return Math.max(GTUF_Config.getEnergySavingMinMultiplier(), percent / 100.0);
    }

    /** 本仓的能耗倍率（见 {@link #getEnergyMultiplier(int)}）。 */
    public double getEnergyMultiplier() {
        return getEnergyMultiplier(getTier());
    }

    /**
     * 成型后允许控制器模型把本仓替换为结构外壳材质（与 ThreadHatch 一致，兼容 GTM 7.3.0+）。
     */
    @Override
    public boolean replacePartModelWhenFormed() {
        return isFormed();
    }

    @Override
    @NotNull
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /** 节能仓不可被多个多方块共享。 */
    @Override
    public boolean canShared() {
        return false;
    }
}
