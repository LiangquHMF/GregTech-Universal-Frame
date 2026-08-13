package com.liangqu.gtuf.api.pipelike.pressurepipe;

import com.gregtechceu.gtceu.api.pipenet.LevelPipeNet;
import com.gregtechceu.gtceu.api.pipenet.PipeNet;

import net.minecraft.nbt.CompoundTag;

import com.liangqu.gtuf.api.data.material.PressurePipeProperties;

/**
 * 压力管道网络：由同材质量程的节点组成，连接各压力机的压力仓。
 *
 * <p>
 * 节点属性即材质量程 {@link PressurePipeProperties}（max/min，kPa）。网络本身不存储
 * 压力——压力只存于机器腔体，网络仅提供"相邻"拓扑，供压力机的压力 tick 扫描 peer 并
 * 计算破裂判定（超节点量程则破坏该节点方块）。
 * </p>
 *
 * <p>
 * 序列化：{@link PipeNet#serializeNBT()} 会把整张节点表写成 NBT，节点属性经
 * {@link #writeNodeData}/{@link #readNodeData} 落盘（重载世界后网络自动重建）。
 * </p>
 */
public class PressurePipeNet extends PipeNet<PressurePipeProperties> {

    public PressurePipeNet(LevelPipeNet<PressurePipeProperties, PressurePipeNet> world) {
        super(world);
    }

    @Override
    protected void writeNodeData(PressurePipeProperties nodeData, CompoundTag tagCompound) {
        tagCompound.putDouble("max_pressure_kpa", nodeData.getMaxPressureKpa());
        tagCompound.putDouble("min_pressure_kpa", nodeData.getMinPressureKpa());
    }

    @Override
    protected PressurePipeProperties readNodeData(CompoundTag tagCompound) {
        double max = tagCompound.getDouble("max_pressure_kpa");
        double min = tagCompound.getDouble("min_pressure_kpa");
        return new PressurePipeProperties(max, min);
    }
}
