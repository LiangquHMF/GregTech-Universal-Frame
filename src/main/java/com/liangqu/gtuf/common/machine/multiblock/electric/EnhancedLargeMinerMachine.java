package com.liangqu.gtuf.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.LargeMinerMachine;
import com.gregtechceu.gtceu.utils.GTTransferUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.List;

import static com.gregtechceu.gtceu.common.data.GTMaterials.DrillingFluid;

/**
 * 增强大型矿机（GTUF 版）：完整复制 GTM {@code LargeMinerMachine} 的行为（区块采掘/管道/
 * 丝绳/丝绸之触/半径调节等），额外支持在创建（KubeJS 构建多方块结构）时配置
 * <b>最大并行数、时长倍率、能耗倍率</b>。
 *
 * <p>
 * <b>实现方式：继承而非重写。</b>矿机不走配方——每次采掘由 {@code MinerLogic.serverTick}
 * 在 {@code speed}（每块 tick）间隔挖一块，能量与钻探液在 {@code drainInput} 逐 tick 消耗。
 * 故把三个倍率按矿机语义落到两处：
 * </p>
 * <ul>
 * <li><b>并行数 P</b>（≥1）：{@link #computeEffectiveSpeed} 把采掘间隔缩为
 * {@code speed × D / P}（P 台矿机合一，采掘速度 ×P），{@link #drainInput} 同步把
 * 每 tick 能耗与钻探液消耗 ×P（每块矿石能耗恒定，总吞吐 ×P）。</li>
 * <li><b>时长倍率 D</b>（≤0 = 关闭等效 1.0；0.5 = 采掘间隔减半 → 速度翻倍）。</li>
 * <li><b>能耗倍率 E</b>（≤0 = 关闭等效 1.0；0.5 = 每 tick 能耗减半）。</li>
 * </ul>
 *
 * <p>
 * 有效采掘间隔烘焙进 {@code super} 构造参数（{@code WorkableMultiblockMachine} 的
 * {@code createRecipeLogic} 原样读取），无需覆写矿机逻辑。能耗/钻探液经 {@link #drainInput}
 * 覆写放大。半径/丝绳/区块模式等其余逻辑全部继承原生类。
 * </p>
 */
public class EnhancedLargeMinerMachine extends LargeMinerMachine {

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

    /** 钻探液每 tick 消耗量（基类字段私有，本类为 {@link #drainInput} 覆写自存一份）。 */
    private final int drillingFluidConsumePerTick;

    /**
     * 默认构造：并行 1、时长/能耗倍率关闭。与 GTM {@code LargeMinerMachine} 签名一致，
     * 供 testmod/工厂的 {@code ::new} 方法引用使用。
     */
    public EnhancedLargeMinerMachine(IMachineBlockEntity holder, int tier, int speed, int maximumChunkDiameter,
                                     int fortune, int drillingFluidConsumePerTick) {
        this(holder, tier, speed, maximumChunkDiameter, fortune, drillingFluidConsumePerTick,
                DEFAULT_PARALLEL, DEFAULT_DURATION_MULTIPLIER, DEFAULT_ENERGY_MULTIPLIER);
    }

    /**
     * 完整构造器：并行数 + 时长倍率 + 能耗倍率全部在创建（KubeJS 构建多方块结构）时设定：
     * {@code .machine((holder, tier) => new GTUFLargeMinerMachine(holder, tier, 64/tier,
     * 2*tier-5, tier, 8-(tier-5), 并行, 时长倍率, 能耗倍率))}。
     *
     * @param tier                        机器档位（GTValues 序数）
     * @param speed                       原生每块采掘间隔（tick），有效间隔 = speed × 时长倍率 ÷ 并行
     * @param maximumChunkDiameter        最大采掘直径（区块单位，同 GTM）
     * @param fortune                     时运等级（同 GTM）
     * @param drillingFluidConsumePerTick 钻探液每 tick 消耗量（同 GTM）
     * @param maxParallel                 最大并行数（≥1，1 = 不并行；采掘速度与能耗/钻探液 ×P）
     * @param durationMultiplier          时长倍率（≤0 = 关闭；0.5 = 采掘间隔减半）
     * @param energyMultiplier            能耗倍率（≤0 = 关闭；0.5 = 半能耗）
     */
    public EnhancedLargeMinerMachine(IMachineBlockEntity holder, int tier, int speed, int maximumChunkDiameter,
                                     int fortune, int drillingFluidConsumePerTick, int maxParallel,
                                     double durationMultiplier, double energyMultiplier) {
        super(holder, tier, computeEffectiveSpeed(speed, maxParallel, durationMultiplier), maximumChunkDiameter,
                fortune, drillingFluidConsumePerTick);
        this.maxParallel = Math.max(1, maxParallel);
        this.durationMultiplier = durationMultiplier;
        this.energyMultiplier = energyMultiplier;
        this.drillingFluidConsumePerTick = drillingFluidConsumePerTick;
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

    /**
     * 有效采掘间隔 = {@code speed × 时长倍率 ÷ 并行}（下限 1 tick）。并行与时长倍率都通过
     * 缩放间隔体现：P 台矿机合一 → 间隔 ÷P；时长倍率 D&lt;1 → 间隔 ×D（更快）。
     *
     * @param speed              原生每块采掘间隔（tick）
     * @param maxParallel        最大并行数（≥1）
     * @param durationMultiplier 时长倍率（≤0 = 关闭等效 1.0）
     * @return 有效采掘间隔（≥1 tick）
     */
    public static int computeEffectiveSpeed(int speed, int maxParallel, double durationMultiplier) {
        double duration = durationMultiplier <= 0 ? 1.0 : durationMultiplier;
        int effective = (int) Math.round(speed * duration / Math.max(1, maxParallel));
        return Math.max(1, effective);
    }

    /**
     * 每 tick 消耗：能耗 = {@code VA[energyTier] × 并行 × 能耗倍率}，钻探液 = 基量 ×
     * 并行（与采掘速度放大匹配，保持每块矿石成本与原生一致）。覆写自 GTM
     * {@code LargeMinerMachine#drainInput}。
     */
    @Override
    public boolean drainInput(boolean simulate) {
        // drain energy
        if (energyContainer != null && energyContainer.getEnergyStored() > 0) {
            long energyToDrain = Math.round(GTValues.VA[getEnergyTier()] * getMaxParallel() *
                    getEnergyMultiplier());
            long resultEnergy = energyContainer.getEnergyStored() - energyToDrain;
            if (resultEnergy >= 0L && resultEnergy <= energyContainer.getEnergyCapacity()) {
                if (!simulate) {
                    energyContainer.changeEnergy(-energyToDrain);
                }
            } else {
                return false;
            }
        } else {
            return false;
        }

        // drain fluid
        if (inputFluidInventory != null && inputFluidInventory.handlers.length > 0) {
            FluidStack drillingFluid = DrillingFluid.getFluid(
                    this.drillingFluidConsumePerTick * getRecipeLogic().getOverclockAmount() * getMaxParallel());
            FluidStack fluidStack = inputFluidInventory.getFluidInTank(0);
            if (fluidStack != FluidStack.EMPTY && fluidStack.isFluidEqual(DrillingFluid.getFluid(1)) &&
                    fluidStack.getAmount() >= drillingFluid.getAmount()) {
                if (!simulate) {
                    GTTransferUtils.drainFluidAccountNotifiableList(inputFluidInventory, drillingFluid,
                            IFluidHandler.FluidAction.EXECUTE);
                }
            } else {
                return false;
            }
        }
        return true;
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
