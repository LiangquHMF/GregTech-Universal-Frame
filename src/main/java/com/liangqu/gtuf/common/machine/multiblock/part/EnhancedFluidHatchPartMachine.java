package com.liangqu.gtuf.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.utils.GTMath;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.liangqu.gtuf.common.data.GTUF_Machines;

/**
 * 增强型流体输入/输出仓：仿原生 {@link FluidHatchPartMachine}。
 *
 * <p>
 * <b>容量公式</b>：原生 {@code initialCapacity * (1 << min(9, tier))} 在 tier≥9 封顶不再增长；
 * 本机改为 {@code initialCapacity * pow(4, tier)}，移除封顶且每级 ×4（增长率 = 原生 ×2）：
 * ULV=8,000 / LV=32,000 / MV=128,000 / ... / UHV=2,097,152,000 / UEV=8,388,608,000（超 int）...
 * </p>
 *
 * <p>
 * <b>存储架构</b>：float 理论容量（tooltip 展示，可超 int）+ int 实际存储（tank 容量经
 * {@link GTMath#saturatedCast(long)} 钳回，UHV 及以内真实，UEV 起钳到 {@link Integer#MAX_VALUE}≈21.4 亿）。
 * 因 {@code 8000*4^tier = 125*2^(6+2tier)}，尾数恒为 125（7 bit）≤ float 的 24 bit 尾数，
 * 全部档位在 float 中均精确表示，无精度损失。
 * </p>
 */
public class EnhancedFluidHatchPartMachine extends FluidHatchPartMachine {

    /** 基准容量 = 原生单槽 1x 仓容量（8,000 mB）。 */
    public static final int INITIAL_TANK_CAPACITY = FluidHatchPartMachine.INITIAL_TANK_CAPACITY_1X;

    public EnhancedFluidHatchPartMachine(IMachineBlockEntity holder, int tier, IO io,
                                         int initialCapacity, int slots, Object... args) {
        super(holder, tier, io, initialCapacity, slots, args);
    }

    /**
     * 理论容量公式（float，可超 int）：initialCapacity * 4^tier。
     * 供注册处 tooltip 复用，保证显示与实际公式一致。
     */
    public static float getEnhancedCapacity(int tier) {
        return (float) (INITIAL_TANK_CAPACITY * Math.pow(4, tier));
    }

    /** 实际 tank 容量（int 钳制）：UEV 起钳到 Integer.MAX_VALUE。 */
    public static int getClampedCapacity(int tier) {
        return GTMath.saturatedCast((long) getEnhancedCapacity(tier));
    }

    /** 实例理论容量，需要修改公式时直接覆盖此方法。 */
    public float getMaxCapacity() {
        return getEnhancedCapacity(getTier());
    }

    /** 按本机公式计算实际 tank 容量（int 钳制）。getTier() 在父类构造后已就绪。 */
    @Override
    protected NotifiableFluidTank createTank(int initialCapacity, int slots, Object... args) {
        return new NotifiableFluidTank(this, slots, getClampedCapacity(getTier()), io);
    }

    /**
     * 保持 IO 切换：查增强流体仓配对表 {@code (tier, 反向IO)} 得对向仓定义
     * （仿原生对 GTMachines.FLUID_IMPORT/EXPORT_HATCH 数组的引用）。
     * 配对表由注册工厂填充；原生 GTM KJS 自定义注册的仓需先手动关联配对表，否则返回 false。
     */
    @Override
    public boolean swapIO() {
        BlockPos blockPos = getHolder().pos();
        IO targetIo = io == IO.IN ? IO.OUT : IO.IN;
        MachineDefinition newDefinition = GTUF_Machines.getEnhancedFluidHatch(getTier(), targetIo);
        if (newDefinition == null) return false;

        BlockState newBlockState = newDefinition.getBlock().defaultBlockState();
        getLevel().setBlockAndUpdate(blockPos, newBlockState);
        if (getLevel().getBlockEntity(blockPos) instanceof IMachineBlockEntity newHolder) {
            if (newHolder.getMetaMachine() instanceof FluidHatchPartMachine newMachine) {
                newMachine.setFrontFacing(this.getFrontFacing());
                newMachine.setUpwardsFacing(this.getUpwardsFacing());
                newMachine.setPaintingColor(this.getPaintingColor());
                for (int i = 0; i < this.tank.getTanks(); i++) {
                    newMachine.tank.setFluidInTank(i, this.tank.getFluidInTank(i));
                }
            }
        }
        return true;
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
}
