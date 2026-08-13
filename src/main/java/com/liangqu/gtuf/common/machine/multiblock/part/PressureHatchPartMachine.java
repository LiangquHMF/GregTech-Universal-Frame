package com.liangqu.gtuf.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import com.liangqu.gtuf.api.pipelike.pressurepipe.LevelPressurePipeNet;
import com.liangqu.gtuf.api.pipelike.pressurepipe.PressurePipeNet;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * 压力仓（Pressure Hatch）：压力机的 IO 终端，把压力机腔体接入压力管道网络。
 *
 * <p>
 * <b>不存储压力</b>——压力只存于机器腔体，本仓仅提供网络拓扑查询：
 * {@link #getConnectedNets()} 扫描六面相邻的压力管道节点，返回这些节点所属的压力网络
 * （供 {@code PressureMultiblockMachine#pressureTick} 做 peer 收集与破裂判定）。管道节点
 * 若在该仓方向被关闭（wrench 拧断）则忽略。
 * </p>
 *
 * <p>
 * 档位（tier）仅决定机型档位展示与传导语义，不改变功能。能力位
 * {@code GTUF_PartAbility.PRESSURE} 由注册工厂 {@code register(tier, block)} 挂到方块上，
 * 结构谓词 {@code Predicates.abilities(GTUF_PartAbility.PRESSURE)} 据此识别仓室。
 * </p>
 */
public class PressureHatchPartMachine extends TieredPartMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            PressureHatchPartMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    public PressureHatchPartMachine(IMachineBlockEntity holder, int tier, Object... args) {
        super(holder, tier);
    }

    /**
     * 查询本仓六面相邻压力管道所属的压力网络（去重）。
     *
     * <p>
     * 客户端或非 ServerLevel 返回空集。仅当管道节点存在且朝向本仓的连接未关闭时计入。
     * </p>
     *
     * @return 与本仓相连的压力网络集合；无连接时为空集
     */
    public Set<PressurePipeNet> getConnectedNets() {
        Set<PressurePipeNet> nets = new HashSet<>();
        Level level = getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return nets;

        BlockPos pos = getPos();
        for (Direction facing : GTUtil.DIRECTIONS) {
            BlockPos neighborPos = pos.relative(facing);
            PressurePipeNet net = LevelPressurePipeNet.getOrCreate(serverLevel).getNetFromPos(neighborPos);
            if (net == null) continue;
            var node = net.getNodeAt(neighborPos);
            if (node == null) continue;
            // 管道节点朝向本仓的方向 = facing.getOpposite()；被关闭则该面不参与传导。
            if (node.isBlocked(facing.getOpposite())) continue;
            nets.add(net);
        }
        return nets;
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

    /** 压力仓不可被多个多方块共享。 */
    @Override
    public boolean canShared() {
        return false;
    }
}
