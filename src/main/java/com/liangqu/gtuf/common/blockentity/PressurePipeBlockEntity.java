package com.liangqu.gtuf.common.blockentity;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import com.liangqu.gtuf.api.data.material.PressurePipeProperties;
import com.liangqu.gtuf.api.pipelike.pressurepipe.PressurePipeType;
import com.liangqu.gtuf.common.machine.multiblock.part.PressureHatchPartMachine;

/**
 * 压力管道方块实体（近空子类，固定泛型）。
 *
 * <p>
 * 压力管道不存储压力、不自行 tick——压力 tick 与破裂判定全部由压力机
 * {@code PressureMultiblockMachine#pressureTick} 经网络拓扑驱动。本类只承担
 * {@code IPipeNode} 的方块实体职责（连接状态、盖板、贴图），无新增 {@code @Persisted}
 * 字段，直接使用基类 {@link PipeBlockEntity#MANAGED_FIELD_HOLDER}。
 * </p>
 *
 * <p>
 * {@link #canAttachTo}：管道节点只在相邻方块是压力仓（终端）时允许"附着"——管道对管道
 * 的连接走 {@code canPipesConnect}，管道对压力仓走 {@code canPipeConnectToBlock}，两者
 * 均独立判定；此处仅提供与 GTM 流体管道一致的语义。
 * </p>
 */
public class PressurePipeBlockEntity extends PipeBlockEntity<PressurePipeType, PressurePipeProperties> {

    public PressurePipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public boolean canAttachTo(Direction side) {
        if (level != null) {
            // 同类型管道：连接判定交给 canPipesConnect（在 Block 层），这里不额外放行。
            if (level.getBlockEntity(getBlockPos().relative(side)) instanceof PressurePipeBlockEntity) {
                return false;
            }
            // 终端：只有压力仓能建立压力连接（机器非 BlockEntity，经 MetaMachineBlockEntity 取元机）。
            return level.getBlockEntity(
                    getBlockPos().relative(side)) instanceof MetaMachineBlockEntity machineBlockEntity &&
                    machineBlockEntity.getMetaMachine() instanceof PressureHatchPartMachine;
        }
        return false;
    }
}
