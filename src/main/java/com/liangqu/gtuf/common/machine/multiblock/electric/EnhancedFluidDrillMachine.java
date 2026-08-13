package com.liangqu.gtuf.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FluidDrillMachine;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import com.liangqu.gtuf.common.machine.trait.GTUFFluidDrillLogic;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 增强钻井机（GTUF 版）：完整复制 GTM {@code FluidDrillMachine} 的行为（矿脉探测/枯竭、
 * 过时钟 +50% 产液、能量等级判定 {@code getEnergyTier()} 等），额外支持在创建（KubeJS 构建
 * 多方块结构）时配置<b>最大并行数、时长倍率、能耗倍率</b>。
 *
 * <p>
 * <b>实现方式：继承而非重写。</b>本类直接 extends {@code FluidDrillMachine}，仅覆写
 * {@link #createRecipeLogic} 换成 {@link GTUFFluidDrillLogic}（把并行/倍率落到每次生成的钻井
 * 配方上）。能量等级、静态工具方法（{@code getDepletionChance/getRigMultiplier/getCasingState/
 * getFrameState/getBaseTexture}）原样继承，KubeJS 脚本可直接复用它们建结构。
 * </p>
 *
 * <p>
 * <b>倍率语义</b>（创建期经构造器传入，KubeJS {@code .machine((holder, tier) => new
 * GTUFFluidDrillMachine(holder, tier, 并行, 时长倍率, 能耗倍率))}）：
 * </p>
 * <ul>
 * <li><b>并行数 P</b>（≥1，1 = 不并行）：每次作业产出的矿脉流体 ×P、能耗 ×P
 * （N 台钻井机合一）。</li>
 * <li><b>时长倍率 D</b>（≤0 = 关闭等效 1.0；0.5 = 每份作业用时减半 → 单位时间产出翻倍、
 * 总能耗按用时缩短降低）。</li>
 * <li><b>能耗倍率 E</b>（≤0 = 关闭等效 1.0；0.5 = 半能耗）。</li>
 * </ul>
 *
 * <p>
 * 基准时长仍为 {@code FluidDrillLogic.MAX_PROGRESS}=20 tick；天然过时钟（能量等级高于机器
 * 档位）的 +50% 产液行为保留，与用户并行叠加。
 * </p>
 */
public class EnhancedFluidDrillMachine extends FluidDrillMachine {

    /** 默认并行数 = 1（不并行）。 */
    public static final int DEFAULT_PARALLEL = 1;

    /** 默认时长倍率 = 0（关闭，等效 1.0）。 */
    public static final double DEFAULT_DURATION_MULTIPLIER = 0.0;
    /** 默认能耗倍率 = 0（关闭，等效 1.0）。 */
    public static final double DEFAULT_ENERGY_MULTIPLIER = 0.0;

    /** 最大并行数（≥1）。创建期设置，final 运行时只读。 */
    private final int maxParallel;

    /** 时长倍率（≤0 = 关闭，等效 1.0）。创建期设置，final 运行时只读。 */
    private final double durationMultiplier;

    /** 能耗倍率（≤0 = 关闭，等效 1.0）。创建期设置，final 运行时只读。 */
    private final double energyMultiplier;

    /**
     * 默认构造：并行 1、时长/能耗倍率关闭。与 GTM {@code FluidDrillMachine} 签名一致，
     * 供 testmod/工厂的 {@code ::new} 方法引用使用。
     */
    public EnhancedFluidDrillMachine(IMachineBlockEntity holder, int tier) {
        this(holder, tier, DEFAULT_PARALLEL, DEFAULT_DURATION_MULTIPLIER, DEFAULT_ENERGY_MULTIPLIER);
    }

    /**
     * 完整构造器：并行数 + 时长倍率 + 能耗倍率全部在创建（KubeJS 构建多方块结构）时设定。
     *
     * @param tier               机器档位（GTValues 序数，驱动能量等级/枯竭概率/rig 倍率）
     * @param maxParallel        最大并行数（≥1，1 = 不并行）
     * @param durationMultiplier 时长倍率（≤0 = 关闭；0.5 = 每份作业用时减半）
     * @param energyMultiplier   能耗倍率（≤0 = 关闭；0.5 = 半能耗）
     */
    public EnhancedFluidDrillMachine(IMachineBlockEntity holder, int tier, int maxParallel,
                                     double durationMultiplier, double energyMultiplier) {
        super(holder, tier);
        this.maxParallel = Math.max(1, maxParallel);
        this.durationMultiplier = durationMultiplier;
        this.energyMultiplier = energyMultiplier;
    }

    /** 最大并行数（≥1）。 */
    public int getMaxParallel() {
        return maxParallel;
    }

    /** 时长倍率（≤0 = 关闭，返回 1.0）。 */
    public double getDurationMultiplier() {
        return durationMultiplier <= 0 ? 1.0 : durationMultiplier;
    }

    /** 能耗倍率（≤0 = 关闭，返回 1.0）。 */
    public double getEnergyMultiplier() {
        return energyMultiplier <= 0 ? 1.0 : energyMultiplier;
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new GTUFFluidDrillLogic(this);
    }

    @NotNull
    @Override
    public GTUFFluidDrillLogic getRecipeLogic() {
        return (GTUFFluidDrillLogic) super.getRecipeLogic();
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
